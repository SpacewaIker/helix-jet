import io.github.treesitter.ktreesitter.plugin.GrammarExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import java.io.File
import java.io.FileInputStream
import java.util.*

class GrammarPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val grammarName = project.name
        val os = OperatingSystem.current()
        val grammarDir = project.projectDir.resolve("tree-sitter-$grammarName")

        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply("io.github.tree-sitter.ktreesitter-plugin")

        project.extensions.getByType(GrammarExtension::class.java).run {
            getBaseDir().set(project.file("tree-sitter-$grammarName"))
            getGrammarName().set(grammarName)
            getClassName().set("TreeSitter${grammarName.replaceFirstChar { it.uppercaseChar() }}")
            getPackageName().set("io.github.treesitter.ktreesitter.$grammarName")
        }

        val generateTask = project.tasks.named("generateGrammarFiles")
        val generatedSrc = project.layout.buildDirectory.get().dir("generated/src")
        val cmakeBuildDir = project.layout.buildDirectory.get().dir("cmake")

        project.extensions.getByType(KotlinMultiplatformExtension::class.java).run {
            jvm().compilerOptions {
                jvmTarget.set(JvmTarget.JVM_21)
            }

            sourceSets.configureEach {
                kotlin.srcDir(generatedSrc.dir(name).dir("kotlin"))
            }

            sourceSets.getByName("jvmMain") {
                resources.srcDir(generatedSrc.dir("jvmMain").dir("resources"))
            }
        }

        project.tasks.configureEach {
            if (name.contains("ProcessResources")
                || name.contains("SourcesJar")
                || name.contains("compile")
                || name.contains("Jar")
                || this is KotlinCompilationTask<*>
            )
                dependsOn(generateTask)
        }

        val generateHeader = project.tasks.register("generateTreeSitterHeader") {
            val outputFile = grammarDir.resolve("bindings/c/tree-sitter-$grammarName.h")
            outputs.file(outputFile)
            mustRunAfter(generateTask)
            onlyIf { !outputFile.exists() }
            doFirst {
                outputFile.writeText(
                    """
                    #ifndef TREE_SITTER_${grammarName.uppercase()}_H_
                    #define TREE_SITTER_${grammarName.uppercase()}_H_
                    
                    typedef struct TSLanguage TSLanguage;
                    
                    #ifdef __cplusplus
                    extern "C" {
                    #endif
                    
                    extern const TSLanguage *tree_sitter_$grammarName(void);
                    
                    #ifdef __cplusplus
                    }
                    #endif
                    
                    #endif // TREE_SITTER_${grammarName.uppercase()}_H_
                """.trimIndent()
                )
            }
        }

        val fixCMakePaths = project.tasks.register("fixCMakePaths") {
            dependsOn(generateTask)
            val cmakeFile = project.layout.buildDirectory.get().file("generated/CMakeLists.txt").asFile
            inputs.file(cmakeFile)
            outputs.file(cmakeFile)
            doFirst {
                var content = cmakeFile.readText().replace("\\", "/")

                var srcDir = "tree-sitter-$grammarName/src"

                // handle subpath
                val languagesProperties: File = project.projectDir.resolve("../../languages.properties").absoluteFile
                val properties = Properties()
                properties.load(FileInputStream(languagesProperties))
                val subpath =
                    properties.entries.find { it.key == "grammar.$grammarName.source.subpath" }?.value as String?
                if (subpath != null) {
                    content = content.replace(
                        "/tree-sitter-$grammarName/src",
                        "/tree-sitter-$grammarName/$subpath/src"
                    )
                    srcDir = "tree-sitter-$grammarName/$subpath/src"
                }

                // Add src dir to include_directories so <tree_sitter/parser.h> can be found
                content = content.replace(
                    "include_directories(",
                    "include_directories(../../$srcDir "
                )

                // Fix for scanner...
                for (scanner in listOf("scanner.c", "scanner.cc")) {
                    val scannerFile =
                        project.layout.projectDirectory.file("$srcDir/$scanner").asFile
                    if (scannerFile.exists() && !content.contains(scanner)) {
                        content = content.replace(
                            "../../$srcDir/parser.c",
                            "../../$srcDir/parser.c ../../$srcDir/$scanner",
                        )
                    }
                }

                // Fix for cpp...
                if (content.contains(".cc")) {
                    content = content.replace("LANGUAGES C", "LANGUAGES C CXX")

                    val setIdx = content.indexOf("set(CMAKE_C_STANDARD")
                    val newLine = content.indexOf("\n", setIdx) + 1
                    content = content.substring(0, newLine) +
                            "set(CMAKE_CXX_STANDARD 17)\n" +
                            "set(CMAKE_CXX_STANDARD_REQUIRED ON)\n" +
                            content.substring(newLine)
                }

                cmakeFile.writeText(content)
            }
        }

        val cmakeConfigure = project.tasks.register("cmakeConfigure", Exec::class.java) {
            dependsOn(fixCMakePaths, generateTask, generateHeader)
            inputs.file(project.layout.buildDirectory.get().file("generated/CMakeLists.txt"))
            outputs.dir(cmakeBuildDir)
            workingDir = project.layout.buildDirectory.get().dir("generated").asFile
            commandLine(
                "cmake",
                "-B", cmakeBuildDir.asFile.path.replace("\\", "/"),
                "-DCMAKE_BUILD_TYPE=Release"
            )
        }

        val cmakeBuild = project.tasks.register("cmakeBuild", Exec::class.java) {
            dependsOn(cmakeConfigure)
            inputs.dir(cmakeBuildDir)
            outputs.dir(project.layout.buildDirectory.get().dir("generated/src/jvmMain/resources"))
            commandLine("cmake", "--build", cmakeBuildDir.asFile.path, "--config", "Release")

            doLast {
                val ext = when {
                    os.isWindows -> "dll"
                    os.isMacOsX -> "dylib"
                    else -> "so"
                }
                val osName = when {
                    os.isWindows -> "windows"
                    os.isMacOsX -> "macos"
                    else -> "linux"
                }
                val arch = when (System.getProperty("os.arch")) {
                    "aarch64", "arm64" -> "aarch64"
                    else -> "x64"
                }
                val prefix = if (os.isWindows) "" else "lib"
                val builtLib = cmakeBuildDir.asFile.walk().find { it.extension == ext }
                    ?: error("Native library not found after CMake build in ${cmakeBuildDir.asFile.path}")

                val resourcesDir = project.layout.buildDirectory.get()
                    .dir("generated/src/jvmMain/resources/lib/$osName/$arch").asFile
                resourcesDir.mkdirs()
                builtLib.copyTo(
                    resourcesDir.resolve("${prefix}ktreesitter-$grammarName.$ext"),
                    overwrite = true
                )
            }
        }

        project.tasks.named("jvmProcessResources") {
            dependsOn(cmakeBuild)
        }

        project.tasks.register("javadocJar", org.gradle.api.tasks.bundling.Jar::class.java) {
            group = "documentation"
            archiveClassifier.set("javadoc")
        }
    }
}
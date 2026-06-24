import java.io.FileInputStream
import java.util.*

rootProject.name = "helix-jet"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        kotlin("multiplatform") version "2.3.10"
        kotlin("jvm") version "2.3.10"
        id("io.github.tree-sitter.ktreesitter-plugin") version "0.25.0"
        id("org.ajoberstar.grgit") version "5.0.0-rc.3"
    }
}

include(":plugin")

val grammarsDir = rootDir.resolve("grammars")
grammarsDir.mkdirs()

var grammars = emptyList<GrammarEntry>()

println("> Grammars")
val languagesProperties: File = rootDir.resolve("languages.properties").absoluteFile
if (!languagesProperties.exists()) {
    println("Could not find language.properties")
} else {
    println("Found languages.properties: ${languagesProperties.absolutePath}")
    grammars = loadLanguagesProperties(languagesProperties)
    println("Valid grammar definitions: ${grammars.size}")
}

grammars.forEach { (grammarName, repoUrl, rev) ->
    print("> Grammar '$grammarName': "); System.out.flush()

    // Create directory
    val subprojectDir = grammarsDir.resolve(grammarName)
    subprojectDir.mkdirs()

    // Create build file
    val buildFile = subprojectDir.resolve("build.gradle.kts")
    if (!buildFile.exists()) {
        buildFile.writeText("apply<GrammarPlugin>()")
    }

    // clone, then checkout tree-sitter grammar
    val repoDir = subprojectDir.resolve("tree-sitter-$grammarName")

    // Repo does not exist, we clone it
    if (!repoDir.resolve(".git").exists()) {
        print("cloning..."); System.out.flush()
        val process = ProcessBuilder("git", "clone", repoUrl, repoDir.absolutePath, "--revision", rev).start()
        val exitedNormally = process.waitFor(5, TimeUnit.MINUTES)
        if (!exitedNormally) {
            process.destroy()
            println("Timed out while cloning $repoUrl at rev $rev")
            return@forEach
        }
        print(" done. "); System.out.flush()
    } else { // Repo does exist, we nuke it to be able to checkout after
        print("repo exists. Nuking... "); System.out.flush()
        val process = ProcessBuilder("git", "-C", repoDir.absolutePath, "reset", "--hard").start()
        val exitedNormally = process.waitFor(10, TimeUnit.SECONDS)
        if (!exitedNormally) {
            process.destroy()
            println("Timed out while nuking $repoUrl")
            return@forEach
        }
        print(" done. "); System.out.flush()
    }

    // Checking out revision
    print("checking out rev..."); System.out.flush()
    val process = ProcessBuilder("git", "-C", repoDir.absolutePath, "checkout", rev).start()
    val exitedNormally = process.waitFor(10, TimeUnit.SECONDS)
    if (!exitedNormally) {
        process.destroy()
        println("Timed out while checking out rev $rev of $repoUrl")
        return@forEach
    }
    print(" done. "); System.out.flush()
    println()

    include(":grammars:$grammarName")
    project(":grammars:$grammarName").projectDir = subprojectDir
}

data class GrammarEntry(
    val name: String,
    var gitUrl: String,
    var gitRev: String,
)

fun loadLanguagesProperties(file: File): List<GrammarEntry> {
    val grammars = mutableMapOf<String, GrammarEntry>()

    val properties = Properties()
    properties.load(FileInputStream(file))

    for ((key, value) in properties.entries) {
        key as String
        value as String

        // grammars should be in the format
        // grammar.language.source.git=...
        // grammar.language.source.rev=...
        val keyPath = key.split(".")
        if (keyPath.size != 4 || keyPath[0] != "grammar" || keyPath[2] != "source") continue

        val name = keyPath[1]
        val entryKey = keyPath[3]

        val entry = grammars.getOrPut(name) { GrammarEntry(name, "", "") }
        when (entryKey) {
            "git" -> entry.gitUrl = value
            "rev" -> entry.gitRev = value
            else -> {}
        }
    }

    return grammars.values.filter { it.name.isNotEmpty() && it.gitUrl.isNotEmpty() && it.gitRev.isNotEmpty() }
}

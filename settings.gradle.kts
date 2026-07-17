import java.io.FileInputStream
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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


fun runCommand(command: List<String>, timeout: Long, unit: TimeUnit): String {
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    val exitedNormally = process.waitFor(timeout, unit)

    if (!exitedNormally) {
        process.destroyForcibly()
        throw GradleException("Command timed out: ${command.joinToString(" ")}")
    }
    if (process.exitValue() != 0) {
        throw GradleException("Command failed (${command.joinToString(" ")}):\n$output")
    }
    return output
}

val grammarProjects = mutableListOf<Pair<String, File>>()
val executor: ExecutorService = Executors.newCachedThreadPool()
try {
    val futures = grammars.map { (grammarName, repoUrl, rev, subpath) ->
        executor.submit<Pair<String, File>> {
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
                repoDir.deleteRecursively()
                repoDir.mkdirs()

                runCommand(
                    listOf("git", "clone", repoUrl, repoDir.absolutePath, "--revision", rev),
                    5,
                    TimeUnit.MINUTES
                )
            } else { // Repo does exist. We check the revision
                val actualRevision = runCommand(listOf("git", "rev-parse", "HEAD"), 5, TimeUnit.SECONDS)
                val isCorrectRevision = actualRevision == rev

                // incorrect revision, nuke and checkout
                if (!isCorrectRevision) {
                    runCommand(listOf("git", "-C", repoDir.absolutePath, "reset", "--hard"), 10, TimeUnit.SECONDS)
                    runCommand(listOf("git", "-C", repoDir.absolutePath, "checkout", rev), 10, TimeUnit.SECONDS)
                }
            }

            Pair(grammarName, subprojectDir)
        }
    }

    val errors = mutableListOf<Exception>()
    futures.forEach { future ->
        try {
            grammarProjects.add(future.get())
        } catch (e: Exception) {
            errors.add(e)
        }
    }

    if (errors.isNotEmpty()) {
        errors.forEach { it.printStackTrace() }
        throw GradleException("Grammars could not be cloned.")
    }
} finally {
    executor.shutdown()
}

grammarProjects.forEach { (grammarName, subprojectDir) ->
    include(":grammars:$grammarName")
    project(":grammars:$grammarName").projectDir = subprojectDir
}

data class GrammarEntry(
    val name: String,
    var gitUrl: String,
    var gitRev: String,
    var subpath: String?,
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

        val name = keyPath[1].replace("-", "_")
        val entryKey = keyPath[3]

        val entry = grammars.getOrPut(name) { GrammarEntry(name, "", "", null) }
        when (entryKey) {
            "git" -> entry.gitUrl = value
            "rev" -> entry.gitRev = value
            "subpath" -> entry.subpath = value
            else -> {}
        }
    }

    return grammars.values.filter { it.name.isNotEmpty() && it.gitUrl.isNotEmpty() && it.gitRev.isNotEmpty() }
}

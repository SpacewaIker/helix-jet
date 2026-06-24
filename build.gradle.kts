plugins {
    kotlin("multiplatform") apply false
    kotlin("jvm") apply false
}

allprojects {
    repositories {
        mavenCentral()
    }
}

tasks.register("buildGrammars") {
    project(":grammars").subprojects.forEach { grammarProject ->
        dependsOn(":grammars:${grammarProject.name}:build")
    }
}

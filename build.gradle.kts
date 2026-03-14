import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea
    `maven-publish`
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("net.minecraftforge.gradle") version "[6.0.24,6.2)"
}

group = property("mod_group_id") as String
version = property("mod_version") as String

base {
    archivesName.set(property("mod_id") as String)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

minecraft {
    mappings("official", property("minecraft_version") as String)
    copyIdeResources = true

    runs {
        configureEach {
            workingDirectory(project.file("run"))
            property("forge.logging.console.level", "debug")
            mods {
                create(property("mod_id") as String) {
                    source(sourceSets.main.get())
                }
            }
        }

        create("client") {
            property("forge.enabledGameTestNamespaces", property("mod_id") as String)
        }
        create("server") {
            property("forge.enabledGameTestNamespaces", property("mod_id") as String)
            arg("--nogui")
        }
        create("gameTestServer") {
            property("forge.enabledGameTestNamespaces", property("mod_id") as String)
        }
    }
}

repositories {
    maven("https://maven.minecraftforge.net")
    maven("https://maven.theillusivec4.top/")
    mavenCentral()
}

dependencies {
    minecraft("net.minecraftforge:forge:${property("minecraft_version")}-${property("forge_version")}")
    implementation(kotlin("stdlib"))

    implementation(fg.deobf("top.theillusivec4.curios:curios-forge:5.9.1+1.20.1"))

    testImplementation(kotlin("test"))
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}

tasks.test {
    useJUnitPlatform()
}

tasks.register("headlessGameTest") {
    group = "verification"
    description = "Runs Forge game tests in a headless dedicated server."
    dependsOn(tasks.named("runGameTestServer"))
}

tasks.processResources {
    val props = mapOf(
        "minecraft_version" to project.property("minecraft_version"),
        "forge_version" to project.property("forge_version"),
        "mod_id" to project.property("mod_id"),
        "mod_name" to project.property("mod_name"),
        "mod_version" to project.property("mod_version")
    )

    inputs.properties(props)
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) {
        expand(props)
    }
}

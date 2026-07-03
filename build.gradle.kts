import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinForForgeJar = "vendor/mods/kotlinforforge-${property("kotlinforforge_version")}-all.jar"
val curiosMappedJar = "vendor/mods/curios-forge-${property("curios_version")}_mapped_parchment_${property("parchment_version")}.jar"
val curiosApiNotation = "top.theillusivec4.curios:curios-forge:${property("curios_version")}:api"

plugins {
    idea
    `maven-publish`
    jacoco
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("net.minecraftforge.gradle") version "[6.0.24,6.2)"
    id("org.parchmentmc.librarian.forgegradle") version "1.2.0"
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
    mappings("parchment", property("parchment_version") as String)
    copyIdeResources = true

    runs {
        configureEach {
            workingDirectory(project.file("run"))
            property("forge.logging.console.level", "debug")
            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", "${projectDir}/build/createSrgToMcp/output.srg")
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
    maven("https://thedarkcolour.github.io/KotlinForForge/")
    mavenCentral()
}

dependencies {
    minecraft("net.minecraftforge:forge:${property("minecraft_version")}-${property("forge_version")}")
    if (file(kotlinForForgeJar).exists()) {
        implementation(files(kotlinForForgeJar))
    } else {
        implementation("thedarkcolour:kotlinforforge:${property("kotlinforforge_version")}")
    }
    if (file(curiosMappedJar).exists()) {
        compileOnly(files(curiosMappedJar))
    } else {
        compileOnly(fg.deobf(curiosApiNotation))
    }

    testImplementation(kotlin("test"))
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.named<Jar>("jar") {
    finalizedBy("reobfJar")
}

val stageRuntimeJar by tasks.registering(Copy::class) {
    group = "build"
    description = "Stages the reobfuscated runtime jar into build/libs using the canonical release filename."
    dependsOn(tasks.named("reobfJar"))
    from(layout.buildDirectory.file("reobfJar/output.jar"))
    into(layout.buildDirectory.dir("libs"))
    rename { "${base.archivesName.get()}-$version.jar" }
}

tasks.named("assemble") {
    dependsOn(stageRuntimeJar)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy("jacocoTestReport")
}

jacoco {
    toolVersion = "0.8.12"
}

val jacocoIncludedClasses = listOf(
    "**/kit/ClassKit.class",
    "**/kit/KitItem.class",
    "**/kit/KitSlot.class",
    "**/kit/KitSlotTarget*",
    "**/client/ClassSelectionState*.class"
)

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    val mainClasses = fileTree(layout.buildDirectory.dir("classes/kotlin/main").get().asFile) {
        include(jacocoIncludedClasses)
    }
    classDirectories.setFrom(mainClasses)
}

tasks.register("headlessGameTest") {
    group = "verification"
    description = "Runs Forge game tests in a headless dedicated server."
    dependsOn(tasks.named("runGameTestServer"))
}

val syncGameTestStructures by tasks.registering(Copy::class) {
    from(layout.projectDirectory.dir("gameteststructures"))
    into(layout.projectDirectory.dir("run/gameteststructures"))
}

val installDevMods by tasks.registering(Copy::class) {
    from(curiosMappedJar)
    into(layout.projectDirectory.dir("run/mods"))
    onlyIf { file(curiosMappedJar).exists() }
}

tasks.configureEach {
    if (name.startsWith("prepareRun")) {
        dependsOn(installDevMods)
    }
}

tasks.matching { it.name == "prepareRunGameTestServer" }.configureEach {
    dependsOn(syncGameTestStructures)
}

tasks.processResources {
    val props = mapOf(
        "minecraft_version" to project.property("minecraft_version"),
        "forge_version" to project.property("forge_version"),
        "kotlinforforge_version" to project.property("kotlinforforge_version"),
        "curios_version" to project.property("curios_version"),
        "mod_id" to project.property("mod_id"),
        "mod_name" to project.property("mod_name"),
        "mod_version" to project.property("mod_version")
    )

    inputs.properties(props)
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) {
        expand(props)
    }
}

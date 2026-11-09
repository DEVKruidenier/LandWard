import io.papermc.hangarpublishplugin.model.Platforms

plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
    id("io.papermc.hangar-publish-plugin") version "0.1.4"
}

group = "io.github.devkruidenier"
version = "1.0.1.5"

val pluginVersion = version.toString()
val hangarChangelog = providers.environmentVariable("HANGAR_CHANGELOG")
    .orElse(providers.fileContents(layout.projectDirectory.file("CHANGELOG.md")).asText)

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
    maven("https://jitpack.io") {
        name = "jitpack"
    }
    maven("https://repo.playeranalytics.net/releases") {
        name = "playeranalytics"
    }
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") {
        name = "placeholderapi"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.112-stable")
    implementation("org.bstats:bstats-bukkit:3.2.1")
    implementation("org.xerial:sqlite-jdbc:3.54.1.0")
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("com.mysql:mysql-connector-j:9.3.0")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("com.djrapitops:plan-api:5.7-R0.2")
    compileOnly("xyz.jpenilla:squaremap-api:1.4.3")
    compileOnly("me.clip:placeholderapi:2.11.7")

    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.yaml:snakeyaml:2.5")
    testImplementation("com.djrapitops:plan-api:5.7-R0.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    processResources {
        filteringCharset = "UTF-8"
        // Changing only the project version must invalidate the processed
        // plugin.yml; otherwise Gradle may package a stale plugin version.
        inputs.property("landwardPluginVersion", pluginVersion)
        filesMatching("plugin.yml") {
            expand("version" to pluginVersion)
        }
        from("LICENSE") {
            into("META-INF")
            rename { "LICENSE-LandWard.txt" }
        }
        from("THIRD_PARTY_NOTICES.md") {
            into("META-INF")
        }
    }

    test {
        useJUnitPlatform()
    }

    jar {
        archiveBaseName.set("LandWard")
        archiveClassifier.set("unshaded")
        manifest {
            attributes["paperweight-mappings-namespace"] = "mojang"
        }
    }

    shadowJar {
        archiveBaseName.set("LandWard")
        archiveClassifier.set("")
        configurations = project.configurations.runtimeClasspath.map { setOf(it) }
        dependencies {
            exclude { it.moduleGroup !in setOf("org.bstats", "org.xerial", "com.zaxxer", "com.mysql") }
        }
        relocate(
            "org.bstats",
            "io.github.devkruidenier.landward.libs.bstats",
        )
        relocate(
            "com.zaxxer.hikari",
            "io.github.devkruidenier.landward.libs.hikari",
        )
        manifest {
            attributes["paperweight-mappings-namespace"] = "mojang"
        }
    }

    // Hangar enforces a 15 MiB upload limit. Keep the normal release JAR
    // universal, but omit SQLite native binaries for platforms that are not
    // supported by the tested Paper release targets in the Hangar artifact.
    register<Jar>("hangarJar") {
        dependsOn(shadowJar)
        archiveBaseName.set("LandWard")
        archiveClassifier.set("hangar")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(zipTree(shadowJar.flatMap { it.archiveFile })) {
            exclude(
                "org/sqlite/native/FreeBSD/**",
                "org/sqlite/native/Mac/**",
                "org/sqlite/native/Linux-Musl/**",
                "org/sqlite/native/Linux/arm/**",
                "org/sqlite/native/Linux/armv6/**",
                "org/sqlite/native/Linux/armv7/**",
                "org/sqlite/native/Linux/ppc64/**",
                "org/sqlite/native/Linux/riscv64/**",
                "org/sqlite/native/Windows/armv7/**",
            )
        }
    }

    build {
        dependsOn(shadowJar)
    }
}

hangarPublish {
    publications.register("plugin") {
        version.set(pluginVersion)
        channel.set("Release")
        id.set("LandWard")
        apiKey.set(providers.environmentVariable("HANGAR_API_TOKEN"))
        changelog.set(hangarChangelog)

        // Keep Hangar's public page aligned with the repository's concise overview.
        pages.resourcePage(providers.fileContents(layout.projectDirectory.file("README.md")).asText)

        platforms {
            register(Platforms.PAPER) {
                jar.set(tasks.named<Jar>("hangarJar").flatMap { it.archiveFile })
                platformVersions.set(
                    providers.gradleProperty("hangarPaperVersions")
                        .map { it.split(",").map(String::trim) },
                )
            }
        }
    }
}

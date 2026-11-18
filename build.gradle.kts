import io.papermc.hangarpublishplugin.model.Platforms

plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
    id("io.papermc.hangar-publish-plugin") version "0.1.4"
}

group = "io.github.devkruidenier"
version = "1.0.5.0-beta2"

val pluginVersion = version.toString()

// Supported Paper/Spigot servers supply JDBC drivers and SLF4J. Resolve the
// same versions ONLY for the standalone packaging test, never for distribution.
val packagingLogging = configurations.create("packagingLogging")
dependencies { add(packagingLogging.name, "org.slf4j:slf4j-api:2.0.17") }
val packagingDrivers = configurations.create("packagingDrivers")
dependencies {
    add(packagingDrivers.name, "org.xerial:sqlite-jdbc:${providers.gradleProperty("serverSqliteVersion").getOrElse("3.49.1.0")}")
    add(packagingDrivers.name, "com.mysql:mysql-connector-j:${providers.gradleProperty("serverMysqlVersion").getOrElse("9.2.0")}")
}

tasks.register<JavaExec>("packagingTest") {
    group = "verification"
    description = "Verify the shaded artifact and exercise its isolated JDBC storage."
    dependsOn(tasks.testClasses, tasks.shadowJar)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    classpath = files(sourceSets.test.get().output, tasks.shadowJar.flatMap { it.archiveFile }, packagingLogging, packagingDrivers)
    mainClass.set("io.github.devkruidenier.landward.packaging.PackagedStorageFixture")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    doFirst {
        val sqlite = packagingDrivers.resolvedConfiguration.resolvedArtifacts
            .single { it.moduleVersion.id.group == "org.xerial" && it.name == "sqlite-jdbc" }.file
        args(tasks.shadowJar.get().archiveFile.get().asFile.absolutePath,
            sqlite.absolutePath, layout.buildDirectory.dir("fixtures/packaging").get().asFile.absolutePath)
    }
}

tasks.register<JavaExec>("updateFixture") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.devkruidenier.landward.update.UpdaterFixture")
    args(layout.buildDirectory.dir("fixtures/landward-update").get().asFile.absolutePath)
}

// Explicit development-only fixture runner; test classes are excluded from release JARs.
tasks.register<JavaExec>("paginationFixture") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.devkruidenier.landward.gui.PaginationFixture")
    args(providers.gradleProperty("fixtureOperation").getOrElse("verify"),
        layout.buildDirectory.dir("fixtures/landward-pagination").get().asFile.absolutePath)
}

tasks.register<JavaExec>("existingServerFixture") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.devkruidenier.landward.gui.ExistingServerFixture")
    doFirst {
        args(providers.gradleProperty("fixtureOperation").getOrElse("verify"),
            providers.gradleProperty("fixtureServer").get())
    }
}
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
    implementation("com.zaxxer:HikariCP:6.3.0")
    // Drivers are part of every supported server's parent classpath. LandWard
    // never downloads libraries at runtime or requires another plugin for SQL.
    testRuntimeOnly("org.xerial:sqlite-jdbc:3.49.1.0")
    testRuntimeOnly("com.mysql:mysql-connector-j:9.2.0")
    // Spigot/CraftBukkit have no built-in Adventure API. Bundle the API used by GUI/console
    // code so other installed plugins are never required to supply it.
    implementation("net.kyori:adventure-api:5.2.0")
    implementation("net.kyori:adventure-text-serializer-plain:5.2.0")
    implementation("net.kyori:adventure-text-serializer-legacy:5.2.0")
    implementation("net.kyori:adventure-text-serializer-gson:5.2.0")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("com.djrapitops:plan-api:5.7-R0.2")
    compileOnly("xyz.jpenilla:squaremap-api:1.4.3")
    compileOnly("me.clip:placeholderapi:2.11.7")

    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testCompileOnly("io.papermc.paper:paper-api:26.2.build.112-stable")
    testImplementation("org.yaml:snakeyaml:2.5")
    testImplementation("com.google.code.gson:gson:2.13.2")
    testImplementation("com.djrapitops:plan-api:5.7-R0.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

// Explicit development-only server probe; never included in the universal plugin.
tasks.register<Jar>("featureProbeJar") {
    dependsOn(tasks.testClasses)
    archiveFileName.set("LandWardFeatureProbe.jar")
    destinationDirectory.set(layout.buildDirectory.dir("fixtures/feature-probe"))
    from(sourceSets.test.get().output) {
        include("io/github/devkruidenier/landward/fixture/FeatureServerProbe*.class")
        include("io/github/devkruidenier/landward/gui/ChunkMapServerProbe*.class")
        include("io/github/devkruidenier/landward/platform/MessageRuntimeProbe*.class")
    }
    from("src/test/featureprobe") { include("plugin.yml") }
}

tasks.test { exclude("**/fixture/**", "**/*ServerProbe*", "**/*RuntimeProbe*") }

tasks.register<Jar>("foliaProbeJar") {
    dependsOn(tasks.testClasses)
    archiveFileName.set("LandWardFoliaProbe.jar")
    destinationDirectory.set(layout.buildDirectory.dir("fixtures/folia-probe"))
    from(sourceSets.test.get().output) {
        include("io/github/devkruidenier/landward/fixture/FoliaServerProbe*.class")
        include("io/github/devkruidenier/landward/gui/FoliaRuntimeProbe*.class")
    }
    from("src/test/foliaprobe") { include("plugin.yml") }
}

tasks {
    withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
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
        filesMatching("config.yml") {
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
        // Preserve providers for bundled libraries. JDBC descriptors belong to
        // the unchanged server drivers; no duplicate copies are shipped here.
        filesMatching("META-INF/services/**") {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
        mergeServiceFiles()
        failOnDuplicateEntries = true
        dependencies {
            exclude { it.moduleGroup !in setOf("org.bstats", "com.zaxxer", "net.kyori") }
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

    // Compatibility task name for existing build callers; no second artifact.
    register("hangarJar") {
        dependsOn(shadowJar)
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
        pages.resourcePage(providers.fileContents(layout.projectDirectory.file(".github/hangar/description.md")).asText)

        platforms {
            register(Platforms.PAPER) {
                jar.set(tasks.shadowJar.flatMap { it.archiveFile })
                platformVersions.set(
                    providers.gradleProperty("hangarPaperVersions")
                        .map { it.split(",").map(String::trim) },
                )
            }
        }
    }
}

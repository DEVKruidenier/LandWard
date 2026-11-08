plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "io.github.devkruidenier"
version = "1.0.0.0"

val pluginVersion = version.toString()

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

    build {
        dependsOn(shadowJar)
    }
}

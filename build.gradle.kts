import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.20"
    id("com.gradleup.shadow") version "8.3.5"
}

group = "dev.rlscoreboard"
version = "0.1.0"
description = "Config-driven scoreboard and leaderboard framework for Paper servers."

repositories {
    mavenCentral()
    // Paper API + server internals.
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc-repo" }
    // PlaceholderAPI (soft dependency, compileOnly).
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") { name = "placeholderapi" }
    // VaultAPI (soft dependency, compileOnly).
    maven("https://jitpack.io") { name = "jitpack" }
    // LuckPerms API (soft dependency, compileOnly).
    maven("https://repo.lucko.me/") { name = "luckperms-repo" }
}

dependencies {
    // Core - the only hard dependency the plugin has.
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")

    // ---- Optional integrations: compileOnly only, never shaded, never required at runtime ----
    // RLScoreboard detects each of these at startup (see IntegrationManager) and simply
    // disables the related feature set if a plugin isn't installed - see section 4/22 of
    // the design spec ("KHÔNG được phụ thuộc cứng").
    compileOnly("me.clip:placeholderapi:2.11.6")

    // VaultAPI's published POM has a hard (non-optional) transitive dependency on an
    // ancient org.bukkit:bukkit:1.13.1-R0.1-SNAPSHOT artifact, which conflicts with
    // paper-api on the same Bukkit "capability" and fails dependency resolution entirely.
    // This is a well-known VaultAPI issue - excluding it is the standard fix, and safe:
    // RLScoreboard only ever calls VaultAPI's own Economy interface, never anything from
    // that transitive bukkit artifact.
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    compileOnly("net.luckperms:api:5.4")

    // Jobs Reborn native datasource: REMOVED. com.github.Zrips:Jobs's JitPack-published POM
    // pulls in hard (non-optional) transitive dependencies on mcMMO, WorldGuard, WorldEdit,
    // WildStackerAPI, and StackMob - none of which are needed to call the Jobs API itself,
    // and none of which resolve from any repository configured here (they're Jobs Reborn's
    // own optional soft-integrations, published elsewhere or not at all). This is exactly
    // the risk called out in README "Residual risk" - see JobsIntegration.kt for the
    // detection-only fallback this plugin uses instead. If you want to retry a native Jobs
    // datasource later, either track down and add every one of those repositories, or ask
    // Jobs Reborn's maintainers about a lighter-weight API-only artifact.

    // AuraSkills - official Maven Central artifact, wiki-documented API, resolves cleanly
    // with no transitive dependency surprises.
    compileOnly("dev.aurelium:auraskills-api-bukkit:2.3.5")

    // ---- Phase 2: storage (section 18) ----
    // SQLite is the zero-config default backend and is bundled/shaded so it works out of
    // the box - it's a single ~11MB jar, not shaded+relocated (see README "Storage &
    // offline leaderboards" for why relocating a JDBC driver is riskier than it looks).
    implementation("org.xerial:sqlite-jdbc:3.53.2.1")

    // MySQL/MariaDB, bundled the same way. protobuf-java is excluded - it's only needed
    // for the X DevAPI (MySQL's document-store mode), not plain JDBC, and is the single
    // biggest transitive dependency this driver would otherwise pull in.
    implementation("com.mysql:mysql-connector-j:9.7.0") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }

    // Kotlin stdlib is provided by the Paper server runtime via paper-api's Kotlin
    // language support in modern Paper builds; if your target build does not bundle it,
    // uncomment the line below and make sure shadowJar relocates it.
    // implementation("org.jetbrains.kotlin:kotlin-stdlib")
}

val targetJavaVersion = 25
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-jvm-default=all")
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    // Nothing to relocate yet - RLScoreboard doesn't shade any third-party runtime
    // library today. If a bundled dependency (e.g. a JDBC driver for Phase 2 MySQL
    // support) is added later, relocate it under dev.rlscoreboard.libs to avoid
    // classpath clashes with other plugins.
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

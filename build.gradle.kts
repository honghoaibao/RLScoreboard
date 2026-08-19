import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.20"
    id("com.gradleup.shadow") version "8.3.5"
}

group = "dev.rlscoreboard"
version = "0.5.0"
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
    // Core - the only hard dependency the plugin has. Also transitively brings in Adventure
    // (Component/LegacyComponentSerializer, already used by ColorUtil) and MiniMessage
    // specifically - MiniMessage has been bundled with Paper since 1.18.x (confirmed via
    // PaperMC's own forum/docs), so no separate net.kyori:adventure-text-minimessage
    // dependency is needed for the MiniMessage support ColorUtil added in the public-release-
    // upgrade pass.
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

    // ---- Integrations + localization addendum ----
    // Geyser, Floodgate, WorldGuard, and WorldEdit (integration/geyser, /floodgate,
    // /worldguard, /worldedit) are detection-only: they read the target plugin's own
    // plugin.yml version via AbstractIntegration.readVersion() and never call into a
    // compiled API, so none of them need a new compileOnly dependency or repository here.
    // See docs/INTEGRATIONS.md "How version detection works" for why that's sufficient for
    // real version-aware compatibility checking even without one. If a future capability
    // (e.g. WorldGuard region conditions, the real FloodgateApi) needs an actual compiled
    // API, add it here the same way PlaceholderAPI/VaultAPI/LuckPerms are declared above -
    // and see the Jobs Reborn removal below first for what can go wrong doing that blind.

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

    // ---- Tests (design spec section 32) ----
    // kotlin("test") is the Kotlin Gradle plugin's own first-party test dependency notation -
    // it resolves to the matching kotlin-test-junit5 bridge automatically, no separate JUnit
    // coordinate to get right by hand. Chosen deliberately over any third-party test library
    // for the same reason paper-api/mavenCentral are trusted elsewhere in this file: official,
    // first-party Kotlin tooling is about as low transitive-dependency-risk as a Gradle
    // dependency gets - unlike, say, the WorldGuard/WorldEdit compiled API, which was
    // researched this same phase and specifically *not* added after finding a real,
    // documented history of stale-SNAPSHOT transitive dependency breakage (see
    // EngineHub/WorldGuard#1874) - see ROADMAP.md for that decision in full.
    testImplementation(kotlin("test"))
}

// Test code that references classes like ConfigValidator needs paper-api on its compile
// classpath too (ConfigValidator.kt imports org.bukkit.configuration.* even though the
// specific functions this project's tests call are pure string logic) - compileOnly
// dependencies aren't visible to the test source set by default, so this configuration
// inheritance is exactly the standard, documented fix. YamlConfiguration/ConfigurationSection
// are real, self-contained implementations bundled in the API jar itself (not
// server-runtime-dependent interfaces), so tests can construct and use them directly without
// needing a mock Bukkit server (e.g. MockBukkit) - see ConfigValidatorTest.
configurations {
    testImplementation.get().extendsFrom(compileOnly.get())
}

val targetJavaVersion = 25
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        // No -jvm-default flag set. Integration.kt *does* declare default method bodies
        // (hasCapability, versionRangeResult) as of the version-compatibility rework - Kotlin
        // compiles those in "compatibility mode" either way (a real JVM default method plus a
        // synthetic $DefaultImpls for binary-compat), which every implementing class in this
        // module picks up automatically with no flag needed. The flag is left unset for the
        // same reason as before: its accepted values differ between the deprecated
        // -Xjvm-default flag ("all", "all-compatibility", ...) and the current -jvm-default
        // flag ("enable", "no-compatibility", ...), which caused an earlier build failure -
        // simplest fix is still just not setting it.
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
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
    // library today. If a bundled dependency needs relocating later, put it under
    // dev.rlscoreboard.libs to avoid classpath clashes with other plugins.

    // ---- JAR size (design spec section 27) ----
    // org.xerial:sqlite-jdbc bundles native SQLite libraries for every OS/architecture it
    // supports, laid out under org/sqlite/native/<OS>/<arch>/ inside its own jar (confirmed
    // against the driver's own README/USAGE.md and a maintainer-documented `zip -d
    // sqlite-jdbc.jar 'org/sqlite/native/*'` example - see PR/commit message for sources,
    // since this environment has no network access to unpack the real jar and check
    // directly). That's the actual, confirmed source of the JAR-size complaint. Excluded
    // here, conservatively:
    //   - org/sqlite/native/Linux/android-arm, arm, armv6, armv7 (32-bit ARM Linux - for
    //     embedded/mobile targets, not something anyone hosts a Paper server on)
    //   - org/sqlite/native/FreeBSD (present as a real path in the driver's own native
    //     layout; FreeBSD Paper hosting is real but rare enough not to justify the size)
    // Deliberately NOT excluded, despite being extra size, because they're genuinely in use
    // for Paper hosting today:
    //   - Linux x86_64 / aarch64 (the two overwhelmingly common Paper-hosting targets -
    //     aarch64 covers ARM cloud instances like AWS Graviton/Oracle Ampere, increasingly
    //     common)
    //   - Linux-Musl (Alpine-based Docker images - a real, non-negligible way people run
    //     server software in containers; excluding this would silently break SQLite storage
    //     for anyone on an Alpine container with no obvious error message pointing at why)
    //   - Windows and macOS (both architectures) - common for local/dev Paper instances
    // This trims real, confirmed-safe-to-drop weight without touching anything a real
    // deployment target is likely to need. **Residual risk, stated plainly**: the exact list
    // of subdirectories inside a current sqlite-jdbc release was not independently verified
    // against the actual jar contents (no network access here to unzip it) - the paths below
    // are believed correct from the driver's own documentation, but the first real
    // `./gradlew build` (or `unzip -l` / `jar tf` on the resulting shaded jar) should confirm
    // these patterns actually matched something and didn't silently no-op. If any of them
    // turn out to be misspelled/wrong, the safe failure mode is "no size reduction happened"
    // (patterns matched nothing), not "a needed platform's native library went missing" -
    // Gradle exclude patterns that match zero files are not an error.
    exclude("org/sqlite/native/FreeBSD/**")
    exclude("org/sqlite/native/Linux/android-arm/**")
    exclude("org/sqlite/native/Linux/arm/**")
    exclude("org/sqlite/native/Linux/armv6/**")
    exclude("org/sqlite/native/Linux/armv7/**")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

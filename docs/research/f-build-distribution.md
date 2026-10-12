# F — Build & distribution engineering for Sculk 6

Research date: 2026-10-12. Baseline replaced: 5.x (`gradle-9.7.0`, Kotlin 2.4.10, BCV 0.18.1, ktlint-gradle 14.2.0,
Shadow 9.6.1, run-paper 3.0.2, JitPack `jitpack.yml`, `renovate.json` = `config:recommended`, hand-rolled
`maven-publish` with no signing, no SBOM, no verification metadata, single-JDK CI on Temurin 21).

Every version below was read from the authoritative metadata (Maven Central `maven-metadata.xml`, PaperMC repo,
Gradle Plugin Portal page, or GitHub release) on the research date. "Verified" = read from that source.
UNVERIFIED items are called out inline.

---

## 1. Version verification

### 1.1 Toolchain core

| Tool | Current stable | Date | Notes | Source |
| --- | --- | --- | --- | --- |
| Gradle | **9.8.1** | 2026-10-07 | Security patch of 9.8.0 (3 high CVEs). Daemon needs JVM 17–27. Embedded Kotlin **2.4.10** (language level 2.2) — the `kotlin-dsl` build-logic compiles with this, not with KGP. Tested with KGP 2.0.0–2.4.20-RC2. | https://services.gradle.org/versions/current · https://docs.gradle.org/current/userguide/compatibility.html |
| Kotlin | **2.4.21** | 2026-10-08 | Bug-fix release; exists (directive was right). 2.5.0-Beta1 published 2026-10-08 is pre-release. KGP 2.4.20–2.4.21 fully supports Gradle 7.6.3–**9.7.0**; 9.8.x "works but may warn". K1 removed; `-language-version=1.9` rejected. Compiler emits up to Java 26 bytecode. | https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/maven-metadata.xml · https://github.com/JetBrains/kotlin/releases/tag/v2.4.21 · https://kotlinlang.org/docs/gradle-configure-project.html |
| KSP | **2.3.12** | 2026-09-09 | Versioning decoupled from Kotlin since 2.3.0 ("KSP version is no longer tied to the Kotlin compiler version"). KSP1 deprecated in 2.3.0, "KSP 1.x has been removed" per README. KSP2 is a standalone tool, not a compiler plugin. Plugin id `com.google.devtools.ksp`. **UNVERIFIED**: no official statement that 2.3.12 supports Kotlin 2.4.21; 2.3.x has a fix for Kotlin 2.4.0 module names (#2964) and third parties ship 2.4.0+2.3.9. Confirm in the spike. | https://repo1.maven.org/maven2/com/google/devtools/ksp/symbol-processing-api/maven-metadata.xml · https://github.com/google/ksp/releases/tag/2.3.0 |
| kotlinx-coroutines | **1.11.0** | 2026-05-08 | Built on Kotlin 2.2.20; forward-compatible with 2.4.21 runtime. | https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/maven-metadata.xml · https://github.com/Kotlin/kotlinx.coroutines/releases |
| kotlinx-serialization | **1.11.0** (1.12.0-RC 2026-09-04) | 2026-04-09 | 1.11.0 built on Kotlin 2.3.20; 1.12.0-RC on 2.4.10. Compiler plugin ships with Kotlin (`org.jetbrains.kotlin.plugin.serialization` 2.4.21). | https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-core/maven-metadata.xml · https://github.com/Kotlin/kotlinx.serialization/releases |
| kaml | **0.104.0** | 2025-11-18 | Stale (Kotlin 2.2.21 era) but the 5.x build already compiles it against Kotlin 2.4.10 + serialization 1.11.0, so compatibility is demonstrated. No release in 11 months — watch for abandonment. | https://repo1.maven.org/maven2/com/charleskorn/kaml/kaml/maven-metadata.xml · https://github.com/charleskorn/kaml/releases |
| JUnit | **6.1.3** | 2026-08-07 | Java 17+, Kotlin 2.2+ baseline. Platform artifacts share the Jupiter version. `junit-platform-launcher` still required on `testRuntimeOnly` under Gradle 9. | https://repo1.maven.org/maven2/org/junit/jupiter/junit-jupiter/maven-metadata.xml · https://docs.junit.org/6.0.0/release-notes/ |
| Foojay toolchain resolver | **1.0.0** | 2025-05-19 | `org.gradle.toolchains.foojay-resolver-convention`. | https://plugins.gradle.org/plugin/org.gradle.toolchains.foojay-resolver-convention |

### 1.2 Quality gates

| Tool | Current stable | Date | Notes | Source |
| --- | --- | --- | --- | --- |
| KGP built-in ABI validation | ships in KGP 2.4.21 | — | Still `@OptIn(ExperimentalAbiValidation)` in 2.4 docs; stabilisation tracked in KT-71172. Tasks `checkKotlinAbi` / `updateKotlinAbi`; `check` depends on it. Filters: `excluded { annotatedWith.add(...) ; byNames.add(...) }`. 2.4 "streamlined" the DSL and deprecated parts of the 2.2/2.3 DSL. | https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html · https://kotlinlang.org/docs/whatsnew24.html · https://youtrack.jetbrains.com/issue/KT-71172 |
| binary-compatibility-validator | **0.18.2** | 2026-09-02 | README: "This plugin is in maintenance mode"; new features go to KGP. Still receives Kotlin-version support. | https://repo1.maven.org/maven2/org/jetbrains/kotlinx/binary-compatibility-validator/maven-metadata.xml · https://github.com/Kotlin/binary-compatibility-validator |
| ktlint CLI/engine | **1.8.0** | 2025-12-15 | No official ktlint Gradle plugin exists; the community standard remains jlleitschuh. | https://repo1.maven.org/maven2/com/pinterest/ktlint/ktlint-cli/maven-metadata.xml |
| ktlint-gradle | **14.2.0** | 2026-03-12 | 14.0.1 built against Gradle 9.1/Java 25; 14.2.0 Isolated-Projects compatible. | https://plugins.gradle.org/plugin/org.jlleitschuh.gradle.ktlint · https://github.com/JLLeitschuh/ktlint-gradle/releases |
| Dokka | **2.2.0** (2.3.0-Beta 2026-09-15) | 2026-03-26 | DGP v2 default since 2.1.0. Tasks `dokkaGenerate`, `dokkaGeneratePublicationHtml`, `dokkaGeneratePublicationJavadoc` (Javadoc format is Alpha). Aggregation via `dependencies { dokka(project(":x")) }`. Javadoc jar is a hand-rolled `Jar` task. | https://repo1.maven.org/maven2/org/jetbrains/dokka/dokka-gradle-plugin/maven-metadata.xml · https://kotlinlang.org/docs/dokka-migration.html · https://kotlinlang.org/docs/dokka-gradle.html |
| ArchUnit | **1.5.1** | 2026-09-25 | Core artifact `com.tngtech.archunit:archunit` works with any runner ("ArchUnit works with any test framework that executes Java code"); `archunit-junit6` exists. Bytecode-based → Kotlin fine. | https://repo1.maven.org/maven2/com/tngtech/archunit/archunit/maven-metadata.xml · https://www.archunit.org/userguide/html/000_Index.html |
| Konsist | **0.18.1** | 2026-10-10 | 0.18.0 "built with Kotlin 2.4.21", min Kotlin 2.0. Source/PSI based. | https://repo1.maven.org/maven2/com/lemonappdev/konsist/maven-metadata.xml · https://github.com/LemonAppDev/konsist/releases |
| ASM | **9.11** | 2026-10-09 | For the class-file-version check. | https://repo1.maven.org/maven2/org/ow2/asm/asm/maven-metadata.xml |
| JMH | **1.37** | 2023-08-03 | No newer release. | https://repo1.maven.org/maven2/org/openjdk/jmh/jmh-core/maven-metadata.xml |
| me.champeau.jmh | **0.7.3** | 2025-01-30 | "Support Gradle 9" (PR #288) merged 2026-07-19 but **not released**; 0.7.3 predates Gradle 9. 5.x lists it in the catalog but never applies it. Risk: do not adopt until 0.7.4+/0.8 ships. | https://plugins.gradle.org/plugin/me.champeau.jmh · https://github.com/melix/jmh-gradle-plugin/issues?q=gradle+9 |
| MockBukkit | `mockbukkit-v1.21` **4.116.3** (2026-08-19) · `mockbukkit-v26.1.2` **4.115.0** (2026-08-04) · `mockbukkit-v26.2` **4.117.0** (2026-10-06) | — | Artifact id = `mockbukkit-v<Minecraft version>` (MC moved to `YY.D[.H]` in 2026); MockBukkit's own version is the Maven version. **No artifact for 26.3 yet.** | https://repo1.maven.org/maven2/org/mockbukkit/mockbukkit/ · https://docs.mockbukkit.org/docs/en/user_guide/introduction/getting_started |
| CycloneDX Gradle | **3.5.1** | 2026-10-09 | Id `org.cyclonedx.bom`; Gradle 8.4+. Root-applied → `cyclonedxDirectBom` per project + `cyclonedxBom` aggregate on root. Maven Central metadata is stale (1.4.0, 2021) — the Portal is the source of truth. | https://plugins.gradle.org/plugin/org.cyclonedx.bom · https://github.com/CycloneDX/cyclonedx-gradle-plugin |
| dependency-license-report | **3.1.4** | 2026-06-10 | `com.github.jk1.dependency-license-report`. Maven Central mirror stale (1.6, 2023). | https://plugins.gradle.org/plugin/com.github.jk1.dependency-license-report |

### 1.3 Minecraft / packaging

| Tool | Current stable | Date | Notes | Source |
| --- | --- | --- | --- | --- |
| Paper API | **26.3.build.175-stable** (26.3-pre-2 alpha also present) | 2026-10-11 | New coordinate scheme `io.papermc.paper:paper-api:<MC>.build.<n>-<channel>`. 5.x still targets `1.21.11-R0.1-SNAPSHOT`. Spigot-mapping reobf dropped since 26.1 — Mojang-mapped only. | https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml · https://docs.papermc.io/paper/dev/userdev/ |
| Folia (Fill) | version groups **26.2**, 26.1.2, 1.21.11… | — | Available on Fill v3 as project `folia`. | https://fill.papermc.io/v3/projects/folia |
| Velocity | **4.2.0** (4.2.1-SNAPSHOT); 3.5.1 on the 3.x line | 2026-10-07 | Velocity 4 is current; decide which line `sculk-velocity` targets. | https://repo.papermc.io/repository/maven-public/com/velocitypowered/velocity-api/maven-metadata.xml · https://fill.papermc.io/v3/projects/velocity |
| Fill v3 projects | `folia, paper, travertine, velocity, waterfall` | — | Canvas is **not** on Fill. | https://fill.papermc.io/v3/projects |
| run-paper / run-velocity / run-waterfall | **3.1.0** | 2026-08-08 | `xyz.jpenilla.run-paper` etc. Folia via `runPaper.folia.registerTask { }`. | https://plugins.gradle.org/plugin/xyz.jpenilla.run-paper · https://github.com/jpenilla/run-task |
| paperweight-userdev | **2.0.0-beta.24** | 2026-09-25 | Requires Gradle ≥ 9.7.1 and Java 21; `paperweight.paperDevBundle("26.3.build.+")`; "only the latest version is officially supported". | https://github.com/PaperMC/paperweight/releases · https://docs.papermc.io/paper/dev/userdev/ |
| Shadow | **9.6.1** | 2026-07-22 | `com.gradleup.shadow`. | https://repo1.maven.org/maven2/com/gradleup/shadow/shadow-gradle-plugin/maven-metadata.xml |
| plugin-yml (maintained fork) | **0.9.0** | 2026-03-25 | `de.eldoria.plugin-yml.paper`; Minecrell's original archived 2024-12-07 at 0.6.0. | https://plugins.gradle.org/plugin/de.eldoria.plugin-yml.paper |
| Adventure / MiniMessage | **5.2.0** | 2026-06-27 | | https://repo1.maven.org/maven2/net/kyori/adventure-api/maven-metadata.xml |
| Ktor | **3.6.0** | 2026-09-16 | | https://repo1.maven.org/maven2/io/ktor/ktor-server-core/maven-metadata.xml |
| JDA | **6.7.0** | 2026-09-20 | | https://repo1.maven.org/maven2/net/dv8tion/JDA/maven-metadata.xml |
| Lettuce / HikariCP / Caffeine | 7.8.0.RELEASE / 7.1.0 / 3.3.0 | 2026-09 / 06 / 09 | | repo1 metadata for each |
| PacketEvents / ProtocolLib licence | GPL-3.0 / GPL-2.0 | — | GitHub licence API. | https://api.github.com/repos/retrooper/packetevents/license · https://api.github.com/repos/dmulloy2/ProtocolLib/license |

### 1.4 Publishing & supply chain

| Tool | Current stable | Date | Notes | Source |
| --- | --- | --- | --- | --- |
| Sonatype OSSRH | **retired 2025-06-30** | — | Central Portal is the only path. Namespaces cannot be active on both. | https://central.sonatype.org/news/20250326_ossrh_sunset/ |
| Central Portal Gradle support | no official plugin | — | "Currently, there is no official Gradle plugin… Gradle support is on our roadmap." Community: vanniktech, JReleaser, nmcp, … "NOT SUPPORTED by Sonatype". | https://central.sonatype.org/publish/publish-portal-gradle/ |
| vanniktech maven-publish | **0.37.0** | 2026-06-21 | `publishToMavenCentral(automaticRelease = true)`, `signAllPublications()`, in-memory key via `ORG_GRADLE_PROJECT_signingInMemoryKey*`. | https://repo1.maven.org/maven2/com/vanniktech/gradle-maven-publish-plugin/maven-metadata.xml · https://vanniktech.github.io/gradle-maven-publish-plugin/central/ |
| JReleaser | **1.26.0** | 2026-08-31 | Portal + Sigstore `.sigstore.json` upload. Heavier; keep as fallback. | https://plugins.gradle.org/plugin/org.jreleaser |
| GradleUp nmcp | **UNVERIFIED** (portal page returned 400) | — | Alternative lightweight Portal uploader. | https://github.com/GradleUp/nmcp |
| Sigstore on Central | accepted & validated since 2025-01 | — | Optional; "PGP signing is currently still required". | https://central.sonatype.org/news/20250128_sigstore_signature_validation_via_portal/ |
| Central OIDC "trusted publishing" | **not available** (UNVERIFIED negative — no Sonatype doc found; third-party docs say "Maven Central does not support OIDC") | — | Publishing stays token + GPG secret based. | https://eclipse.dev/cbi/best-practices/github-actions/central-portal/ |
| com.gradle.plugin-publish | **2.2.1** | 2026-09-10 | Applies `java-gradle-plugin` + `maven-publish`. Non-prefixed groups need one-time manual approval; valid OSS repo URL + SPDX licence prioritised. | https://plugins.gradle.org/plugin/com.gradle.plugin-publish · https://blog.gradle.org/new-plugin-portal-acceptance-criteria |
| gradle/actions | **v6.4.0** | 2026-09-28 | `setup-gradle`, `dependency-submission`, `wrapper-validation`. | https://github.com/gradle/actions/releases |
| actions/attest | **v4** (`attest-build-provenance@v4` is a wrapper) | — | `permissions: id-token: write, contents: read, attestations: write`; `subject-path` accepts globs (≤1024 subjects). Verify: `gh attestation verify <file> -R owner/repo`. | https://github.com/actions/attest · https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations |
| Renovate Gradle manager | — | — | Supports `gradle/*.toml` catalogs incl. rich versions; updates `verification-metadata.xml` by running `./gradlew --write-verification-metadata` **only when the administrator allows wrapper execution** (self-hosted). Presets: `group:kotlinMonorepo`, `group:kspMonorepo`, `group:kotlinx-coroutinesMonorepo`, `group:ktorMonorepo`, `group:junit-frameworkMonorepo`, `group:gradle`. | https://docs.renovatebot.com/modules/manager/gradle/ · https://docs.renovatebot.com/modules/manager/gradle-wrapper/ · https://docs.renovatebot.com/presets-group/ |
| Maven KSP | no official JetBrains plugin | — | `me.kpavlov.ksp.maven:ksp-maven-plugin` **0.4.7** (2026-08-03; KSP2, Kotlin 2.3+, JVM 17+, goals `process`/`process-test`, `<extensions>true</extensions>`); `io.mcarle:ksp-maven-plugin` 2.3.5-1 (2026-02-13); `com.dyescape:kotlin-maven-symbol-processing` 1.6 (2023, KSP1-era, dead). | https://repo1.maven.org/maven2/me/kpavlov/ksp/maven/ksp-maven-plugin/maven-metadata.xml · https://github.com/kpavlov/ksp-maven-plugin · https://repo1.maven.org/maven2/io/mcarle/ksp-maven-plugin/maven-metadata.xml |

---

## 2. Deep dive 1 — JVM 21 bytecode from JDK 25

**Facts.**
- Gradle's own docs show the pattern: toolchain 17 + `options.release = 11` ("`--release` does not control which JDK is used — only how the compiler treats source code"). Toolchain 25 + release 21 is the same mechanism. https://docs.gradle.org/current/userguide/toolchains.html
- KGP: `jvmToolchain(N)` "sets `compilerOptions.jvmTarget` to the toolchain's JDK version *if the user doesn't set the `jvmTarget` option explicitly*" — explicit `jvmTarget` wins. https://kotlinlang.org/docs/gradle-configure-project.html
- Kotlin `-Xjdk-release=21`: "Limit the API of the JDK in the classpath to the specified Java version. Automatically sets `-jvm-target`. Possible values `1.8, 9, …, 26`. Not guaranteed to be effective for each JDK distribution (KT-29974)." This is the Kotlin counterpart of javac `--release` and the only compile-time fence against JDK 22+ API (e.g. `List.removeFirst`, `StructuredTaskScope`). https://kotlinlang.org/docs/compiler-reference.html
- `kotlin.jvm.target.validation.mode=error` (default on Gradle 8+) fails when Kotlin `jvmTarget` ≠ Java `targetCompatibility`. **UNVERIFIED**: that KGP reads `options.release` rather than `targetCompatibility` for this check — set `java.targetCompatibility = 21` as well in the spike if it trips.
- Gradle 9.8 daemon runs on JVM 17–27; Kotlin 2.4 compiles on and targets JDK 25/26, so a 25 toolchain is fully supported.

**Convention plugin (`build-logic/src/main/kotlin/sculk.jvm.gradle.kts`).**

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins { kotlin("jvm") }

val buildJdk = 25   // what the compiler, Gradle workers and the "modern" test run use
val targetJdk = 21  // what every published class file and API reference is fenced to

kotlin {
    jvmToolchain(buildJdk)                       // also moves JavaCompile/Test onto JDK 25
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)          // explicit beats the toolchain default
        // API fence: compiling on 25 must not let a 22+ symbol resolve. Without this,
        // jvmTarget alone still sees the full JDK 25 classpath.
        freeCompilerArgs.add("-Xjdk-release=$targetJdk")
        allWarningsAsErrors.set(!name.contains("Test"))
    }
}

tasks.withType<JavaCompile>().configureEach { options.release.set(targetJdk) }

// Two launchers, one build. `test` proves the supported floor; `testJdk25` proves the
// server many hosts actually run (Paper 26.x recommends Java 25).
val testJdk25 by tasks.registering(Test::class) {
    group = "verification"
    val main = sourceSets.test.get()
    testClassesDirs = main.output.classesDirs
    classpath = main.runtimeClasspath
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(buildJdk)) })
}
tasks.test {
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(targetJdk)) })
}
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}
tasks.check { dependsOn(testJdk25) }
```

**Belt and braces: class-file version gate** (`verifyBytecodeLevel`, wired into `check`): open every produced jar
(`jar`, `shadowJar`, `testFixturesJar`), read bytes 6–7 of each `.class`, fail if major > 65 (Java 21). ~30 lines with
`java.util.zip` + `DataInputStream`; ASM 9.11 is optional. This catches a module that forgot the convention plugin, which
the compiler flags cannot.

**CI provisioning.** `actions/setup-java@v5` accepts a multi-line `java-version: |\n 21\n 25`; all are added to PATH, the
last becomes `JAVA_HOME`, and each exports `JAVA_HOME_<MAJOR>_X64`. Put 25 last so the Gradle daemon runs on 25.
Gradle auto-detects `JAVA_HOME` and the current JVM; make the 21 install visible either through
`org.gradle.java.installations.fromEnv=JAVA_HOME_21_X64,JAVA_HOME_25_X64` in `gradle.properties` or by letting the
foojay resolver auto-provision a Temurin 21 (slower, but zero config). Keep foojay in `settings.gradle.kts` regardless so
local contributors on a single JDK still build:

```kotlin
// settings.gradle.kts
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }
```

---

## 3. Deep dive 2 — Static architecture checks

| Option | Works on Kotlin bytecode | Runner coupling | Catches transitive/synthetic references | Verdict |
| --- | --- | --- | --- | --- |
| ArchUnit 1.5.1 (core) | Yes — reads class files | None; `ClassFileImporter().importPath(...)` + `rule.check(classes)` from a plain `main` or any test | Yes: lambdas, `$DefaultImpls`, inline-function residue, `runBlocking` → `BuildersKt.runBlocking` call sites | **Primary** |
| Konsist 0.18.1 | Source/PSI | Test only | Source-level only; misses bytecode-only deps (shaded libs, KSP-generated code); fast and readable for naming/layer rules | Secondary, for naming/KDoc/`@SculkStable` presence |
| Custom ASM task | Yes | Gradle task | Whatever you write | Only for the class-version gate |

**Recommendation.** One module `sculk-archcheck` (not published) with a JUnit 6 suite (`archunit-junit6`) that imports
`build/classes` of every published module via Gradle `Configuration` wiring, runs in `check`. Konsist alongside for the
source-level conventions in `CLAUDE.md` (stability markers on every public declaration, fake for every consumer-called
interface). Rule set from the directive, in ArchUnit DSL:

```kotlin
val classes = ClassFileImporter()
    .withImportOption(ImportOption.DoNotIncludeTests())
    .importPaths(publishedClassDirs)                      // all modules at once; filter by root package per rule

fun module(name: String) = JavaClass.Predicates.resideInAPackage("studio.sculk.$name..")

val rules = listOf(
    noClasses().that(not(module("internals"))).should().dependOnClassesThat().resideInAnyPackage("net.minecraft..", "com.mojang.."),
    noClasses().that().resideInAnyPackage("studio.sculk", "studio.sculk.common..", "studio.sculk.text..", "studio.sculk.config..",
            "studio.sculk.data..", "studio.sculk.discord..", "studio.sculk.web..")
        .should().dependOnClassesThat().resideInAnyPackage("org.bukkit..", "io.papermc..", "net.kyori.adventure.platform.bukkit.."),
    noClasses().that(not(module("packets.packetevents"))).should().dependOnClassesThat().resideInAPackage("com.github.retrooper.."),
    noClasses().that(not(module("packets.protocollib"))).should().dependOnClassesThat().resideInAPackage("com.comphenix.."),
    noClasses().that(not(module("discord.jda"))).should().dependOnClassesThat().resideInAPackage("net.dv8tion.."),
    noClasses().that(not(module("web.ktor"))).should().dependOnClassesThat().resideInAPackage("io.ktor.."),
    noClasses().that(module("data")).and(not(resideInAPackage("studio.sculk.data.driver..")))
        .should().dependOnClassesThat().resideInAnyPackage("org.sqlite..", "org.mariadb..", "org.postgresql..", "com.zaxxer..", "org.h2.."),
    noClasses().that(module("scheduler")).or(module("task")).or(module("platform.folia"))
        .should().dependOnClassesThat().haveFullyQualifiedName("org.bukkit.scheduler.BukkitScheduler"),
    noClasses().that(not(resideInAPackage("..test.."))).should()
        .callMethodWhere(target(owner(name("kotlinx.coroutines.BuildersKt"))).and(target(name("runBlocking")))),
    noClasses().should().dependOnClassesThat().resideInAnyPackage("kotlin.reflect.full..", "kotlin.reflect.jvm.."),
)
rules.forEach { it.check(classes) }
```

Plus a Gradle-side guard (resolution, not bytecode): fail configuration if `org.jetbrains.kotlin:kotlin-reflect` appears
on any `runtimeClasspath` (`configurations.named("runtimeClasspath").incoming.afterResolve { ... }`), replacing the
release-checklist `grep`.

---

## 4. Deep dive 3 — Publishing pipeline

**Central Portal requirements** (https://central.sonatype.org/publish/requirements/): for every jar a `-sources.jar` and
`-javadoc.jar`; `.md5` + `.sha1` for every file (sha256/512 optional); a PGP `.asc` for every file; POM with
`name`, `description`, `url`, ≥1 `license`, ≥1 `developer`, `scm.connection/developerConnection/url`; version not
`-SNAPSHOT`. Namespace `studio.sculk`: add namespace in the Portal → copy verification key → DNS TXT on `sculk.studio`
→ "Verify Namespace" only after the record is live (early clicks cache NXDOMAIN). Only "Published" status matters;
Sonatype confirms a namespace can't be on OSSRH and Portal simultaneously (none exists for us — clean start).

**Plugin choice.** vanniktech 0.37.0: detects KGP/`java-platform`/`version-catalog`/`java-gradle-plugin` components, builds
the Portal bundle, signs, polls validation. JReleaser only if we want Sigstore bundles or GitHub release notes generated
in the same tool — not worth the second config surface in 6.0.

```kotlin
// build-logic/src/main/kotlin/sculk.publish.gradle.kts
plugins { id("com.vanniktech.maven.publish") }

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    pom {
        name.set(project.name); description.set(project.description ?: "Sculk Studio module")
        url.set("https://github.com/SculkStudios/sculk-studio")
        licenses { license { name.set("MIT"); url.set("https://opensource.org/licenses/MIT") } }
        developers { developer { id.set("sculkstudios"); name.set("Sculk Studios"); url.set("https://sculk.studio") } }
        scm { url.set("https://github.com/SculkStudios/sculk-studio"); connection.set("scm:git:https://github.com/SculkStudios/sculk-studio.git") }
    }
}
// Javadoc jar from Dokka HTML — Central only checks presence, Dokka's Javadoc format is Alpha.
val dokkaJavadocJar by tasks.registering(Jar::class) {
    from(tasks.named("dokkaGeneratePublicationHtml")); archiveClassifier.set("javadoc")
}
```

CI secrets → `ORG_GRADLE_PROJECT_mavenCentralUsername/Password` (Portal *user token*, not login),
`ORG_GRADLE_PROJECT_signingInMemoryKey` (ASCII-armoured private key, exported with `gpg --export-secret-keys --armor`),
`ORG_GRADLE_PROJECT_signingInMemoryKeyId`, `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword`. Keep the key as a GitHub
*environment* secret on a `release` environment with required reviewers; never in repository secrets. Publish the public
key to `keys.openpgp.org` and `keyserver.ubuntu.com` (Portal validates against both).

**Workflow shape (`release.yml`, trigger `push: tags: v*`).**

```yaml
permissions: { contents: write, id-token: write, attestations: write }
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v5
        with: { distribution: temurin, java-version: "21\n25" }
      - uses: gradle/actions/setup-gradle@v6         # wrapper validation + cache; dependency-graph optional
      - run: ./gradlew build apiCheck checkKotlinAbi cyclonedxBom publishToMavenCentral --no-configuration-cache
        env: { ORG_GRADLE_PROJECT_mavenCentralUsername: ${{ secrets.CENTRAL_TOKEN_USER }}, ... }
      - uses: actions/attest@v4                       # SLSA provenance for every published jar + SBOM
        with: { subject-path: "**/build/libs/*.jar\nbuild/reports/sbom/bom.json" }
      - uses: softprops/action-gh-release@v2
        with: { files: "build/reports/sbom/bom.json" }   # SBOM + checksums attached to the GitHub release
```

Consumers verify with `gh attestation verify sculk-common-6.0.0.jar -R SculkStudios/sculk-studio`. There is no OIDC path
into Central, so provenance lives on GitHub, not in the Maven repo; the SBOM and attestation reference the Maven
coordinates so the two can be correlated. Attach `bom.json` to the Central deployment too (extra files are allowed) —
nice-to-have, UNVERIFIED that vanniktech passes arbitrary extra artifacts through; a `MavenPublication.artifact(...)` with
classifier `cyclonedx` does.

**Reproducible builds.** Gradle ≥ 9.0 archives are reproducible by default (fixed order, fixed timestamps,
0755/0644). Add `reproducibleFileTimestamp` (incubating) fed from `SOURCE_DATE_EPOCH` = commit time so two rebuilds of
one tag are byte-identical; set `kotlin.incremental=false` and `org.gradle.caching=false` in the release job. Verification:
a second job on a different runner image rebuilds the same tag and diffs `sha256sum **/build/libs/*.jar` against the
published artifacts (download from `repo1.maven.org` after the Portal releases). Publish the diff as a check — that is
exactly what Reproducible Central does for Maven, and it is the only honest answer to "is the jar the source".

---

## 5. Deep dive 4 — Dependency and licence hygiene

**Gradle dependency verification.** Bootstrap once:
`./gradlew --write-verification-metadata sha256,pgp --export-keys` → commits `gradle/verification-metadata.xml` and
`gradle/verification-keyring.keys`. Set `<verify-metadata>true</verify-metadata>`, `<verify-signatures>true</verify-signatures>`,
`<keyring-format>armored</keyring-format>`. Trust publisher keys at group level (`<trusted-key id=".." group="org.jetbrains.kotlin"/>`)
rather than per-artifact so Kotlin bumps don't churn the file. Update flow: dry-run (`--write-verification-metadata sha256 help --dry-run` →
`verification-metadata.dryrun.xml`), real write, review diff, prune old versions by hand (Gradle never prunes), flip
`origin="Generated by Gradle"` to `Verified`. PaperMC/CodeMC snapshots are unsigned → `<trusted-artifacts>` with a
regex on `io.papermc` only, never on a wildcard. Repositories: drop JitPack and dmulloy2 from `settings.gradle.kts`
(PacketEvents is on Maven Central via `com.github.retrooper`; ProtocolLib needs dmulloy2 — keep it scoped to
`sculk-packets-protocollib` with `exclusiveContent`).

**Renovate (`renovate.json`).**

```json
{ "$schema": "https://docs.renovatebot.com/renovate-schema.json",
  "extends": ["config:recommended", "group:kotlinMonorepo", "group:kspMonorepo", "group:kotlinx-coroutinesMonorepo",
              "group:junit-frameworkMonorepo", "group:gradle", ":semanticCommits"],
  "packageRules": [
    { "groupName": "kotlin toolchain", "matchPackagePatterns": ["^org\\.jetbrains\\.kotlin", "^com\\.google\\.devtools\\.ksp", "^org\\.jetbrains\\.dokka"] },
    { "matchPackageNames": ["io.papermc.paper:paper-api"], "allowedVersions": "/^26\\.3\\./" },
    { "matchManagers": ["gradle-wrapper"], "schedule": ["before 6am on monday"] }
  ],
  "gradle": { "enabled": true } }
```

Caveat: the hosted Mend app does not execute `gradlew`, so `verification-metadata.xml` is **not** auto-updated — the
Renovate PR will fail CI with a verification error until a maintainer runs the write command. Either self-host Renovate
with wrapper execution allowed, or add a `workflow_dispatch` job that refreshes the metadata on Renovate branches and
pushes the commit (same trust level as the bot). KSP must move with Kotlin — the `kotlin toolchain` group above enforces it.

**Licence report.** Apply `com.github.jk1.dependency-license-report` 3.1.4 at root with `allowedLicensesFile`
(MIT, Apache-2.0, BSD-2/3, EPL-2.0, LGPL-2.1 *as separate artifact only*) and `checkLicense` in `check`; exclude
`compileOnly` configurations from the allow-list check and report them separately.

**GPL adapters in an MIT library — commonly held positions (not legal advice).**
1. *FSF view*: a program that links (statically or dynamically) to a GPL library is a combined work; distributing the
   combination requires GPL-compatible terms for the whole. Under this view a shaded jar containing PacketEvents is GPL-3.
2. *Separate-works / "mere aggregation" view* (common among practitioners): an adapter that is distributed on its own,
   compiled against the GPL API (`compileOnly`), never ships GPL bytes, and is combined only at runtime by the *server
   operator* who already has the GPL plugin, is a separate work; the one who distributes the combination bears the
   obligation. Oracle v. Google leaves API-surface copying contested, which makes position 1 harder to assert against
   API-only use.
3. *Incompatibility*: ProtocolLib is GPL-2.0 (no "or later" shown by GitHub's detector) and PacketEvents GPL-3.0 — these
   two cannot be in one combined work at all. That alone mandates separate adapter artifacts.
4. PacketEvents' own FAQ tells consumers not to shade the API; JDA's graph reaches LGPL trove4j/jna (already handled in 5.x).

**Recommended documented stance** (`LICENSING.md`, linked from every adapter README and POM description):
Sculk core and the adapter *source* are MIT. Adapters are published as separate artifacts, depend on the GPL library as
`compileOnly`/`<scope>provided</scope>`, bundle none of its bytes, and are only ever loaded when the operator has installed
that plugin. Sculk never shades PacketEvents/ProtocolLib and the Gradle plugin refuses to (`shadowJar` dependency filter
excluding `com.github.retrooper:*` and `net.dmulloy2:*`, failing loudly). A consumer who *does* bundle a GPL library into
their product distributes a GPL-governed work and is told so in the docs. That is the position most Paper ecosystem
libraries (ProtocolLib adapters in Apache/MIT libraries) have taken for a decade; it is defensible, not certain.

---

## 6. Deep dive 5 — Consumer experience

**Gradle.** Publish `studio.sculk:sculk-bom` (`java-platform`, auto-generated from modules — keep the 5.x generator) and a
catalog `studio.sculk:sculk-catalog` via the `version-catalog` plugin (`from(files("gradle/sculk.versions.toml"))`,
`from(components["versionCatalog"])`; published with `.toml` extension). Consumer:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement { versionCatalogs { create("sculk") { from("studio.sculk:sculk-catalog:6.0.0") } } }
// build.gradle.kts
dependencies { implementation(platform(sculk.bom)); implementation(sculk.commands); implementation(sculk.gui) }
```

The catalog must list *only* Sculk modules plus the handful of transitive libraries consumers must align (coroutines,
serialization) — republishing Paper/JUnit versions in our catalog invites conflicts with the consumer's own `libs`.

**Maven.** First-class means: Gradle module metadata is not read by Maven, so every variant consumers need must be a plain
artifact with a correct POM — `testFixtures` published as `<artifactId>-test-fixtures` jars (`java-test-fixtures` does this
through `maven-publish`; verify the POM has no `capabilities` leakage). Reference `pom.xml` for the docs:

```xml
<dependencyManagement><dependencies>
  <dependency><groupId>studio.sculk</groupId><artifactId>sculk-bom</artifactId><version>6.0.0</version><type>pom</type><scope>import</scope></dependency>
</dependencies></dependencyManagement>
<dependencies>
  <dependency><groupId>studio.sculk</groupId><artifactId>sculk-commands</artifactId></dependency>
  <dependency><groupId>io.papermc.paper</groupId><artifactId>paper-api</artifactId><version>26.3.build.175-stable</version><scope>provided</scope></dependency>
  <dependency><groupId>studio.sculk</groupId><artifactId>sculk-common</artifactId><classifier>test-fixtures</classifier><scope>test</scope></dependency>
</dependencies>
<properties><kotlin.version>2.4.21</kotlin.version><maven.compiler.release>21</maven.compiler.release></properties>
<build><plugins>
  <plugin><groupId>org.jetbrains.kotlin</groupId><artifactId>kotlin-maven-plugin</artifactId><version>${kotlin.version}</version>
    <extensions>true</extensions> <!-- Kotlin 2.4: infers jvmTarget AND jdkRelease from maven.compiler.release -->
    <configuration><compilerPlugins><plugin>kotlinx-serialization</plugin></compilerPlugins></configuration>
    <dependencies><dependency><groupId>org.jetbrains.kotlin</groupId><artifactId>kotlin-maven-serialization</artifactId><version>${kotlin.version}</version></dependency></dependencies>
    <executions><execution><id>compile</id><goals><goal>compile</goal></goals></execution>
                <execution><id>test-compile</id><goals><goal>test-compile</goal></goals></execution></executions></plugin>
  <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-shade-plugin</artifactId><version>3.6.0</version>
    <executions><execution><phase>package</phase><goals><goal>shade</goal></goals><configuration>
      <relocations><relocation><pattern>studio.sculk</pattern><shadedPattern>com.example.myplugin.libs.sculk</shadedPattern></relocation>
                   <relocation><pattern>kotlin</pattern><shadedPattern>com.example.myplugin.libs.kotlin</shadedPattern></relocation>
                   <relocation><pattern>kotlinx</pattern><shadedPattern>com.example.myplugin.libs.kotlinx</shadedPattern></relocation></relocations>
      <filters><filter><artifact>*:*</artifact><excludes><exclude>META-INF/*.SF</exclude><exclude>META-INF/*.RSA</exclude></excludes></filter></filters>
    </configuration></execution></executions></plugin>
</plugins></build>
```

(maven-shade-plugin 3.6.0 is from memory — **UNVERIFIED**, check before docs ship.) Maven KSP: document
`me.kpavlov.ksp.maven:ksp-maven-plugin` 0.4.7 with `<extensions>true</extensions>` and the Sculk processor declared under the
*plugin's* `<dependencies>`; it is community-maintained, so Sculk's codegen stays optional and every generated artefact has
a reflection-free, hand-writable equivalent (the 5.x "descriptor tree, no kotlin-reflect" rule already guarantees this).

**Packaging guidance for consumers (docs page).** Three supported shapes, in order of preference:
(1) *Runtime resolution* — `paper-plugin.yml` `PluginLoader` + `MavenLibraryResolver` pulling `studio.sculk:*` from a
Central mirror (Paper warns if Central is used directly; `PAPER_DEFAULT_CENTRAL_REPOSITORY`). No relocation, smallest jar,
but every plugin gets its own classloader copy — fine. (2) *Shade + relocate* — Shadow 9.6.1 with `relocate("studio.sculk", ...)`
and `relocate("kotlin", ...)`, `minimize()` off (serialization descriptors), exclude `META-INF/*.kotlin_module` collisions via
`mergeServiceFiles()`. The `sculk-gradle-plugin` applies this configuration. (3) *Shared library plugin* — a future
`SculkLib` Paper plugin exposing one copy; out of scope for 6.0 but the module layout must not preclude it (no static singletons).

**Consumer verification projects.** `consumers/gradle-plugin` and `consumers/maven-plugin` in the repo, *not* included in
`settings.gradle.kts`; CI job `consumers` runs after `publishToMavenLocal`/snapshot publish: `./gradlew -p consumers/gradle-plugin build`
and `mvn -f consumers/maven-plugin/pom.xml -s ci-settings.xml verify` with the version injected (`-Psculk.version` / `-Dsculk.version`).
Both build a plugin jar, both run one MockBukkit test against `mockbukkit-v26.2`, and the Maven one must compile with
`kotlin-maven-plugin` only (no Gradle metadata) — that is the test that `api`/`implementation` split produced a sane POM.
Snapshots: the Central Portal snapshot repository (`https://central.sonatype.com/repository/maven-snapshots/`) — KSP's own
nightlies use it, so it is live; vanniktech routes `-SNAPSHOT` versions there automatically.

---

## 7. Deep dive 6 — Integration-test harness

**run-task 3.1.0 capabilities** (verified from README/wiki; signatures from DeepWiki's code summary — re-check against
3.1.0 sources in the spike): tasks extend `JavaExec` → `jvmArgs(...)`, `systemProperty(...)`, `args(...)`,
`runDirectory.set(...)`; `minecraftVersion("26.3")`; `build(n)` pins a Fill build; `downloadPlugins { modrinth(..); hangar(..);
github(owner, repo, tag, asset); url(..) }`; shared `runPaper.downloadPluginsSpec { }`; `pluginJars(...)`; your `shadowJar`
auto-detected; `--nogui` added automatically; `disable.watchdog=true` set by default; `runPaper.folia.registerTask { }` →
`runFolia` using the Fill `folia` project with `pluginsMode` `INHERIT_ALL | INHERIT_NONE | PLUGIN_JAR_DETECTION`;
`runVelocity { velocityVersion("4.2.0") }`. **Arbitrary jars**: `serverJar(file)` / `runClasspath.from(...)` bypass the
downloads API — this is how Canvas (not on Fill; downloads from canvasmc.io, Java 25+) and Leaf run: a `Download` task
fetches the jar (pinned URL + sha256) into `build/servers/` and the task points at it. There is no URL-based
`DownloadsAPIService` for non-PaperMC projects; registering a custom one is possible but not worth it for two forks.

**Fill v3** (https://docs.papermc.io/misc/downloads-api/): `GET https://fill.papermc.io/v3/projects/{project}` (versions grouped),
`/versions/{v}/builds` (array with `channel` ∈ STABLE/BETA/ALPHA, `id`, `downloads["server:default"].url`). Mandatory
non-generic `User-Agent` with contact (`sculk-studio-ci/6.0 (software@talentbenut.nl)`). Pin `STABLE` only in CI; Paper
26.3 has stable builds since build 175.

**Harness design.** Module `integration-tests/` (not published, not in `apiCheck`):
- `sculk-itest-plugin`: a Paper plugin (`folia-supported: true`) that at `onEnable` runs a JUnit-Platform launcher *inside the
  server* over `@ServerTest` classes from `sculk-*` test fixtures, writes `run/results/<suite>.json`, then
  `Bukkit.shutdown()`. Failure of any test → non-zero exit via `System.exit(1)` after the shutdown hook flushes.
- Three tasks: `runPaperItest` (`minecraftVersion("26.3")`), `runFoliaItest` (`runPaper.folia.registerTask`), `runVelocityItest`
  plus `runCanvasItest` (`serverJar`). Each `timeout.set(Duration.ofMinutes(8))`, `standardInput = InputStream.nullInputStream()`
  (default is `System.in` — blocks in CI), `jvmArgs("-Xmx1536m", "-Dcom.mojang.eula.agree=true")`, `runDirectory` under
  `build/`, EULA file pre-seeded by a `doFirst`.
- `verifyItestResults` parses the JSON files and converts them into Gradle test-report XML so GitHub's test summary shows them.
- CI matrix `{paper, folia, velocity} × {jdk21, jdk25}`; Canvas nightly-only (`schedule`), since its builds are unversioned.
- Unit level stays on MockBukkit (`mockbukkit-v26.2` 4.117.0 today; there is no 26.3 artifact yet — MockBukkit trails Paper by
  weeks, so the unit matrix must be allowed to lag the integration matrix by one MC version).

---

## 8. Deep dive 7 — Scaffolding

`sculk-gradle-plugin` (`studio.sculk`, Gradle Plugin Portal via `com.gradle.plugin-publish` 2.2.1, mirrored to Central so
Maven-side `pluginManagement` resolution and dependency verification both work). It must: apply `kotlin("jvm")` with the
JDK-25/`release 21` convention from §2; add `paper-api 26.3.build.*` as `compileOnly`; apply `de.eldoria.plugin-yml.paper`
0.9.0 and fill `paper-plugin.yml` (`folia-supported`, Sculk loader class); apply Shadow with the relocation + GPL-refusal
filter from §5; register `runServer`/`runFolia`/`runVelocity` via run-task; wire MockBukkit + JUnit 6 test defaults; expose
`sculk { modules("commands", "gui"); packaging = SHADE | RUNTIME_LOADER }`. The non-prefixed group `studio.sculk` needs
one manual Portal approval on first publish — publish a `0.0.1` placeholder early to absorb the delay.

Templates: Gradle's `BuildInitSpec` (incubating since 8.12, still incubating) lets a plugin add `gradle init --type sculk-plugin`
types; **UNVERIFIED** CLI syntax for loading the spec plugin (`-Dorg.gradle.buildinit.specs=…`) — the user guide no longer
documents it. Given that, a web generator (Astro island on docs.daisy.cat emitting a zip from a template tree, or a
`degit`-style `bunx create-sculk-plugin`) is more practical and also produces the Maven variant, which `gradle init` never
will. Ship the generator; revisit `BuildInitSpec` when it stabilises.

---

## 9. Recommended stack

| Tool | Version | Why |
| --- | --- | --- |
| Gradle wrapper | 9.8.1 | Current stable with security fixes; JVM 17–27; embedded Kotlin 2.4.10 for build-logic. Accept KGP "9.7.0 max" warnings; re-check at 2.4.30/2.5. |
| Kotlin / KGP / serialization plugin | 2.4.21 | Latest stable; 2.5 is Beta. |
| JDK (build) / bytecode target | Temurin 25 / 21 via `jvmTarget` + `-Xjdk-release=21` + `options.release=21` + class-version gate | The only combination that both uses the current JDK and fences the API. |
| KSP | 2.3.12 (`com.google.devtools.ksp`) | Decoupled versioning; KSP2 only. Pairing with 2.4.21 UNVERIFIED — spike first. |
| kotlinx-coroutines / serialization / kaml | 1.11.0 / 1.11.0 / 0.104.0 | Stable set already proven in 5.x; move to serialization 1.12 when final. |
| API dumps | KGP built-in `abiValidation` (opt-in) | BCV is frozen; built-in is the successor and its filters cover `@SculkInternal`. Fall back to BCV 0.18.2 only if the 2.4 DSL breaks in 2.5. |
| Lint | ktlint 1.8.0 via ktlint-gradle 14.2.0 | Unchanged from 5.x; no official alternative. |
| Docs | Dokka 2.2.0 (+ `dokka-javadoc` for Central's javadoc jar) | DGP v2 aggregation, K2 analysis. |
| Tests | JUnit 6.1.3, MockBukkit `-v26.2` 4.117.0, ArchUnit 1.5.1, Konsist 0.18.1 | JUnit 6 is the only line Kotlin 2.4-era libs target; MockBukkit per-MC artifact. |
| Benchmarks | JMH 1.37 via a ~60-line build-logic convention (bytecode generator + `JavaExec`) | me.champeau.jmh 0.7.3 lacks Gradle 9 support in any release. |
| Integration | run-paper/run-velocity 3.1.0, Fill v3, `serverJar` for Canvas | Covers Paper/Folia/Velocity natively; Canvas via pinned download. |
| Internals tier | paperweight-userdev 2.0.0-beta.24 | Required for `net.minecraft` access; Gradle ≥ 9.7.1, Java 21 — compatible with the 25 toolchain. |
| Packaging | Shadow 9.6.1; plugin-yml `de.eldoria` 0.9.0 | Current, Gradle 9 native. |
| Publishing | vanniktech maven-publish 0.37.0 → Central Portal; GPG `.asc` + optional Sigstore later | No official Sonatype Gradle plugin; OSSRH gone. |
| Plugin Portal | com.gradle.plugin-publish 2.2.1 | For `studio.sculk`. |
| SBOM / provenance | CycloneDX 3.5.1; actions/attest v4 | Aggregate SBOM on root; attestations on GitHub (Central has no OIDC). |
| Dependency hygiene | Gradle verification metadata (sha256+pgp), Renovate with Kotlin/KSP grouping, jk1 license-report 3.1.4 | Supply-chain floor; GPL adapters stay `compileOnly` and separate. |
| Toolchains in CI | setup-java@v5 (21 + 25), foojay 1.0.0, gradle/actions@v6 | Two launchers, one build. |
| JitPack | removed (`jitpack.yml` deleted) | Central + Portal snapshots replace it; keep only a README note for forks. |

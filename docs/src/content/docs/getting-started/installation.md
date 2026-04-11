---
title: Installation
description: Add Sculk Studio to your Minecraft Paper plugin via JitPack.
---

Sculk Studio is distributed through [JitPack](https://jitpack.io). Add the repository and the modules you need to your build file.

## Gradle (Kotlin DSL)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}
```

```kotlin
// build.gradle.kts
dependencies {
    // Always required
    implementation("com.github.SculkStudios.sculk-studio:sculk-platform:1.0.0")

    // Optional modules (transitively included via sculk-platform, but explicit for clarity)
    implementation("com.github.SculkStudios.sculk-studio:sculk-core:1.0.0")
    implementation("com.github.SculkStudios.sculk-studio:sculk-config:1.0.0")
    implementation("com.github.SculkStudios.sculk-studio:sculk-data:1.0.0")
    implementation("com.github.SculkStudios.sculk-studio:sculk-effects:1.0.0")
}
```

## Gradle (Groovy DSL)

```groovy
// settings.gradle
dependencyResolutionManagement {
    repositories {
        maven { url 'https://jitpack.io' }
        maven { url 'https://repo.papermc.io/repository/maven-public/' }
    }
}
```

```groovy
// build.gradle
dependencies {
    implementation 'com.github.SculkStudios.sculk-studio:sculk-platform:1.0.0'
}
```

## Shading

Sculk Studio must be shaded into your plugin jar. Use the Shadow plugin:

```kotlin
plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

tasks.shadowJar {
    archiveClassifier = ""
    relocate("gg.sculk", "your.plugin.libs.sculk")
}
```

## Requirements

- **Paper** 1.21.x (1.21.11-R0.1-SNAPSHOT or later)
- **Java** 21+
- **Kotlin** 2.x (if writing Kotlin plugins)

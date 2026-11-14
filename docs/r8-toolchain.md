# Android R8 and Kotlin metadata compatibility

The release sample pins **R8 9.1.56** through `pluginManagement.buildscript` in
`settings.gradle.kts`. Its Android Gradle plugin remains 9.0.1 and its Gradle
wrapper remains 9.1.0. This focused override fixes metadata parsing without
suppressing warnings or changing Kotlin code.

## Verified diagnosis and fix

The original optimized APK's mapping header identified **R8 9.0.32**. AGP 9.0.1
embeds that compiler in `builder.jar`; a separate cached R8 9.1.29 artifact did
not establish which compiler Gradle actually used.

Running release minification with `--info` exposed the underlying errors. These
classes had valid Kotlin metadata version **2.4.0**, while the embedded compiler
accepted at most **2.3.0**:

- `engineering.carr.hanzianimator.compose.StrokeOrderCanvasKt$StrokeOrderCanvas$inputModifier$1$1`
- `engineering.carr.hanzianimator.data.BundledDataSource_androidKt$platformBundledDataSource$1`
- `engineering.carr.hanzianimator.data.BundledDataSource_androidKt$androidBundledDataSource$1`

The classes were not malformed source output. An independent scan using R8
9.1.29's metadata reader accepted the project and cached runtime metadata,
including version 2.4.0. The older embedded compiler caused the warnings.

On 2026-10-12, the APK/AAB release build succeeded with the override. The new
mapping header confirmed `compiler_version: 9.1.56`, and the complete `--info`
log contained **no Kotlin metadata errors or warnings**. Evidence artifacts are
`build/android-final.log` and
`androidApp/build/outputs/mapping/release/mapping.txt`; both are build outputs.

## Consumer requirements

Google's [Kotlin compatibility table](https://developer.android.com/build/kotlin-support)
requires R8 **9.1.29 or newer** for Kotlin 2.4 class files. Consumers should use
AGP 9.1 or newer, or a compatible explicit R8 override when retaining an older
AGP. AGP 9.1 requires Gradle 9.3.1; see its
[official compatibility notes](https://developer.android.com/build/releases/agp-9-1-0-release-notes?hl=en).

The repository uses Google's documented
[R8 replacement mechanism](https://r8.googlesource.com/r8/+/c435b231b47303c6e4a61da7a3d01ac6aeb3dd6f#replacing-r8-in-agp):

```kotlin
pluginManagement {
    buildscript {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            classpath("com.android.tools:r8:9.1.56")
        }
    }
}
```

Verify the compiler used by an actual minified release build from its mapping
header. Checking an arbitrary cached jar's `--version` is insufficient. If a
future upgrade produces generic metadata warnings, capture the minification
task with `--info` to obtain class names and the underlying exception before
changing compiler versions. Do not silence those warnings with keep rules or
warning suppression.

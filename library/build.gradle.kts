import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    explicitApi()

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "HanziAnimator"
            isStatic = true
        }
    }

    jvm()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    android {
        namespace = "engineering.carr.hanzianimator"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "engineering.carr.hanzianimator.internal.resources"
}

mavenPublishing {
    publishToMavenCentral()
    // Signing is required for the Central publish and is configured on CI via
    // the ORG_GRADLE_PROJECT_signingInMemoryKey env var. Locally (e.g. for
    // publishToMavenLocal) it is skipped when no key is present.
    if (providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKey").isPresent ||
        project.findProperty("signingInMemoryKey") != null
    ) {
        signAllPublications()
    }

    coordinates("engineering.carr", "shenzhen-hanzi-animator", "0.1.0")

    pom {
        name.set("shenzhen-hanzi-animator")
        description.set("Kotlin Multiplatform library that animates hanzi stroke order and quizzes drawing, for Compose Multiplatform.")
        inceptionYear.set("2026")
        url.set("https://github.com/marcuscarr/shenzhen-hanzi-animator")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("marcuscarr")
                name.set("Marcus Carr")
                url.set("https://github.com/marcuscarr")
            }
        }
        scm {
            url.set("https://github.com/marcuscarr/shenzhen-hanzi-animator")
            connection.set("scm:git:git://github.com/marcuscarr/shenzhen-hanzi-animator.git")
            developerConnection.set("scm:git:ssh://git@github.com/marcuscarr/shenzhen-hanzi-animator.git")
        }
    }
}

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    android { namespace = "engineering.carr.hanzianimator.sample.shared"; compileSdk = 36; minSdk = 24; compilerOptions { jvmTarget = JvmTarget.JVM_11 }; androidResources { enable = true } }
    iosArm64().binaries.framework { baseName = "HanziSample"; isStatic = true }
    iosSimulatorArm64().binaries.framework { baseName = "HanziSample"; isStatic = true }

    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":library"))
            implementation(project(":bundled-data"))
            implementation(project(":material3"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
        }
        androidMain.dependencies {

        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

compose.resources {
    packageOfResClass = "engineering.carr.hanzianimator.sample.resources"
}

// A browser host serves decoded archives at ./strokes/ (with optional HTTP sidecars).
kotlin.sourceSets.named("wasmJsMain") {
    resources.srcDir(project(":bundled-data").layout.projectDirectory.dir("src/webMain/resources"))
}

compose.desktop {
    application {
        mainClass = "engineering.carr.hanzianimator.sample.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "shenzhen-hanzi-animator-sample"
            packageVersion = "1.0.0"
        }
    }
}

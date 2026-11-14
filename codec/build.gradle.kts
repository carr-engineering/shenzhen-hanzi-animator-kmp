import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.mavenPublish)
}
kotlin {
    explicitApi()
    android { namespace = "engineering.carr.hanzianimator.codec"; compileSdk = 36; minSdk = 24; compilerOptions { jvmTarget = JvmTarget.JVM_11 } }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    js { browser() }
    @OptIn(ExperimentalWasmDsl::class) wasmJs { browser() }
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
        }
        commonTest.dependencies { implementation(libs.kotlin.test) }
        jvmTest.dependencies { implementation(project(":json")); implementation(libs.kotlinx.serialization.json) }
    }
}

tasks.withType<Test>().configureEach { systemProperty("hanzi.repositoryRoot", rootProject.projectDir.absolutePath) }

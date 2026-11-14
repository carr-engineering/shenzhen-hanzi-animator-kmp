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
    android { namespace = "engineering.carr.hanzianimator.compose"; compileSdk = 36; minSdk = 24; compilerOptions { jvmTarget = JvmTarget.JVM_11 } }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    js { browser() }
    @OptIn(ExperimentalWasmDsl::class) wasmJs { browser() }
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(libs.compose.runtime)
            api(libs.compose.ui)
            implementation(libs.compose.foundation)
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies { implementation(libs.kotlin.test); implementation(libs.kotlinx.coroutines.test) }
        jvmTest.dependencies { implementation(compose.desktop.currentOs); implementation(project(":json")); implementation(project(":bundled-data")); implementation(libs.kotlinx.serialization.json); implementation(libs.compose.material3) }
    }
}

abstract class GenerateLibraryVersion : DefaultTask() {
    @get:Input abstract val libraryVersion: Property<String>
    @get:OutputDirectory abstract val sourceDirectory: DirectoryProperty
    @TaskAction fun generate() {
        sourceDirectory.file("engineering/carr/hanzianimator/LibraryVersion.kt").get().asFile.apply {
            parentFile.mkdirs()
            writeText("package engineering.carr.hanzianimator\ninternal const val LIBRARY_VERSION: String = \"${libraryVersion.get()}\"\n")
        }
    }
}
val generatedVersion = layout.buildDirectory.dir("generated/libraryVersion")
val generateLibraryVersion = tasks.register<GenerateLibraryVersion>("generateLibraryVersion") {
    libraryVersion.set(project.version.toString())
    sourceDirectory.set(generatedVersion)
}
kotlin.sourceSets.commonMain { kotlin.srcDir(generateLibraryVersion.map { it.sourceDirectory }) }
tasks.matching { it.name.startsWith("compile") }.configureEach { dependsOn(generateLibraryVersion) }

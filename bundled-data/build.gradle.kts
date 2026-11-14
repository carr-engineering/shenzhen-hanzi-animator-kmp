import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.mavenPublish)
}
kotlin {
    explicitApi()
    android { namespace = "engineering.carr.hanzianimator.bundleddata"; compileSdk = 36; minSdk = 24; compilerOptions { jvmTarget = JvmTarget.JVM_11 } }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    js { browser() }
    @OptIn(ExperimentalWasmDsl::class) wasmJs { browser() }
    sourceSets {
        commonMain.dependencies {
            api(project(":data"))
        }
        commonTest.dependencies { implementation(libs.kotlin.test); implementation(libs.kotlinx.coroutines.test) }
    }
}

// The HZB1 stroke packs are generated from the pinned source corpus and not committed; every task
// that packages resources first checks they exist (tools/prepare_bundled_data.sh creates them).
val strokePackDirs = listOf("androidMain", "jvmMain", "iosMain", "webMain").map { file("src/$it/resources/strokes") }
val checkStrokePacks = tasks.register("checkStrokePacks") {
    val dirs = strokePackDirs
    doLast {
        val missing = dirs.filter { !it.resolve("manifest.json").isFile }
        if (missing.isNotEmpty()) throw GradleException(
            "Generated stroke packs are missing (${missing.joinToString { it.path }}): run tools/prepare_bundled_data.sh"
        )
    }
}
tasks.configureEach {
    if (name != checkStrokePacks.name && (name.contains("Resources") || name.contains("Assets"))) dependsOn(checkStrokePacks)
}

// Raw byte resources are copied with opaque extensions; no generated Compose reader.
kotlin.sourceSets.named("jsMain") { resources.srcDir("src/webMain/resources") }
kotlin.sourceSets.named("wasmJsMain") { resources.srcDir("src/webMain/resources") }
// Android libraries publish these native assets directly in the AAR.
kotlin.android { androidResources { enable = true } }

kotlin.sourceSets.named("androidMain") { resources.setSrcDirs(emptyList<String>()) }
extensions.configure<com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension> {
    onVariants { variant -> variant.sources.assets?.addStaticSourceDirectory("src/androidMain/resources") }
}

// Native frameworks cannot discover JVM/Compose resources. Publish explicit host bundles.
val iosStrokeResources = tasks.register<Zip>("iosStrokeResources") {
    archiveClassifier.set("ios-data")
    from("src/iosMain/resources")
    from("src/commonMain/resources")
}
val webStrokeResources = tasks.register<Zip>("webStrokeResources") {
    archiveClassifier.set("web-data")
    from("src/webMain/resources")
    from("src/commonMain/resources")
}
extensions.configure<org.gradle.api.publish.PublishingExtension> {
    publications.withType<org.gradle.api.publish.maven.MavenPublication>().configureEach {
        if (name == "kotlinMultiplatform") {
            artifact(iosStrokeResources)
            artifact(webStrokeResources)
        }
    }
}

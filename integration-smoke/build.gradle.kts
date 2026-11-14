import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    kotlin("jvm") version "2.4.10" apply false
    kotlin("plugin.compose") version "2.4.10" apply false
}

val smokeVersion = providers.gradleProperty("smokeVersion").orElse("0.1.0")
val publishedModules = mapOf(
    "headless" to listOf("core", "codec"),
    "compose" to listOf("compose"),
    "bundled" to listOf("bundled-data"),
    "json" to listOf("json"),
    "material3" to listOf("material3"),
)
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    if (name in listOf("compose", "material3")) apply(plugin = "org.jetbrains.kotlin.plugin.compose")
    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
    }
    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }
    dependencies {
        publishedModules.getValue(project.name).forEach {
            add("implementation", "engineering.carr:shenzhen-hanzi-animator-$it:${smokeVersion.get()}")
        }
        add("testImplementation", "org.jetbrains.kotlin:kotlin-test-junit:2.4.10")
    }
    tasks.register("verifyDependencyClosure") {
        group = "verification"
        doLast {
            val ids = configurations.getByName("runtimeClasspath").incoming.resolutionResult.allComponents
                .mapNotNull { it.id as? ModuleComponentIdentifier }
                .map { "${it.group}:${it.module}" }.sorted()
            check(ids.any { it.startsWith("engineering.carr:shenzhen-hanzi-animator-") }) { "No published Hanzi dependency resolved" }
            check(ids.none { it.startsWith("io.ktor:") || it.contains("components-resources") }) { "Unexpected network/resource dependency: $ids" }
            if (project.name != "material3") check(ids.none { it.contains("material3") || it.startsWith("org.jetbrains.compose.material:") || it.startsWith("androidx.compose.material:") }) { "Unexpected Material dependency: $ids" }
            if (project.name in listOf("headless", "bundled", "json")) {
                check(ids.none { it.startsWith("org.jetbrains.compose") || it.startsWith("androidx.compose") || it.contains("skiko") }) { "Headless consumer pulls UI: $ids" }
            }
            if (project.name == "headless") {
                val allowed = ids.filterNot { it.startsWith("engineering.carr:") || it.startsWith("org.jetbrains.kotlin:kotlin-stdlib") || it == "org.jetbrains:annotations" }
                check(allowed.isEmpty()) { "Core/codec must use only stdlib: $allowed" }
            }
            if (project.name != "json") {
                check(ids.none { it.contains("shenzhen-hanzi-animator-json") || it.startsWith("org.jetbrains.kotlinx:kotlinx-serialization-json") }) { "Unexpected JSON importer/runtime: $ids" }
            }
            if (project.name in listOf("headless", "bundled")) {
                check(ids.none { it.startsWith("org.jetbrains.kotlinx:kotlinx-serialization") }) { "Unexpected headless serialization dependency: $ids" }
            }
            val report = layout.buildDirectory.file("reports/runtime-dependencies.txt").get().asFile
            report.parentFile.mkdirs()
            report.writeText(ids.joinToString("\n", postfix = "\n"))
            println("${project.name}: ${ids.size} runtime modules; dependency closure passed")
        }
    }
    tasks.named("check") { dependsOn("verifyDependencyClosure") }
    if (name == "bundled") {
        val iosData = configurations.create("publishedIosData") { isCanBeConsumed = false }
        val webData = configurations.create("publishedWebData") { isCanBeConsumed = false }
        dependencies {
            add(iosData.name, "engineering.carr:shenzhen-hanzi-animator-bundled-data:${smokeVersion.get()}:ios-data@zip")
            add(webData.name, "engineering.carr:shenzhen-hanzi-animator-bundled-data:${smokeVersion.get()}:web-data@zip")
        }
        tasks.register("verifyPublishedResourceBundles") {
            group = "verification"
            doLast {
                for (configuration in listOf(iosData, webData)) {
                    java.util.zip.ZipFile(configuration.singleFile).use { archive ->
                        val entries = archive.entries().asSequence().filterNot { it.isDirectory }.map { it.name }.toSet()
                        for (name in listOf("Arphic-Public-License.txt", "makemeahanzi-COPYING.txt", "makemeahanzi-LGPL.txt", "makemeahanzi-GPL.txt")) {
                            check("licenses/$name" in entries) { "Missing $name in ${configuration.name}" }
                        }
                        check("strokes/manifest.json" in entries) { "Missing profile manifest" }
                        if (configuration == iosData) {
                            check("strokes/all.hzb" in entries) { "Missing iOS bundle data" }
                        } else {
                            check("strokes/index.txt" in entries) { "Missing web character index" }
                            check(entries.count { it.endsWith(".hzb") } == 1024) { "Web profile must include 1,024 raw blocks" }
                            check(entries.count { it.endsWith(".hzb.gz") } == 1024) { "Missing gzip HTTP sidecars" }
                            check(entries.count { it.endsWith(".hzb.br") } == 1024) { "Missing Brotli HTTP sidecars" }
                        }
                    }
                }
            }
        }
        tasks.named("check") { dependsOn("verifyPublishedResourceBundles") }
    }
}

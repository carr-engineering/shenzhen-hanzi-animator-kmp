pluginManagement { repositories { gradlePluginPortal(); google(); mavenCentral() } }
dependencyResolutionManagement {
    repositories {
        // Only the published Hanzi modules may be resolved from this repository.
        exclusiveContent {
            forRepository { maven { url = uri(providers.gradleProperty("smokeRepositoryUrl").get()) } }
            filter { includeGroup("engineering.carr") }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "hanzi-published-consumer-smoke"
include("headless", "compose", "bundled", "json", "material3")

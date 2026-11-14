import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.publish.PublishingExtension

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.mavenPublish) apply false
}
allprojects {
    group = "engineering.carr"
    version = providers.gradleProperty("libraryVersion").get()
    plugins.withId("com.vanniktech.maven.publish") {
        providers.gradleProperty("publicationTestRepositoryUrl").orNull?.let { testUrl ->
            extensions.configure<PublishingExtension> {
                repositories.maven { name = "IntegrationSmoke"; url = uri(testUrl) }
            }
        }
        extensions.configure<MavenPublishBaseExtension> {
            publishToMavenCentral()
            coordinates("engineering.carr", "shenzhen-hanzi-animator-${project.name.removePrefix("library").ifEmpty { "compose" }}", project.version.toString())
            if (providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKey").isPresent) signAllPublications()
            val repositoryUrl = providers.gradleProperty("publicationRepositoryUrl").orElse("https://github.com")
            pom {
                name.set("shenzhen-hanzi-animator-${project.name}")
                description.set("Hanzi stroke animation and practice: ${project.name} module")
                inceptionYear.set("2026")
                url.set(repositoryUrl)
                licenses {
                    license { name.set("MIT License (Kotlin code)"); url.set("https://opensource.org/licenses/MIT"); distribution.set("repo") }
                    if (project.name == "bundled-data") {
                        license { name.set("Arphic Public License (glyph data)"); url.set("https://github.com/chanind/hanzi-writer-data/blob/68d10a4b21150cae5e1ebbd223eed289cf32d90c/ARPHICPL.TXT"); distribution.set("repo") }
                        license { name.set("GNU Lesser General Public License 3.0 or later (component metadata)"); url.set("https://www.gnu.org/licenses/lgpl-3.0.html"); distribution.set("repo") }
                    }
                }
                developers { developer { id.set("marcus-carr"); name.set("Marcus Carr") } }
                scm { url.set(repositoryUrl); connection.set(repositoryUrl.map { "scm:git:$it.git" }) }
            }
        }
        val publicationUrl = providers.gradleProperty("publicationRepositoryUrl")
        tasks.matching { it.name.contains("MavenCentral", ignoreCase = true) }.configureEach {
            doFirst {
                val url = publicationUrl.orNull
                require(url != null && Regex("https://github.com/[A-Za-z0-9][A-Za-z0-9-]*/[A-Za-z0-9_.-]+").matches(url)) { "Set -PpublicationRepositoryUrl=https://github.com/owner/repository before publishing." }
            }
        }
    }
}

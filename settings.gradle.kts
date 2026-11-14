rootProject.name = "shenzhen-hanzi-animator"

pluginManagement {
    // AGP 9.0.1 embeds R8 9.0.32, which cannot read Kotlin 2.4 metadata.
    // Use Google's documented override; this stable patch exceeds the 9.1.29 minimum.
    buildscript {
        repositories { google(); mavenCentral() }
        dependencies { classpath("com.android.tools:r8:9.1.56") }
    }
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":library")
include(":sample")
include(":core", ":codec", ":json", ":data", ":bundled-data", ":material3", ":browser-engine", ":androidApp")

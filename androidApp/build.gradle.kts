plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}
android {
    namespace = "engineering.carr.hanzianimator.sample"
    compileSdk = 36
    defaultConfig { applicationId = "engineering.carr.hanzianimator.sample"; minSdk = 24; targetSdk = 36; versionCode = 1; versionName = providers.gradleProperty("libraryVersion").get() }
    buildFeatures { compose = true }
    androidResources { noCompress += "hzb" }
    buildTypes { release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt")); signingConfig = signingConfigs.getByName("debug") } }
}
dependencies {
    implementation(project(":sample"))
    implementation(project(":bundled-data"))
    implementation(libs.androidx.activity.compose)
}

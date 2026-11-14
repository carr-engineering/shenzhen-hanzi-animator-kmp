import org.jetbrains.kotlin.gradle.dsl.JsModuleKind
plugins { alias(libs.plugins.kotlinMultiplatform) }
kotlin {
    js {
        browser()
        nodejs()
        useEsModules()
        binaries.library()
        generateTypeScriptDefinitions()
    }
    sourceSets {
        commonMain.dependencies { api(project(":core")); api(project(":codec")) }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}

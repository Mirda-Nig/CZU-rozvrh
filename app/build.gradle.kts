import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Podpisový klíč pro release (soubor keystore.properties v kořeni projektu, viz README).
// Ukázková verze s vymyšleným rozvrhem na screenshoty: gradlew installDebug -Pdemo=true
val demo = (findProperty("demo") as String?) == "true"

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.example.czurozvrh"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.czurozvrh.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "DEMO", demo.toString())
    }

    signingConfigs {
        create("release") {
            if (!keystoreProps.isEmpty) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (demo) applicationIdSuffix = ".demo"
        }
        release {
            // Bez keystore.properties se release podepíše debug klíčem (jde nainstalovat, ale ne aktualizovat přes jiný podpis).
            signingConfig = if (!keystoreProps.isEmpty) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.okhttp)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.jsoup)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
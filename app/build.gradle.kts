plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release builds are signed with YOUR upload key (GitHub secrets); Google Play re-signs with the app signing key.
val releaseKeystore: String? = System.getenv("TIJORI_KEYSTORE_PATH")
// Every Play upload needs a higher versionCode: the GitHub run number. Bump appVersion for big releases.
val buildNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val appVersion = "2.0"

android {
    namespace = "com.tijoriapp.vault"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tijoriapp.vault"
        minSdk = 28 // StrongBox API, SaveCallback.onSuccess(IntentSender), modern Keystore
        targetSdk = 36
        versionCode = buildNumber
        versionName = "$appVersion.$buildNumber"
        buildConfigField("String", "SUPPORT_EMAIL", "\"tosourav6@gmail.com\"")
    }

    signingConfigs {
        create("release") {
            if (releaseKeystore != null) {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("TIJORI_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TIJORI_KEY_ALIAS")
                keyPassword = System.getenv("TIJORI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug" // install next to the Play version without touching its vault
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = if (releaseKeystore != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        htmlReport = true
        xmlReport = false
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        localeFilters += listOf("en", "hi")
    }
    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.kotlin_module", "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json", "/META-INF/*.version", "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            )
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.splashscreen)
    implementation(libs.fragment.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.biometric)
    implementation(libs.coroutines.android)
    implementation(libs.bouncycastle) // Argon2id only; R8 strips the rest

    testImplementation(libs.junit)
    testImplementation(libs.json) // real org.json for JVM tests (Android's copy is a stub there)
}

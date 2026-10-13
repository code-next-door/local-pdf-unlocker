plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.codenextdoor.pdfunlocker"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.codenextdoor.pdfunlocker"
        // Android 15: PdfRenderer can open password-protected PDFs and write an unlocked copy,
        // so the app needs no PDF library.
        minSdk = 35
        targetSdk = 37
        versionName = providers.gradleProperty("versionName").getOrElse("0.1.0")
        versionCode = providers.gradleProperty("versionCode").map(String::toInt).getOrElse(1)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The release key lives only in GitHub secrets (and the author's own backup), never in
    // the repo. CI passes it in through these environment variables; without them the
    // release APK is simply unsigned.
    val releaseKeystore = providers.environmentVariable("PDFUNLOCKER_KEYSTORE_FILE").orNull
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                // keytool's default (PKCS12) keystores use one password for the store and the key.
                storePassword = providers.environmentVariable("PDFUNLOCKER_KEYSTORE_PASSWORD").get()
                keyPassword = storePassword
                keyAlias = providers.environmentVariable("PDFUNLOCKER_KEY_ALIAS").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
        // Shrunk exactly like release, but signed with the debug key and installed as a
        // separate app, so the black-box tests in :releasetest can drive the R8 output.
        create("minified") {
            initWith(getByName("release"))
            applicationIdSuffix = ".minified"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        // Also check the test code (unit and device tests), not only the app.
        checkTestSources = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Declared so the app ships the same coroutines version the tests compile against
    // (otherwise AndroidX pulls an older one and device tests fail with NoSuchMethodError).
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Accessibility Test Framework checks (touch targets, contrast, labels) in UI tests.
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    // Security fixes for libraries it brings along (tests only, never in the app).
    // Dependabot flags the older versions; remove once it brings these itself.
    constraints {
        androidTestImplementation("org.jsoup:jsoup:1.23.2")
        androidTestImplementation("com.google.protobuf:protobuf-javalite:3.25.5")
        androidTestImplementation("com.google.guava:guava:33.4.0-android")
    }
    // Compose testing brings Espresso 3.5, which calls an input API removed in newer Android.
    androidTestImplementation(libs.androidx.test.espresso.core)
    // Stubs the system file pickers in UI tests.
    androidTestImplementation(libs.androidx.test.espresso.intents)
    // Types the PIN into the system's fingerprint/PIN prompt (another app's window) in UI tests.
    androidTestImplementation(libs.androidx.test.uiautomator)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

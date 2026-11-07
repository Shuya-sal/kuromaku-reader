plugins { id("com.android.application") }

android {
    namespace = "id.shuyasal.kuromaku"
    compileSdk = 35

    defaultConfig {
        applicationId = "id.shuyasal.kuromaku"
        minSdk = 26
        targetSdk = 35
        versionCode = 61
        versionName = "2.34.0"
    }

    // One checked-in debug keystore, used by both CI and local builds.
    //
    // Android refuses to install an APK whose signing certificate differs from the
    // installed one, and until 2.20.2 every CI run generated its own throwaway debug
    // keystore -- so each release appeared as a different publisher, "update" was
    // rejected, and the only way forward was uninstall, which deletes reading
    // progress, bookmarks and offline downloads. Committing a debug keystore is
    // exactly what it is for: it is not a secret and grants no publishing rights.
    signingConfigs {
        create("sharedDebug") {
            storeFile = file("../keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("sharedDebug")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "1024m"
            it.setForkEvery(25)
        }
    }

    packaging {
        resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module")
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("androidx.work:work-runtime:2.9.1")
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
}

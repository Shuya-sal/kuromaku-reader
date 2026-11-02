plugins { id("com.android.application") }

android {
    namespace = "id.shuyasal.kuromaku"
    compileSdk = 35
    defaultConfig {
        applicationId = "id.shuyasal.kuromaku"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

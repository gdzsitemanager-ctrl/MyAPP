plugins { id("com.android.application") }

android {
    namespace = "com.frontline.messenger"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.frontline.messenger"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

plugins { id("com.android.application") }

android {
    namespace = "com.dronewarfare.frontline"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.dronewarfare.frontline"
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
    lint { checkReleaseBuilds = false; abortOnError = false }
}

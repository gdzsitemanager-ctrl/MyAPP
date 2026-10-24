plugins { id("com.android.application") }

android {
    namespace = "com.webapk.template"
    compileSdk = 34
    defaultConfig {
        // Плейсхолдер: билдер подменяет его на уникальный пакет (той же длины)
        applicationId = "com.webapk.pkgplaceholder00"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { checkReleaseBuilds = false; abortOnError = false }
}

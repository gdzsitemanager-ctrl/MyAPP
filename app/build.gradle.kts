plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Шаблонный APK (модуль :template) собирается первым и кладётся в assets билдера
val copyTemplate by tasks.registering(Copy::class) {
    dependsOn(":template:assembleRelease")
    from(project(":template").layout.buildDirectory.file("outputs/apk/release/template-release-unsigned.apk"))
    into(layout.buildDirectory.dir("generated/template_assets"))
    rename { "template.apk" }
}
tasks.named("preBuild") { dependsOn(copyTemplate) }

android {
    namespace = "com.webapk.builder"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.webapk.builder"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += listOf("apk") }
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/template_assets"))
    packaging {
        resources { excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/DEPENDENCIES", "/META-INF/*.kotlin_module") }
    }
    lint { abortOnError = false }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.android.tools.build:apksig:8.5.2")
}

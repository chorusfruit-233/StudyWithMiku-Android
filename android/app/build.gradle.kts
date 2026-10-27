plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Credentials are supplied by the local environment or GitHub Actions secrets.
// Never embed a password or keystore in this project.
val signingEnvironment = listOf("MIKU_KEYSTORE_PATH", "MIKU_STORE_PASSWORD", "MIKU_KEY_ALIAS", "MIKU_KEY_PASSWORD")
    .associateWith { providers.environmentVariable(it).orNull }
val hasReleaseSigning = signingEnvironment.values.all { !it.isNullOrBlank() }
val checkReleaseSigning = tasks.register("checkReleaseSigning") {
    doLast {
        val missing = signingEnvironment.filterValues { it.isNullOrBlank() }.keys
        check(missing.isEmpty()) { "Release signing requires environment variables: ${missing.joinToString()}. See android/RELEASING.md." }
        check(rootProject.file(signingEnvironment.getValue("MIKU_KEYSTORE_PATH")!!).isFile) { "Release keystore file does not exist." }
    }
}

android {
    namespace = "io.github.chorusfruit233.studywithmiku"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "io.github.chorusfruit233.studywithmiku"
        minSdk = 26
        targetSdk = 37
        versionCode = 17
        versionName = "1.2.14"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    if (hasReleaseSigning) {
        signingConfigs.create("release") {
            storeFile = rootProject.file(signingEnvironment.getValue("MIKU_KEYSTORE_PATH")!!)
            storePassword = signingEnvironment.getValue("MIKU_STORE_PASSWORD")
            keyAlias = signingEnvironment.getValue("MIKU_KEY_ALIAS")
            keyPassword = signingEnvironment.getValue("MIKU_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            // Keep the first signed release behavior identical to the validated app.
            isMinifyEnabled = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    androidResources { noCompress += listOf("mp3", "mp4") }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
tasks.matching { it.name == "packageRelease" || it.name == "validateSigningRelease" }.configureEach {
    dependsOn(checkReleaseSigning)
}
dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    // Material Icons is no longer updated or included in the Compose BOM.
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.material3:material3:1.5.0-beta01")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-datasource:1.11.1")
    implementation("androidx.media3:media3-database:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-gif:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    // Compose's transitive Espresso is too old for Android 17's InputManager API.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:5.5.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.5.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

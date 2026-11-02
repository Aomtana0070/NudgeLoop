plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val uploadStoreFile = providers.environmentVariable("NUDGELOOP_UPLOAD_STORE_FILE").orNull
val uploadStorePassword = providers.environmentVariable("NUDGELOOP_UPLOAD_STORE_PASSWORD").orNull
val uploadKeyAlias = providers.environmentVariable("NUDGELOOP_UPLOAD_KEY_ALIAS").orNull
val uploadKeyPassword = providers.environmentVariable("NUDGELOOP_UPLOAD_KEY_PASSWORD").orNull
val uploadSigningValues = listOf(uploadStoreFile, uploadStorePassword, uploadKeyAlias, uploadKeyPassword)
val releaseSigningConfigured = uploadSigningValues.all { !it.isNullOrBlank() }

if (uploadSigningValues.any { !it.isNullOrBlank() } && !releaseSigningConfigured) {
    throw GradleException("Set all NUDGELOOP_UPLOAD_* environment variables to sign a release.")
}

val releaseOutputRequested = gradle.startParameter.taskNames.any {
    it.substringAfterLast(':') == "bundleRelease" || it.substringAfterLast(':') == "assembleRelease"
}
if (releaseOutputRequested && !releaseSigningConfigured) {
    throw GradleException("Release output must be signed. Set all NUDGELOOP_UPLOAD_* environment variables.")
}

android {
    namespace = "app.renotify"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.renotify"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "1.1.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    if (releaseSigningConfigured) {
        signingConfigs {
            create("release") {
                storeFile = file(uploadStoreFile!!)
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.3.0")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

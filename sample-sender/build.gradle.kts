plugins {
    id("com.android.application")
}

android {
    namespace = "app.renotify.testsender"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.renotify.testsender"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

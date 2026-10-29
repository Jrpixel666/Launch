plugins {
    id("com.android.application")
}

android {
    namespace = "app.pocketshell"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.pocketshell"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }
}

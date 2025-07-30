plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.mpoxfriend"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.mpoxfriend"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BASE_URL", "\"http://192.168.92.238:8001/\"")
        //buildConfigField("String", "BASE_URL", "\"http://17.17.17.226:8001/\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "BASE_URL", "\"http://192.168.92.238:8001/\"")
            //buildConfigField("String", "BASE_URL", "\"http://17.17.17.226:8001/\"")
        }
        debug {
            buildConfigField("String", "BASE_URL", "\"http://192.168.92.238:8001/\"")
            //buildConfigField("String", "BASE_URL", "\"http://17.17.17.226:8001/\"")}
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.okhttp) // Replace with the latest version
    implementation(libs.okhttp3.logging.interceptor) // Replace with the latest version
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}


buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.google.gms:google-services:4.4.1") // ✅ Required for the plugin
        classpath("com.android.tools.build:gradle:8.13.2")
        classpath(libs.google.services) // or latest version
    }
}


plugins {
    id("com.android.application")
    id("com.google.gms.google-services") version "4.4.4" apply false
    //id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt") // ← this is the missing piece
    //id("org.jetbrains.kotlin.android") version "2.0.0"

}

android {
    namespace = "com.tomer.parashi"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tomer.parashi"
        minSdk = 26 // Changed from 24 to 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    //implementation(platform("com.google.firebase:firebase-bom:34.2.0"))
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))

    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx") // ← includes FirebaseApp
    implementation ("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-functions-ktx")
    //implementation(libs.firebase.functions)
    //implementation(libs.firebase.auth.ktx) // ← This is the ke
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("com.kosherjava:zmanim:2.5.0")
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
apply(plugin = "com.google.gms.google-services")
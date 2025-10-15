// File: Quiz.kt
package com.example.quiz2 // ← match your app's package name

import android.app.Application
import com.google.firebase.FirebaseApp

//class MyApp : Application() {
//    override fun onCreate() {
//        super.onCreate()
//        FirebaseApp.initializeApp(this) // ← This is the magic line
//    }
//}

class Quiz : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this) // ✅ Initializes Firebase
    }
}
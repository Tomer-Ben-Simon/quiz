// File: Quiz.kt
package com.tomer.parashi

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
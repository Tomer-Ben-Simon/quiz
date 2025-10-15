// File: Quiz.kt
package com.example.quiz2 // ← match your app's package name

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import com.google.firebase.FirebaseApp


class Quiz : Application() {

    override fun onCreate() {
        super.onCreate()
//        if (isMainProcess(this)) {
//            FirebaseApp.initializeApp(this)
//        }
    }
    private fun isMainProcess(context: Context): Boolean {
        val pid = android.os.Process.myPid()
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processName = manager.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
        return processName == context.packageName
    }

}
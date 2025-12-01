package com.example.quiz2

import androidx.appcompat.app.AppCompatActivity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.UUID
import android.widget.EditText
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SplashActivity : AppCompatActivity(){

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            val currentUser = auth.currentUser

            if (currentUser != null) {
                val userId = currentUser.uid
                val userRef = db.collection("users").document(userId)

                userRef.get()
                    .addOnSuccessListener { document ->
                        if (document.exists() &&
                            !document.getString("name").isNullOrEmpty() &&
                            !document.getString("phone").isNullOrEmpty()) {
                            // Profile exists → go to MainActivity
                            startActivity(Intent(this, MainActivity::class.java))
                        } else {
                            // Profile missing → go to UserProfileActivity
                            startActivity(Intent(this, UserProfileActivity::class.java))
                        }
                        finish()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error loading profile", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, UserProfileActivity::class.java))
                        finish()
                    }
            } else {
                // No user signed in → sign in anonymously
                auth.signInAnonymously()
                    .addOnSuccessListener {
                        startActivity(Intent(this, UserProfileActivity::class.java))
                        finish()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Sign-in failed", Toast.LENGTH_SHORT).show()
                    }
            }
        }, 1500) // 1.5 second delay
    }
}
package com.example.quiz2

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SplashActivity : AppCompatActivity(){

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed(
        {
            if (!isNetworkAvailable()) {
                // No internet, go directly to MainActivity and load local questions
                Toast.makeText(this, "אין חיבור לאינטרנט. נטען שאלות מקומיות.", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                // Internet available, proceed with Firebase logic
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
                            }
//                            else {
//                                // Profile missing → go to UserProfileActivity
//                                startActivity(Intent(this, UserProfileActivity::class.java))
//                            }
                            finish()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "שגיאה בטעינת פרופיל. נסה שוב.", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, UserProfileActivity::class.java))
                            finish()
                        }
                } else {
                    // No user signed in → sign in anonymously
                    auth.signInAnonymously()
                        .addOnSuccessListener {
                            startActivity(Intent(this, MainActivity::class.java))
                            //startActivity(Intent(this, UserProfileActivity::class.java))
                            finish()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "התחברות אנונימית נכשלה.", Toast.LENGTH_SHORT).show()
                            // Fallback if anonymous sign-in fails even with internet (unlikely but possible)
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        }
                }
            }
        }, 4000) // 4 second delay
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (capabilities != null&&connectivityManager.isActiveNetworkMetered==true) {
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> return true
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> return true
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> return true
            }
        }
        return false
    }
}

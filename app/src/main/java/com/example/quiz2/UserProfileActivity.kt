package com.example.quiz2

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.UUID
import android.widget.EditText
import android.widget.Button
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


class UserProfileActivity : AppCompatActivity() {
    public var userId = ""
    private lateinit var nameInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var saveButton: Button
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        nameInput = findViewById(R.id.nameInput)
        phoneInput = findViewById(R.id.phoneInput)
        saveButton = findViewById(R.id.saveButton)

        saveButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val phone = phoneInput.text.toString().trim()

            if (name.isNotEmpty() && phone.isNotEmpty()) {

//                FirebaseAuth.getInstance()
//                    .signInAnonymously()
//                    .addOnSuccessListener { authResult ->
//                        val userId = authResult.user?.uid
//                        saveUserProfile(userId)
//                    }
//                    .addOnFailureListener {
//                        Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
//                    }
                userId = FirebaseAuth.getInstance().currentUser?.uid ?: UUID.randomUUID().toString()
                val userData = mapOf(
                    "name" to name,
                    "phone" to phone
                )
                db.collection("users")
                    .document(userId)
                    .set(userData)
                    .addOnSuccessListener {
                        Toast.makeText(this, "המשתמש נשמר בהצלחה", Toast.LENGTH_SHORT).show()
                        // ✅ Return to MainActivity
                        val intent = Intent(this, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        finish() // Optional: closes UserProfileActivity so it's removed from back stack
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "שגיאה בשמירת המשתמש", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "נא למלא את כל השדות", Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun saveUserProfile(userId: String?) {
        val name = nameInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()

        val userData = mapOf(
            "name" to name,
            "phone" to phone
        )

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId!!)
            .set(userData)
            .addOnSuccessListener {
                Toast.makeText(this, "User saved", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
    }
}

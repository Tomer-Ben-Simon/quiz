package com.example.quiz2

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.quiz2.MainActivity.Parasha
import com.example.quiz2.MainActivity.Root
import com.example.quiz2.MainActivity.Section
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlin.collections.forEach

class AdminActivity: AppCompatActivity() {
    private lateinit var setToFirebaseQuizButton: Button
    private lateinit var getFromFirebaseQuizButton: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_set_get_quiz_questions)


        setToFirebaseQuizButton = findViewById(R.id.setButton)
        getFromFirebaseQuizButton = findViewById(R.id.getButton)
        setToFirebaseQuizButton.setOnClickListener {
            try {
                val json = assets.open("quiz_week_1.json").bufferedReader().use { it.readText() }
                val root = Gson().fromJson(json, Root::class.java)

                if (root != null) {
                    val parashotList = parseParashot(root)
                    uploadToFirestore(parashotList)
                    Toast.makeText(this@AdminActivity, "נתוני החידון נשלחו בהצלחה ל-Firestore", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@AdminActivity, "שגיאה: קובץ ה-JSON ריק או לא תקין", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("AdminActivity", "Error reading JSON or uploading to Firestore", e)
                Toast.makeText(this@AdminActivity, "שגיאה בשליחת הנתונים: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        
        getFromFirebaseQuizButton.setOnClickListener {
            val db = FirebaseFirestore.getInstance()
            db.collection("parashot").get().addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Toast.makeText(this@AdminActivity, "אין פרשות ב-Firestore.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                for (document in result) {
                    val parashaName = document.id
                    // ה-sections הן תת-אוסף (sub-collection), לכן צריך להביא אותן בנפרד
                    document.reference.collection("sections").get().addOnSuccessListener { sectionsResult ->
                        if (sectionsResult.isEmpty) {
                            Log.d("AdminActivity", "אין sections עבור הפרשה $parashaName.")
                            return@addOnSuccessListener
                        }
                        for (sectionDoc in sectionsResult) {
                            val sectionName = sectionDoc.id
                            val questions = sectionDoc.get("questions")
                            Log.d("AdminActivity", "Parasha: $parashaName, Section: $sectionName, Questions: $questions")
                            // ניתן להציג את הנתונים ב-TextView או ב-Logcat
                        }
                        Toast.makeText(this@AdminActivity, "נתונים נקראו מ-Firestore ל-Logcat", Toast.LENGTH_SHORT).show()
                    }.addOnFailureListener { e ->
                        Log.e("AdminActivity", "Error fetching sections for $parashaName", e)
                        Toast.makeText(this@AdminActivity, "שגיאה בקריאת sections: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.addOnFailureListener { e ->
                Log.e("AdminActivity", "Error fetching parashot from Firestore", e)
                Toast.makeText(this@AdminActivity, "שגיאה בקריאת פרשות: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

    }

    fun parseParashot(root: Root): List<Parasha> {
        val result = mutableListOf<Parasha>()

        root.parashot.forEach { parashaMap: Map<String, List<Map<String, List<MainActivity.Question>>>> ->
            val parashaName = parashaMap.keys.first()
            val sectionsRaw = parashaMap[parashaName] ?: emptyList()

            val sections = sectionsRaw.map { sectionObj: Map<String, List<MainActivity.Question>> ->
                val sectionName = sectionObj.keys.first()
                val questions = sectionObj[sectionName] ?: emptyList()
                Section(sectionName, questions)
            }

            result.add(Parasha(parashaName, sections))
        }

        return result
    }

    fun uploadToFirestore(parashot: List<Parasha>) {
        val db = FirebaseFirestore.getInstance()

        parashot.forEach{ parasha ->
            val parashaDoc = db.collection("parashot").document(parasha.name)

            parasha.sections.forEach{ section ->
                val safeSectionName = section.name.replace("/", "_") 

                parashaDoc.collection("sections")
                    .document(safeSectionName)
                    .set(mapOf("questions" to section.questions))
            }
        }
    }
}

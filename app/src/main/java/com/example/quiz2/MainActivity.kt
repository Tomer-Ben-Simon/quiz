package com.example.quiz2

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.util.Random
import java.util.UUID

class MainActivity : AppCompatActivity() {

    // מחלקות נתונים הועברו לקובץ QuizDataModels.kt (או שהוגדרו כאן אם הועתקו חזרה)
    // נשאיר אותן כאן לשלב זה כדי לא ליצור קבצים חדשים אלא לתקן את הקיים
    data class Question(
        val id: Int = 0,
        val questionNumPerDay: Int = 0,
        val question: String = "",
        val options: List<String> = emptyList(),
        val answer: Int = 0,
        var userAnswer: Int = -1,
        val day: String = ""
    )

    data class Section(
        val name: String,
        val questions: List<Question>
    )

    data class Parasha(
        val name: String,
        val sections: List<Section>
    )

    data class Root(
        val parashot: List<Map<String, List<Map<String, List<Question>>>>>
    )

    // מחלקת נתונים עבור פרופיל משתמש ב-Firestore
    data class UserProfile(
        val name: String = "",
        val phone: String = "",
        val firebaseAuthUids: MutableList<String> = mutableListOf(), // רשימת UID מכל המכשירים
        val lastUpdated: FieldValue = FieldValue.serverTimestamp() // Timestamp לעדכון אחרון
        // ה-document ID ב-Firestore ישמש כמזהה הראשי של הפרופיל (currentPersonProfileId)
    )

    data class ParashaQuiz(
        val hebrewName: String = "",
        val questions: List<Question> = emptyList()
    )

    private lateinit var functions: FirebaseFunctions
    private lateinit var varAuth: FirebaseAuth // Changed from `auth` to `varAuth` to avoid conflict with other `auth` in `MainActivity`
    private lateinit var db: FirebaseFirestore

    private lateinit var tvParasha: TextView
    private lateinit var weekDay: TextView
    private lateinit var tvQuestion: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvFeedback: TextView
    private lateinit var radioGroup: RadioGroup
    private lateinit var option1: RadioButton
    private lateinit var option2: RadioButton
    private lateinit var option3: RadioButton
    private lateinit var option4: RadioButton
    private lateinit var btnAction: Button
    private lateinit var currentQ: Question
    private lateinit var spinnerParasha: Spinner
    private lateinit var btnPrevious: Button
    private lateinit var btnNext: Button
    private lateinit var reviewNavigation: LinearLayout

    private var lastIndex = 0
    private var currentQuestionIndex = 0
    private var currentQuizScore = 0
    private var answered = false
    private var inReviewMode = false
    private var isUserProfileComplete = false
    private var currentPersonProfileId: String? = null // המזהה היציב של האדם (Document ID באוסף users)

    var parash = ""
    private var questions = mutableListOf<Question>()
    val hebrewDate = JewishDate()
    val dayOfWeek = hebrewDate.dayOfWeek

    var hebrewDayName = when (dayOfWeek) {
        1 -> "ראשון"
        2 -> "שני"
        3 -> "שלישי"
        4 -> "רביעי"
        5 -> "חמישי"
        6 -> "שישי"
        7 -> "שביעי"
        else -> "לא ידוע"
    }

    var dayName = when (hebrewDayName) {
        "ראשון" -> "rishon"
        "שני" -> "sheni"
        "שלישי" -> "shlishi"
        "רביעי" -> "reviee"
        "חמישי" -> "hamishi"
        "שישי" -> "shishi"
        "שבת" -> "shabat"
        else -> "לא ידוע"
    }

    val parashots = listOf(
        "בראשית","נח","לך לך","וירא","חיי שרה","תולדות","ויצא","וישלח","וישב","חנוכה","מקץ","ויגש","ויחי",
        "שמות","וארא","בא","בשלח","יתרו","משפטים","תרומה","תצוה","כי תשא","ויקהל","ויקהל-פקודי","פקודי",
        "ויקרא","צו","שמיני","תזריע","תזריע-מצורע","מצורע","אחרי מות","אחרי מות-קדושים","קדושים","אמור","בהר","בהר־בחקתי","בחקתי",
        "במדבר","נשא","בהעלותך","שלח־לך","קורח","חוקת","בלק","פינחס","מטות","מטות־מסעי","מסעי",
        "דברים","ואתחנן","עקב","ראה","שופטים","כי תצא","כי תבוא","נצבים","נצבים-וילך","וילך","האזינו","וזאת הברכה"
    )

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.action_user)?.isVisible = !isUserProfileComplete
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
//            R.id.action_user -> {
//                val intent = Intent(this, UserProfileActivity::class.java)
//                startActivity(intent)
//                true
//            }
            R.id.admin -> {
                val intent = Intent(this, AdminActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_show_results -> {
                showListOfParticipants()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        varAuth = FirebaseAuth.getInstance()
        functions = FirebaseFunctions.getInstance()
        db = FirebaseFirestore.getInstance()

        checkUserProfileStatus()

        tvQuestion = findViewById(R.id.tvQuestion)
        tvProgress = findViewById(R.id.tvProgress)
        tvFeedback = findViewById(R.id.tvFeedback)
        radioGroup = findViewById(R.id.radioGroup)
        option1 = findViewById(R.id.option1)
        option2 = findViewById(R.id.option2)
        option3 = findViewById(R.id.option3)
        option4 = findViewById(R.id.option4)
        btnAction = findViewById(R.id.btnAction)
        spinnerParasha = findViewById(R.id.spinnerParasha)
        tvParasha = findViewById(R.id.Parash)
        weekDay = findViewById(R.id.weekDay)

        btnPrevious = findViewById(R.id.btnPrevious)
        btnNext = findViewById(R.id.btnNext)
        reviewNavigation = findViewById(R.id.reviewNavigation)

        val weekdays = listOf("ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת")
        val spinnerWeekdays = findViewById<Spinner>(R.id.spinnerWeekdays)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, weekdays)
        spinnerWeekdays.adapter = adapter
        val todayIndex = (dayOfWeek - 1) % 7
        spinnerWeekdays.setSelection(todayIndex)
        // 1. Initially populate the parasha spinner with all local parashot
        setupParashotSpinner(null)
        fetchParasha { hebrewParasha, _ ->
            val parashaIndex = findParashaIndex(hebrewParasha)
            if (parashaIndex != -1) {
                parash = parashots[parashaIndex]
                setupParashotSpinner(parash)

//                val currentSpinnerAdapter = spinnerParasha.adapter as? ArrayAdapter<String>
//                val foundIndex = currentSpinnerAdapter?.getPosition(parash) ?: -1
//                if (foundIndex != -1) {
//                    spinnerParasha.setSelection(foundIndex)
//                    tvParasha.text = parash // Update TextView as well
//                }
//            }
//            else {
//                Log.w("MainActivity", "Current parasha from Hebcal not found in local list: $hebrewParasha")
            }
            else{
                setupParashotSpinner("בראשית")
            }
        }

        spinnerParasha.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                parash = parent.getItemAtPosition(position).toString()
                tvParasha.text = parash
                updateDayDisplay(weekDay.text.toString()) // Use current selected day
                resetQuizForDay(parash, dayName)
                tvProgress.visibility = View.GONE
                tvFeedback.visibility = View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        spinnerWeekdays.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedDay = weekdays[position]
                updateDayDisplay(selectedDay)
                dayName = when (selectedDay) {
                    "ראשון" -> "rishon"
                    "שני" -> "sheni"
                    "שלישי" -> "shlishi"
                    "רביעי" -> "reviee"
                    "חמישי" -> "hamishi"
                    "שישי" -> "shishi"
                    "שבת" -> "shabat"
                    else -> "לא ידוע"
                }
                resetQuizForDay(parash, dayName)
                tvProgress.visibility = View.GONE
                tvFeedback.visibility = View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        btnAction.setOnClickListener {
            if (btnAction.text == "התחל") {
                tvQuestion.visibility = View.VISIBLE
                radioGroup.visibility = View.VISIBLE
                loadQuestionsAndStartQuiz()
            } else {
                if (!answered) {
                    val selectedId = radioGroup.checkedRadioButtonId
                    if (selectedId == -1) {
                        Toast.makeText(this, "בבקשה בחר את התשובה", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val selectedIndex = when (selectedId) {
                        R.id.option1 -> 0
                        R.id.option2 -> 1
                        R.id.option3 -> 2
                        R.id.option4 -> 3
                        else -> -1
                    }
                    checkAnswer(selectedIndex)
                } else {
                    nextQuestion()
                }
            }
        }

        btnPrevious.setOnClickListener {
            if (inReviewMode && currentQuestionIndex > 0) {
                currentQuestionIndex--
                renderQuestionForReview()
            }
        }

        btnNext.setOnClickListener {
            if (inReviewMode && currentQuestionIndex < lastIndex) {
                currentQuestionIndex++
                renderQuestionForReview()
            }
        }
    }

    private fun checkUserProfileStatus() {
        val user = varAuth.currentUser // Used `varAuth`
        if (user != null) {
            val currentFirebaseUid = user.uid
            // חפש פרופיל משתמש המכיל את ה-UID הנוכחי
            db.collection("users")
                .whereArrayContains("firebaseAuthUids", currentFirebaseUid)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    if (!querySnapshot.isEmpty) {
                        // נמצא פרופיל קיים המקושר ל-UID הזה
                        val userProfileDoc = querySnapshot.documents.first()
                        currentPersonProfileId = userProfileDoc.id // שמור את המזהה היציב של הפרופיל
                        isUserProfileComplete = true
                    } else {
                        // ה-UID הנוכחי לא מקושר לאף פרופיל קיים
                        isUserProfileComplete = false
                    }
                    invalidateOptionsMenu() // עדכון התפריט
                }
                .addOnFailureListener { e ->
                    Log.e("MainActivity", "Error checking user profile status: ${e.message}", e)
                    isUserProfileComplete = false
                    invalidateOptionsMenu()
                }
        }
    }

    private fun updateDayDisplay(day: String) {
        val currentDayString = day
        var hanukkahDay=-1
        val dayMonth=hebrewDate.jewishMonth
        if (parash == "חנוכה") {
            if (hebrewDate.jewishMonth==8 && hebrewDate.jewishDayOfMonth>24){
                hanukkahDay = hebrewDate.jewishDayOfMonth-24
            }
            if (hebrewDate.jewishMonth==9 && hebrewDate.isCheshvanLong && hebrewDate.jewishDayOfMonth<3){
                hanukkahDay = hebrewDate.jewishDayOfMonth+6
            }
            else if (hebrewDate.jewishMonth==9 && !hebrewDate.isCheshvanLong && hebrewDate.jewishDayOfMonth<4){
                hanukkahDay = hebrewDate.jewishDayOfMonth+5
            }
            if (hanukkahDay != -1) {
                weekDay.text = "נר $hanukkahDay"
            } else {
                weekDay.text = currentDayString
            }
        } else {
            weekDay.text = currentDayString
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadQuestionsAndStartQuiz() {
        questions.clear()
        fetchQuestionsFromFirestore(parash, dayName) { firestoreQuestions ->
            if (firestoreQuestions.isNotEmpty()) {
                questions.addAll(firestoreQuestions)
                renderQuestion()
            } else {
                val localQuestions = getQuestionsFromLocalJson(applicationContext, parash, dayName)
                if (localQuestions.isNotEmpty()) {
                    questions.addAll(localQuestions)
                    renderQuestion()
                } else {
                    Toast.makeText(this, "אין שאלות זמינות להיום.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchQuestionsFromFirestore(parasha: String, day: String, callback: (List<Question>) -> Unit) {
        db.collection("parashot").document(parasha)
            .collection("sections")
            .document(day)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val questionsData = document.get("questions") as? List<Map<String, Any>>
                    if (questionsData != null) {
                        val parsedQuestions = questionsData.mapNotNull { map ->
                            try {
                                Question(
                                    id = (map["id"] as? Number)?.toInt() ?: 0,
                                    questionNumPerDay = (map["questionNumPerDay"] as? Number)?.toInt() ?: 0,
                                    question = map["question"] as? String ?: "",
                                    options = map["options"] as? List<String> ?: emptyList(),
                                    answer = (map["answer"] as? Number)?.toInt() ?: 0,
                                    day = map["day"] as? String ?: day
                                )
                            } catch (e: Exception) { null }
                        }
                        callback(parsedQuestions.shuffled().take(10))
                    } else { callback(emptyList()) }
                } else { callback(emptyList()) }
            }
            .addOnFailureListener { callback(emptyList()) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    //private fun setupParashotSpinner(currentParashaKey: String) {
    private fun setupParashotSpinner(currentParashaKey: String?) { // Made currentParashaKey nullable
//        val allParashot = parashots
//        val spinnerList = mutableListOf<String>()
//        // Always add all parashot initially from your local list
//        spinnerList.addAll(allParashot)
//        // Optionally, reorder it so current/found parasha is first
//        // For simplicity, we'll just add all then set selection
//        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, spinnerList)
//        spinnerParasha.adapter = adapter
//        if (currentParashaKey != null) {
//            val currentParashaIndex = allParashot.indexOfFirst { it.equals(currentParashaKey, ignoreCase = true) }
//            if (currentParashaIndex != -1) {
//                // Set selection only if a valid currentParashaKey was provided and found
//                spinnerParasha.setSelection(currentParashaIndex)
//            } else {
//                spinnerParasha.setSelection(0) // Default to first if not found (e.g., new parasha not in hardcoded list)
//            }
//        }
//        else
//        {
//            spinnerParasha.setSelection(0) // Default to the first item if no currentParashaKey is given
//        }

        val allParashot = parashots
        val currentParashaIndex = allParashot.indexOfFirst { it.equals(currentParashaKey, ignoreCase = true) }
        val spinnerList = mutableListOf<String>()

        if (currentParashaIndex != -1) {
            spinnerList.add(allParashot[currentParashaIndex])
            for (i in (currentParashaIndex - 1) downTo 0) {
                spinnerList.add(allParashot[i])
            }
        } else {

            spinnerList.addAll(allParashot)
            spinnerParasha.setSelection(0)
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, spinnerList)
        spinnerParasha.adapter = adapter
        spinnerParasha.setSelection(0)
    }

    fun findParashaIndex(name: String): Int {
        val nameWithoutPrefix = name.replace("פרשת ", "").trim()
        return parashots.indexOfFirst { it.equals(nameWithoutPrefix, ignoreCase = true) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun resetQuizForDay(parasha: String, day: String) {
        currentQuestionIndex = 0
        currentQuizScore = 0
        answered = false
        inReviewMode = false
        btnAction.visibility = View.VISIBLE
        reviewNavigation.visibility = View.GONE
        btnAction.text = "התחל"
        tvQuestion.visibility = View.GONE
        radioGroup.visibility = View.GONE

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val lastPlayedDate = prefs.getString("last_played_${parasha}_$day", null)
        val currentDate = LocalDate.now().toString()

        if (lastPlayedDate == currentDate) {
            btnAction.isEnabled = false
            tvFeedback.text = "כבר השלמת את החידון להיום"
        } else {
            btnAction.isEnabled = true
            tvFeedback.text = ""
            questions.clear()
        }
    }

    @SuppressLint("UseKtx")
    @RequiresApi(Build.VERSION_CODES.O)
    private fun saveLastPlayedDate(parasha: String, day: String) {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val editor = prefs.edit()
        val currentDate = LocalDate.now().toString()
        editor.putString("last_played_${parasha}_$day", currentDate)
        editor.apply()
    }

    fun fetchParasha(callback: (String, Any?) -> Unit) {
        val client = OkHttpClient()
        val request = okhttp3.Request.Builder()
            .url("https://www.hebcal.com/shabbat?cfg=json&geonameid=293397&m=50")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Parasha", "Failed to fetch", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { json ->
                    try {
                        val jsonObject = JSONObject(json)
                        val items = jsonObject.getJSONArray("items")
                        for (i in 0 until items.length()) {
                            val item = items.getJSONObject(i)
                            if (item.getString("category") == "parashat") {
                                val hebrew = item.getString("hebrew")
                                val parashaEnglish = item.getString("title")
                                runOnUiThread { callback(hebrew, parashaEnglish) }
                                break
                            }
                        }
                    } catch (e: Exception) { Log.e("Parasha", "JSON Parse error", e) }
                }
            }
        })
    }

    private fun getQuestionsFromLocalJson(context: Context, parasha: String, day: String): List<Question> {
        val jsonString = context.assets.open("quiz_week_1.json").bufferedReader().use { it.readText() }
        val root = JSONObject(jsonString)
        val parashotArray = root.getJSONArray("parashot")
        val questionsForDay = mutableListOf<Question>()

        val dayNameToKeys = mapOf(
            "rishon" to listOf("rishon", "ראשון"),
            "sheni" to listOf("sheni", "שני"),
            "shlishi" to listOf("shlishi", "שלישי"),
            "reviee" to listOf("reviee", "רביעי"),
            "hamishi" to listOf("hamishi", "חמישי"),
            "shishi" to listOf("shishi", "שישי"),
            "shabat" to listOf("shabat", "שבת")
        )
        val possibleKeys = dayNameToKeys[day] ?: listOf(day)

        for (i in 0 until parashotArray.length()) {
            val parashaObj = parashotArray.getJSONObject(i)
            if (parashaObj.has(parasha)) {
                val daysArray = parashaObj.getJSONArray(parasha)
                for (j in 0 until daysArray.length()) {
                    val dayObj = daysArray.getJSONObject(j)
                    val dayKey = dayObj.keys().next()
                    if (possibleKeys.contains(dayKey)) {
                        val qArray = dayObj.getJSONArray(dayKey)
                        for (k in 0 until qArray.length()) {
                            val q = qArray.getJSONObject(k)
                            questionsForDay.add(Question(
                                id = q.getInt("id"),
                                questionNumPerDay = q.getInt("question_num_per_day"),
                                question = q.getString("question"),
                                options = q.getJSONArray("options").let { arr -> List(arr.length()) { arr.getString(it) } },
                                answer = q.getInt("answer")
                            ))
                        }
                        break
                    }
                }
                break
            }
        }
        return questionsForDay.shuffled().take(10)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun renderQuestion() {
        if (questions.isEmpty()) {
            btnAction.text = "התחל"
            tvQuestion.visibility = View.GONE
            radioGroup.visibility = View.GONE
            return
        }

        if (currentQuestionIndex < questions.size) {
            currentQ = questions[currentQuestionIndex]
            lastIndex = questions.size - 1
            tvQuestion.text = currentQ.question
            tvQuestion.visibility = View.VISIBLE
            radioGroup.visibility = View.VISIBLE
            tvProgress.visibility = View.VISIBLE
            tvFeedback.visibility = View.VISIBLE

            option1.visibility = View.VISIBLE
            option2.visibility = View.VISIBLE
            option3.visibility = View.VISIBLE
            option4.visibility = View.VISIBLE

            tvProgress.text = "שאלה ${currentQuestionIndex + 1} מתוך ${questions.size}"
            tvFeedback.text = ""
            radioGroup.clearCheck()

            option1.text = currentQ.options.getOrNull(0) ?: ""
            option2.text = currentQ.options.getOrNull(1) ?: ""
            option3.text = currentQ.options.getOrNull(2) ?: ""
            option4.text = currentQ.options.getOrNull(3) ?: ""

            enableOptions(true)
            btnAction.text = "שלח"
            answered = false
        }
    }

    private fun renderQuestionForReview() {
        currentQ = questions[currentQuestionIndex]
        tvQuestion.text = currentQ.question
        tvProgress.text = "שאלה ${currentQuestionIndex + 1} מתוך ${questions.size}"

        option1.visibility = View.VISIBLE
        option2.visibility = View.VISIBLE
        option3.visibility = View.VISIBLE
        option4.visibility = View.VISIBLE

        option1.text = currentQ.options.getOrNull(0) ?: ""
        option2.text = currentQ.options.getOrNull(1) ?: ""
        option3.text = currentQ.options.getOrNull(2) ?: ""
        option4.text = currentQ.options.getOrNull(3) ?: ""

        enableOptions(false)
        if (currentQ.userAnswer != -1) {
            (radioGroup.getChildAt(currentQ.userAnswer) as RadioButton).isChecked = true
        }

        val correctIndex = currentQ.answer
        if (currentQ.userAnswer == correctIndex) {
            tvFeedback.text = "נכון ✅"
        } else {
            tvFeedback.text = "טעות ❌ (התשובה: ${currentQ.options.getOrNull(correctIndex) ?: ""})"
        }
    }

    private fun enableOptions(enable: Boolean) {
        for (i in 0 until radioGroup.childCount) {
            radioGroup.getChildAt(i).isEnabled = enable
        }
    }

    private fun checkAnswer(selectedIndex: Int) {
        currentQ.userAnswer = selectedIndex
        answered = true
        enableOptions(false)
        if (selectedIndex == currentQ.answer) {
            currentQuizScore++
            tvFeedback.text = "נכון ✅"
        } else {
            tvFeedback.text = "טעות ❌ (התשובה: ${currentQ.options.getOrNull(currentQ.answer) ?: ""})"
        }
        btnAction.text = if (currentQuestionIndex == lastIndex) "צפה בתוצאות" else "הבא"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun nextQuestion() {
        if (currentQuestionIndex < lastIndex) {
            currentQuestionIndex++
            renderQuestion()
        } else {
            saveLastPlayedDate(parash, dayName)
            promptToSaveForLeaderboard()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun promptToSaveForLeaderboard() {
        val finalScore = if (questions.isNotEmpty()) (currentQuizScore.toDouble() / questions.size) * 100 else 0.0
        val message = "החידון הסתיים! ציון: ${currentQuizScore}/${questions.size} (${finalScore.toInt()}%).\n" +
                "האם תרצה לשמור את תוצאתך לרשימת המצטיינים ולהגרלה השבועית?"

        AlertDialog.Builder(this)
            .setTitle("תוצאות החידון")
            .setMessage(message)
            .setPositiveButton("כן, שמור תוצאה") { dialog, _ ->
                checkAndSaveUserDataThenPerformSaveQuizResult()
                dialog.dismiss()
            }
            .setNegativeButton("לא, תודה") { dialog, _ ->
                dialog.dismiss()
                showQuizReviewOrCloseDialog()
            }
            .setCancelable(false)
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun checkAndSaveUserDataThenPerformSaveQuizResult() {
        val currentFirebaseUid = varAuth.currentUser?.uid
        if (currentFirebaseUid == null) {
            Toast.makeText(this, "שגיאה: משתמש לא מחובר. לא ניתן לשמור נתונים.", Toast.LENGTH_SHORT).show()
            showQuizReviewOrCloseDialog()
            return
        }

        // 1. נסה למצוא פרופיל משתמש המכיל את ה-UID הנוכחי
        db.collection("users")
            .whereArrayContains("firebaseAuthUids", currentFirebaseUid)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    // 1a. נמצא פרופיל קיים המקושר ל-UID הזה
                    val userProfileDoc = querySnapshot.documents.first()
                    currentPersonProfileId = userProfileDoc.id // שמור את המזהה היציב של הפרופיל
                    isUserProfileComplete = true
                    invalidateOptionsMenu()
                    performSaveQuizResult() // המשך לשמירת תוצאה
                } else {
                    // 1b. ה-UID הנוכחי לא מקושר לאף פרופיל קיים - בקש פרטים
                    isUserProfileComplete = false
                    invalidateOptionsMenu()
                    promptForUserNameAndPhone()
                }
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Error checking user profile status: ${e.message}. Falling back to prompt.", e)
                isUserProfileComplete = false
                invalidateOptionsMenu()
                promptForUserNameAndPhone() // במקרה של כשל, נבקש פרטים בכל זאת
            }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun promptForUserNameAndPhone() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("פרטי משתמש להגרלה")
        val view = layoutInflater.inflate(R.layout.dialog_user_details, null)
        val etName = view.findViewById<EditText>(R.id.etUserName)
        val etPhone = view.findViewById<EditText>(R.id.etUserPhone)
        builder.setView(view)

        builder.setPositiveButton("שמור והמשך", null)
        builder.setNegativeButton("ביטול") { dialog, _ ->
            Toast.makeText(this, "פרטיך לא נשמרו. לא ניתן לשמור את תוצאת החידון להגרלה.", Toast.LENGTH_LONG).show()
            dialog.dismiss()
            showQuizReviewOrCloseDialog()
        }

        val dialog = builder.create()
        dialog.setCancelable(false)
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()

            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "שם מלא ומספר טלפון נדרשים!", Toast.LENGTH_SHORT).show()
            } else {
                val currentFirebaseUid = varAuth.currentUser?.uid
                // 2. בדוק אם קיים פרופיל אחר עם אותו שם וטלפון
                db.collection("users")
                    .whereEqualTo("name", name)
                    .whereEqualTo("phone", phone)
                    .get()
                    .addOnSuccessListener { querySnapshot ->
                        if (!querySnapshot.isEmpty) {
                            // נמצאו פרופילים עם שם וטלפון זהים
                            val existingDoc = querySnapshot.documents.first()
                            val existingProfileId = existingDoc.id
                            val existingUids = existingDoc.get("firebaseAuthUids") as? MutableList<String> ?: mutableListOf()

                            if (currentFirebaseUid != null && !existingUids.contains(currentFirebaseUid)) {
                                // משתמש זה (UID חדש) מנסה להשתמש בפרטים של משתמש קיים
                                // במקרה זה, נציג הודעת שגיאה ולא נשמור את תוצאת החידון הנוכחית לאותו פרופיל קיים.
                                Toast.makeText(this,
                                    "משתמש זה כבר קיים וכבר נבחן ביום זה. מה שנשמר במסד נתונים זה המבחן הראשון. תודה",
                                    Toast.LENGTH_LONG).show()
                                dialog.dismiss()
                                showQuizReviewOrCloseDialog() // סיים את התהליך בלי שמירה
                            } else if (currentFirebaseUid != null && existingUids.contains(currentFirebaseUid)) {
                                // זה אותו משתמש, רק מעדכן פרטים (או מנסה שוב באותו מכשיר)
                                currentPersonProfileId = existingProfileId // וודא שה-ID היציב נכון
                                isUserProfileComplete = true
                                invalidateOptionsMenu()
                                saveUserData(name, phone) // עדכן את השם/טלפון בפרופיל הקיים
                                dialog.dismiss()
                            }
                            else {
                                // מצב בלתי צפוי או UID null, נחזור לדיאלוג סיום
                                Toast.makeText(this, "שגיאה פנימית בזיהוי משתמש.", Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                                showQuizReviewOrCloseDialog()
                            }
                        } else {
                            // 3. לא נמצא פרופיל עם שם וטלפון זהים - צור פרופיל חדש
                            saveNewUserProfile(name, phone, currentFirebaseUid)
                            dialog.dismiss()
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "שגיאה בבדיקת כפילות: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e("MainActivity", "Error checking for duplicate user: ${e.message}", e)
                        dialog.dismiss()
                        showQuizReviewOrCloseDialog()
                    }
            }
        }
    }

    private fun saveNewUserProfile(name: String, phone: String, firebaseUid: String?) {
        if (firebaseUid == null) {
            Toast.makeText(this, "שגיאה: משתמש לא מחובר. לא ניתן ליצור פרופיל חדש.", Toast.LENGTH_SHORT).show()
            showQuizReviewOrCloseDialog()
            return
        }

        val newProfileId = UUID.randomUUID().toString() // מזהה ייחודי חדש לפרופיל האדם
        val newUserProfile = UserProfile(
            name = name,
            phone = phone,
            firebaseAuthUids = mutableListOf(firebaseUid) // קשר את ה-UID הנוכחי
        )

        db.collection("users").document(newProfileId).set(newUserProfile)
            .addOnSuccessListener {
                currentPersonProfileId = newProfileId // שמור את המזהה היציב שנוצר
                isUserProfileComplete = true
                invalidateOptionsMenu()
                Toast.makeText(this, "פרטיך נשמרו בהצלחה כמשתמש חדש!", Toast.LENGTH_SHORT).show()
                performSaveQuizResult()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "שגיאה בשמירת פרופיל חדש: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("MainActivity", "Error saving new user profile: ${e.message}", e)
                showQuizReviewOrCloseDialog()
            }
    }

    private fun saveUserData(name: String, phone: String) {
        val personProfileId = currentPersonProfileId
        if (personProfileId == null) {
            // זה לא אמור לקרות אם זרימת הקוד נכונה (הוא נקרא רק אחרי שהוגדר currentPersonProfileId)
            Toast.makeText(this, "שגיאה פנימית: מזהה פרופיל משתמש לא נמצא.", Toast.LENGTH_SHORT).show()
            showQuizReviewOrCloseDialog()
            return
        }

        val userData = hashMapOf(
            "name" to name,
            "phone" to phone,
            "lastUpdated" to FieldValue.serverTimestamp()
        )
        // עדכן את הפרופיל הקיים (ש-currentPersonProfileId מצביע עליו)
        db.collection("users").document(personProfileId).set(userData, SetOptions.merge())
            .addOnSuccessListener {
                isUserProfileComplete = true
                invalidateOptionsMenu()
                Toast.makeText(this, "פרטיך עודכנו בהצלחה!", Toast.LENGTH_SHORT).show()
                performSaveQuizResult()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "שגיאה בשמירת פרטי משתמש: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("MainActivity", "Error saving user data: ${e.message}", e)
                showQuizReviewOrCloseDialog()
            }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun performSaveQuizResult() {
        val currentFirebaseUid = varAuth.currentUser?.uid
        val personProfileId = currentPersonProfileId // המזהה היציב של האדם

        if (currentFirebaseUid == null || personProfileId == null) {
            Log.e("MainActivity", "User UID or PersonProfileId is null. Cannot save quiz result.")
            Toast.makeText(this, "שגיאה: משתמש לא מחובר או פרופיל לא מזוהה. לא נשמרו נתונים.", Toast.LENGTH_SHORT).show()
            showQuizReviewOrCloseDialog()
            return
        }

        // קבל את השם מהפרופיל היציב של המשתמש, לא מה-Firebase Auth
        db.collection("users").document(personProfileId).get().addOnSuccessListener { userProfileDoc ->
            val userName = userProfileDoc.getString("name") ?: "אלמוני"
            val resultData = hashMapOf(
                "userId" to personProfileId, // השתמש במזהה היציב של הפרופיל
                "firebaseAuthUid" to currentFirebaseUid, // שמור גם את ה-UID של המכשיר הספציפי
                "name" to userName,
                "parasha" to parash,
                "day" to dayName,
                "score" to (currentQuizScore.toDouble() / questions.size) * 100,
                "correctAnswers" to currentQuizScore,
                "totalQuestions" to questions.size,
                "date" to LocalDate.now().toString(),
                "completionTime" to FieldValue.serverTimestamp()
            )
            db.collection("quizResults").add(resultData).addOnSuccessListener {
                addMessage("$userName scored ${currentQuizScore} on $parash")
                showQuizReviewOrCloseDialog()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "שגיאה בשמירת תוצאת החידון: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("MainActivity", "Error saving quiz result: ${e.message}", e)
                showQuizReviewOrCloseDialog()
            }
        }
        .addOnFailureListener { e ->
            Toast.makeText(this, "שגיאה באחזור פרטי משתמש לשמירת תוצאה: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("MainActivity", "Error fetching user name for quiz result: ${e.message}", e)
            showQuizReviewOrCloseDialog()
        }
    }

    private fun addMessage(text: String): Task<String> {
        val data = hashMapOf("text" to text, "push" to true)
        return functions.getHttpsCallable("addMessage").call(data).continueWith { task ->
            task.result?.data as? String ?: ""
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showQuizReviewOrCloseDialog() {
        AlertDialog.Builder(this)
            .setTitle("החידון הסתיים")
            .setMessage("הציון שלך: $currentQuizScore מתוך ${questions.size}")
            .setPositiveButton("תודה") { _, _ -> showListOfParticipants() }
            .setNegativeButton("עבור על התשובות") { _, _ ->
                inReviewMode = true
                btnAction.visibility = View.GONE
                reviewNavigation.visibility = View.VISIBLE
                currentQuestionIndex = 0
                renderQuestionForReview()
            }
            .show()
    }

    private fun showListOfParticipants() {
        db.collection("quizResults").whereEqualTo("parasha", parash).get()
            .addOnSuccessListener { result ->
                val userScores = mutableMapOf<String, MutableMap<String, Double>>()
                val userNames = mutableMapOf<String, String>()
                for (doc in result.documents) {
                    val uid = doc.getString("userId") ?: continue // עכשיו זה ה-personProfileId
                    val d = doc.getString("day") ?: continue
                    val s = doc.getDouble("score") ?: 0.0
                    userNames[uid] = doc.getString("name") ?: "אלמוני"
                    userScores.getOrPut(uid) { mutableMapOf() }[d] = s
                }
                val leaderboard = userScores.map { (uid, scores) ->
                    Triple(userNames[uid]!!, scores[dayName] ?: 0.0, scores.values.sum())
                }.sortedByDescending { it.third }.take(10)

                val list = leaderboard.mapIndexed { i, (n, ds, ts) ->
                    "${i + 1}. $n - יומי: ${ds.toInt()}%, כולל: ${ts.toInt()}%"
                }.joinToString("\n")

                AlertDialog.Builder(this)
                    .setTitle("מובילים בפרשת $parash")
                    .setMessage(if (list.isEmpty()) "אין עדיין תוצאות" else list)
                    .setPositiveButton("סגור", null)
                    .show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "שגיאה באחזור רשימת המצטיינים: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("MainActivity", "Error fetching leaderboard: ${e.message}", e)
            }
    }
}

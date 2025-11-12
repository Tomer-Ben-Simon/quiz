package com.example.quiz2
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.RequiresApi
import androidx.room.Dao
import androidx.room.Database
import org.json.JSONObject
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Call
import okhttp3.Callback
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.google.firebase.firestore.FirebaseFirestore
import androidx.appcompat.widget.Toolbar
import com.google.firebase.auth.FirebaseAuth
import android.text.SpannableString
import android.text.style.RelativeSizeSpan


class MainActivity : AppCompatActivity() {

    @Entity(tableName = "users")
    data class User(
        @PrimaryKey val userId: String,
        val name: String
    )

    @Entity(
        tableName = "quiz_results",
        foreignKeys = [ForeignKey(
            entity = User::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )]
    )
    data class QuizResult(
        @PrimaryKey(autoGenerate = true) val id: Int = 0,
        val userId: String,
        val quizId: String,
        val score: Int,
        val timestamp: Long
    )

    data class Parashot(
        val name: String,
        val hebrew: String,
        val days: List<Days>,
        val week_day: String,
    )

    data class Days(
        val dayName: String,
        val questions: List<Question>
    )

    data class Question(
        val id: Int,
        val question_num_per_day: Int,
        val question: String,
        val options: List<String>,
        val answer: Int
    )

    @Dao
    interface UserDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insertUser(user: User)

        @Query("SELECT * FROM users")
        suspend fun getAllUsers(): List<User>
    }

    @Dao
    interface QuizResultDao {
        @Insert
        suspend fun insertResult(result: QuizResult)

        @Query("SELECT * FROM quiz_results WHERE userId = :userId")
        suspend fun getResultsForUser(userId: String): List<QuizResult>

        @Query("SELECT * FROM quiz_results ORDER BY score DESC")
        suspend fun getLeaderboard(): List<QuizResult>
    }
    @Database(entities = [User::class, QuizResult::class], version = 1)
    abstract class AppDatabase : RoomDatabase() {
        abstract fun userDao(): UserDao
        abstract fun quizResultDao(): QuizResultDao
    }

    private lateinit var Week_day: TextView
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
    private var lastIndex = 0
    private var currentIndex = 0
    private var score = 0
    private var answered = false
    var Parash=""

    val hebrewDate = JewishDate()
    val dayOfWeek = hebrewDate.dayOfWeek // returns 5 for Thursday
    val hebrewDayName = when (dayOfWeek) {
        1 -> "ראשון"
        2 -> "שני"
        3 -> "שלישי"
        4 -> "רביעי"
        5 -> "חמישי"
        6 -> "שישי"
        7 -> "שבת"
        else -> "לא ידוע"
    }
    var DayName = when (hebrewDayName) {
        "ראשון" -> "rishon"
        "שני" -> "sheni"
        "שלישי" -> "shlishi"
        "רביעי" -> "reviee"
        "חמישי" -> "hamishi"
        "שישי" -> "shishi"
        "שבת" -> "shabat"
        else -> "לא ידוע"
    }

    val quizResponses = mutableListOf<QuizResult>()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)


        // Find the menu item safely
        val resultsMenuItem = menu?.findItem(R.id.action_show_results)

        // --- SAFE CODE BLOCK ---
        // Only proceed if the menu item was actually found
        if (resultsMenuItem != null) {
            val title = resultsMenuItem.title

            // Also check if the title itself is not null or empty
            if (!title.isNullOrEmpty()) {
                val spannableTitle = SpannableString(title)

                // Apply the size span
                // The previous fix (?: 0) is still important here
                spannableTitle.setSpan(
                    RelativeSizeSpan(1.2f),
                    0,
                    title.length, // No need for Elvis operator here because we already checked for null
                    0
                )

                // Set the new styled title
                resultsMenuItem.title = spannableTitle
            }
        }
        // --- END OF SAFE CODE BLOCK ---
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_user -> {
                // טיפול בלחיצה על כפתור פרופיל המשתמש
                val intent = Intent(this, UserProfileActivity::class.java)
                startActivity(intent)
                true
            }
            // ===== הוספת טיפול בלחיצה על הכפתור החדש =====
            R.id.action_show_results -> {
                // קריאה לפונקציה שמציגה את רשימת המשתתפים
                showListOfParticipants()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        //FirebaseApp.initializeApp(this) // ← This is the magic line
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val tvParasha = findViewById<TextView>(R.id.Parash)
        fetchParasha { hebrewParasha, englishParash ->
            //lifecycleScope.launch(Dispatchers.Main) {
                tvParasha.text = hebrewParasha
                Parash= englishParash.toString()
            //}
        }
        val Week_day = findViewById<TextView>(R.id.Week_day)
        Week_day.text=hebrewDayName
        //val weekdays = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val weekdays = listOf("ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, weekdays)
        val spinnerWeekdays = findViewById<Spinner>(R.id.spinnerWeekdays)
        spinnerWeekdays.adapter = adapter
        val todayName = weekdays[dayOfWeek - 1] // Calendar.SUNDAY = 1
        val todayIndex = weekdays.indexOf(todayName)
        spinnerWeekdays.setSelection(todayIndex)
        spinnerWeekdays.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedDay = weekdays[position]
                Week_day.text=selectedDay
                DayName = when (selectedDay) {
                    "ראשון" -> "rishon"
                    "שני" -> "sheni"
                    "שלישי" -> "shlishi"
                    "רביעי" -> "reviee"
                    "חמישי" -> "hamishi"
                    "שישי" -> "shishi"
                    "שבת" -> "shabat"
                    else -> "לא ידוע"

                }

            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        tvQuestion = findViewById(R.id.tvQuestion)
        tvProgress = findViewById(R.id.tvProgress)
        tvFeedback = findViewById(R.id.tvFeedback)
        radioGroup = findViewById(R.id.radioGroup)
        option1 = findViewById(R.id.option1)
        option2 = findViewById(R.id.option2)
        option3 = findViewById(R.id.option3)
        option4 = findViewById(R.id.option4)
        btnAction = findViewById(R.id.btnAction)



        btnAction.setOnClickListener {

            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val isFirstRun = prefs.getBoolean("is_first_run", true)

            if (isFirstRun) {


                // ✅ Run your one-time setup code here
                renderQuestion()

                prefs.edit().putBoolean("is_first_run", false).apply()
            }

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
            //saveQuizResult()
        }
    }

    private fun isMainProcess(context: Context): Boolean {
        val pid = android.os.Process.myPid()
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processName = manager.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
        return processName == context.packageName
    }
    private fun saveQuizResult() {

        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        val user = auth.currentUser
        if (user != null) {
            Log.d("Auth", "Signed in anonymously as: ${user.uid}")
            val db = FirebaseFirestore.getInstance()
            val newScore = score // Replace with actual score from quiz logic
            val updateData = mapOf(
                "parasha" to Parash.toString(),
                "correctAnswers" to newScore,
                "totalQuestions" to lastIndex+1,
                "score" to (newScore.toDouble() / (lastIndex + 1)) * 100)

            db.collection("users")
                .document(user.uid)
                .update(updateData)
                .addOnSuccessListener {
                    Log.d("Firestore", "Score updated successfully")
                }
                .addOnFailureListener { e ->
                    Log.e("Firestore", "Error updating score", e)
                }


        }
    }

    fun fetchParasha(callback: (String, Any?) -> Unit) {
        val client = OkHttpClient()
        val request = Request.Builder().url("https://www.hebcal.com/shabbat?cfg=json&geonameid=293397&m=50").build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Parasha", "Failed to fetch", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { json ->
                    val jsonObject = JSONObject(json)
                    val items = jsonObject.getJSONArray("items")
                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        if (item.getString("category") == "parashat") {
                            val hebrew = item.getString("hebrew")
                            val parashaEnglish = item.getString("title")
                            runOnUiThread {
                                callback(hebrew,parashaEnglish)
                            }
                            break
                        }
                    }
                }
            }
        })
    }

    fun   getTodayQuestions(context: Context): List<Question> {
        val jsonString = loadQuizFromAssets(context)
        val root = JSONObject(jsonString)
        val parashot = root.getJSONArray("parashot")

        val questionsToday = mutableListOf<Question>()

        for (i in 0 until parashot.length())
        {
            val parasha = parashot.getJSONObject(i)
            val parashKeys = parasha.keys()
            val firstKey = parashKeys.next()
            if (Parash.toString().contains(firstKey.toString(),false))
            {
                val parasha_days = parasha.getJSONArray(firstKey.toString())
                for (j in 0 until parasha_days.length())
                {
                    val days = parasha_days.getJSONObject(j)
                    val daysKeys = days.keys()
                    val daysKeysFirstKey = daysKeys.next()
                    if (daysKeysFirstKey.toString() == DayName.toString())
                    {
                        val questions = days.getJSONArray(DayName.toString())
                        for (k in 0 until questions.length())
                        {
                            val q = questions.getJSONObject(k)
                            val question = Question(
                                id = q.getInt("id"),
                                question_num_per_day = q.getInt("question_num_per_day"),
                                question = q.getString("question"),
                                options = q.getJSONArray("options").let { arr ->
                                    List(arr.length()) { arr.getString(it) }
                                },
                                answer = q.getInt("answer")
                            )
                            questionsToday.add(question)
                        }
                        break
                    }
                }
                break
            }
        }
        return questionsToday
    }
    private fun renderQuestion() {
        val questions = getTodayQuestions(applicationContext)
        currentQ=questions[currentIndex]
        lastIndex=questions.size-1
        val q = questions[currentIndex]
            tvQuestion.text = questions[currentIndex].question
            tvProgress.text = " שאלה ${currentIndex + 1} מתוך ${questions.size}"
            tvFeedback.text = ""
            radioGroup.clearCheck()
            option1.text = q.options[0]
            option2.text = q.options[1]
            option3.text = q.options[2]
            option4.text = q.options[3]
            enableOptions(true)
            btnAction.text = "שלח"
            answered = false
    }
    private fun enableOptions(enable: Boolean) {
        option1.isEnabled = enable
        option2.isEnabled = enable
        option3.isEnabled = enable
        option4.isEnabled = enable
    }
    private fun checkAnswer(selectedIndex: Int) {
        //val jsonString = loadQuizFromAssets(applicationContext)
        //val quiz = Gson().fromJson(jsonString, Quiz::class.java)
        //val correctIndex = quiz.questions[currentIndex].answer.toInt()
        val correctIndex =currentQ.answer.toInt()
        //val correctIndex = questions[currentIndex].answerIndex
        answered = true
        enableOptions(false)
        if (selectedIndex == correctIndex) {
            score++
            tvFeedback.text = "נכון ✅"
        } else {
            tvFeedback.text = "טעות ❌ (התשובה: ${currentQ.options[correctIndex]})"
        }
        btnAction.text = if (currentIndex == lastIndex) "צפה בתוצאות"
        else "הבא"
    }

    private fun nextQuestion() {
        //val jsonString = loadQuizFromAssets(applicationContext)
        //val quiz = Gson().fromJson(jsonString, Quiz::class.java)
        if (currentIndex < lastIndex) {
            currentIndex++
            renderQuestion()
        } else {
            saveQuizResult()
            showResultDialog()

        }
    }

    private fun showResultDialog() {
        var message = "הציון שלך $score מתוך ${lastIndex+1}"



        AlertDialog.Builder(this)
            .setTitle("החידון הסתיים")
            .setMessage(message)
            .setPositiveButton("תודה") { _, _ -> showListOfParticipants() }
            //.setNegativeButton("סגור") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }
    private fun showListOfParticipants() {
        val db = FirebaseFirestore.getInstance()
        // 1. מיון התוצאות לפי שדה 'score' בסדר יורד והגבלה ל-10 התוצאות הראשונות
        val usersRef = db.collection("users")
            .orderBy("score", com.google.firebase.firestore.Query.Direction.DESCENDING) // מיון לפי ציון, מהגבוה לנמוך
            .limit(10) // הגבלה ל-10 התוצאות המובילות

        usersRef.get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Toast.makeText(this, "לא נמצאו משתתפים", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                Log.d("Firestore", "Query succeeded with ${result.size()} documents")
                val userList = mutableListOf<String>()

                // 2. יצירת הרשימה הממוספרת
                for ((index, document) in result.withIndex()) {
                    val name = document.getString("name") ?: "אלמוני"
                    var scoreValue = 0.0
                    val scoreData = document.get("score")

                    if (scoreData is Number) {
                        scoreValue = scoreData.toDouble()
                    }

                    val formattedScore = String.format("%.2f", scoreValue)
                    // הוספת מספור לכל שורה
                    userList.add("${index + 1}. $name - ציון: $formattedScore")
                }

                val message = userList.joinToString("\n")

                // 3. הצגת התוצאות ב-AlertDialog במקום Toast
                AlertDialog.Builder(this)
                    .setTitle("10 התוצאות הגבוהות ביותר")
                    .setMessage(message)
                    .setPositiveButton("סגור", null) // כפתור לסגירת הדיאלוג
                    .show()
            }
            .addOnFailureListener { exception ->
                Log.w("Firestore", "Error getting users: ", exception)
                Toast.makeText(this, "שגיאה בקבלת התוצאות", Toast.LENGTH_SHORT).show()
            }

        // אין צורך להתחיל מחדש את החידון כאן, לכן הקריאה ל-restartQuiz() נשארת בהערה
        // restartQuiz()
    }
    private fun restartQuiz() {
        currentIndex = 0
        score = 0
        renderQuestion()
    }
    private fun loadQuizFromAssets(context: Context): String {
        return context.assets.open("quiz_week_1.json").bufferedReader().use { it.readText() }
    }
}

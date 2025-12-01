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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query as FirestoreQuery
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


class MainActivity : AppCompatActivity() {






    data class Question(
        val id: Int,
        val questionNumPerDay: Int,
        val question: String,
        val options: List<String>,
        val answer: Int,
        var userAnswer: Int = -1,
        val day: String = ""
    )



    data class ParashaQuiz(
        val hebrewName: String = "",
        val questions: List<Question> = emptyList()
    )


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
    private var currentIndex = 0
    private var score = 0
    private var answered = false
    private var inReviewMode = false

    var parash=""
    private val questions = mutableListOf<Question>()
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
    val parashots =
        listOf(
            "בראשית","נח"  ,"לך לך"  ,"וירא" ,"חיי שרה","תולדות"  ,"ויצא"  ,"וישלח","וישב"  ,"מקץ"    ,"ויגש","ויחי",
            "שמות"  ,"וארא","בא"     ,"בשלח" ,"יתרו"   ,"משפטים"  ,"תרומה" ,"תצוה" ,"כי תשא","ויקהל"  ,"ויקהל-פקודי","פקודי",
            "ויקרא" ,"צו"  ,"שמיני"  ,"תזריע","תזריע-מצורע","מצורע"  ,"אחרי מות" ,"אחרי מות-קדושים","קדושים","אמור" ,"בהר"   ,"בהר-בחוקותי","בחוקותי",
            "במדבר" ,"נשא" ,"בהעלותך","שלח"  ,"קרח"    ,"חקת"     ,"בלק"   ,"פנחס" ,"מטות"  ,"מטות-מסעי" ,"מסעי"   ,
            "דברים" ,"ואתחנן" ,"עקב","ראה"  ,"שופטים"  ,"כי תצא"  ,"כי תבוא","נצבים" ,"נצבים-וילך","וילך","האזינו" ,"וזאת הברכה"
        )

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_user -> {
                val intent = Intent(this, UserProfileActivity::class.java)
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
        weekDay = findViewById(R.id.weekDay)
        weekDay.text=hebrewDayName
        tvParasha = findViewById(R.id.Parash)

        btnPrevious = findViewById(R.id.btnPrevious)
        btnNext = findViewById(R.id.btnNext)
        reviewNavigation = findViewById(R.id.reviewNavigation)

        val weekdays = listOf("ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת")
        val spinnerWeekdays = findViewById<Spinner>(R.id.spinnerWeekdays)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, weekdays)
        spinnerWeekdays.adapter = adapter
        val todayName = weekdays[dayOfWeek - 1]
        val todayIndex = weekdays.indexOf(todayName)
        spinnerWeekdays.setSelection(todayIndex)

        fetchParasha { hebrewParasha, _ ->
            val parashaIndex = findParashaIndex(hebrewParasha)
            if(parashaIndex != -1) {
                parash = parashots[parashaIndex]
                setupParashotSpinner(parash)
            }
        }

        spinnerParasha.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                parash = parent.getItemAtPosition(position).toString()
                tvParasha.text = parash
                resetQuizForDay(parash, dayName)
                tvProgress.visibility=View.GONE
                tvFeedback.visibility=View.GONE

            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        spinnerWeekdays.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedDay = weekdays[position]
                weekDay.text=selectedDay
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
                tvProgress.visibility=View.GONE
                tvFeedback.visibility=View.GONE

            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        btnAction.setOnClickListener {
            if (btnAction.text == "התחל") {
                tvQuestion.visibility = View.VISIBLE
                radioGroup.visibility = View.VISIBLE
                renderQuestion()
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
            if (inReviewMode && currentIndex > 0) {
                currentIndex--
                renderQuestionForReview()
            }
        }

        btnNext.setOnClickListener {
            if (inReviewMode && currentIndex < lastIndex) {
                currentIndex++
                renderQuestionForReview()
            }
        }
    }

    private fun getParashotList(): List<String> {
        val jsonString = loadQuizFromAssets(applicationContext)
        val root = JSONObject(jsonString)
        val parashotArray = root.getJSONArray("parashot")
        val parashotList = mutableListOf<String>()
        for (i in 0 until parashotArray.length()) {
            val parashaObject = parashotArray.getJSONObject(i)
            val parashaName = parashaObject.keys().next()
            parashotList.add(parashaName)
        }
        return parashotList
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupParashotSpinner(currentParashaKey: String) {
        val allParashotFromJSON = getParashotList()
        val spinnerList = mutableListOf<String>()
        val currentParashaIndex = allParashotFromJSON.indexOfFirst { it.equals(currentParashaKey, ignoreCase = true) }

        if (currentParashaIndex != -1) {
            spinnerList.add(allParashotFromJSON[currentParashaIndex])
            for (i in (currentParashaIndex - 1) downTo 0) {
                spinnerList.add(allParashotFromJSON[i])
            }
        } else {
            spinnerList.addAll(allParashotFromJSON)
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, spinnerList)
        spinnerParasha.adapter = adapter
        spinnerParasha.setSelection(0)
    }

    fun findParashaIndex(name: String): Int {
        return parashots.indexOfFirst { it.contains(name, ignoreCase = true) || name.contains(it, ignoreCase = true) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun resetQuizForDay(parasha: String, day: String) {
        currentIndex = 0
        score = 0
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
    @RequiresApi(Build.VERSION_CODES.O)
    private fun saveQuizResult() {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user != null) {
            Log.d("Auth", "Signed in anonymously as: ${user.uid}")
            val db = FirebaseFirestore.getInstance()
//            val newScore = score
//            val updateData = mapOf(
//                "parasha" to parash,
//                "correctAnswers" to newScore,
//                "totalQuestions" to lastIndex+1,
//                "score" to (newScore.toDouble() / (lastIndex + 1)) * 100)
            val userRef = db.collection("users").document(user.uid)
//            db.collection("users")
//                .document(user.uid)
//                .set(updateData, com.google.firebase.firestore.SetOptions.merge())
//                .addOnSuccessListener {
//                    Log.d("Firestore", "Score updated successfully")
//                }
//                .addOnFailureListener { e ->
//                    Log.e("Firestore", "Error updating score", e)
//                }
            userRef.get().addOnSuccessListener { userDoc ->
                val userName = userDoc.getString("name") ?: "אלמוני"
                val resultData = hashMapOf(
                    "userId" to user.uid,
                    "name" to userName,
                    "parasha" to parash,
                    "day" to dayName,
                    "score" to (score.toDouble() / questions.size) * 100,
                    "correctAnswers" to score,
                    "totalQuestions" to questions.size,
                    "date" to LocalDate.now().toString()
                )

                db.collection("quiz_results")
                    .add(resultData)
                    .addOnSuccessListener { Log.d("Firestore", "Quiz result saved successfully.") }
                    .addOnFailureListener { e -> Log.e("Firestore", "Error saving quiz result.", e) }
            }.addOnFailureListener { e ->
                Log.e("Firestore", "Failed to get user name", e)
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
    private fun fetchQuestionsFromFirestore(parasha: String, day: String, callback: (List<Question>) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("parashot_quizzes").document(parasha)
            .get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    val allQuestions = document.toObject(ParashaQuiz::class.java)?.questions
                    val questionsForDay = allQuestions?.filter { it.day == day }
                    callback(questionsForDay ?: emptyList())
                } else {
                    callback(emptyList())
                }
            }
            .addOnFailureListener {
                callback(emptyList())
            }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    fun   getTodayQuestions(context: Context, userId: String?): List<Question> {
        val jsonString = loadQuizFromAssets(context)
        val root = JSONObject(jsonString)
        val parashot = root.getJSONArray("parashot")

        val questionsToday = mutableListOf<Question>()

        val dayNameToKeys = mapOf(
            "rishon" to listOf("rishon", "ראשון"),
            "sheni" to listOf("sheni", "שני"),
            "shlishi" to listOf("shlishi", "שלישי"),
            "reviee" to listOf("reviee", "רביעי"),
            "hamishi" to listOf("hamishi", "חמישי"),
            "shishi" to listOf("shishi", "שישי"),
            "shabat" to listOf("shabat", "שבת")
        )
        val possibleKeysForDay = dayNameToKeys[dayName] ?: listOf(dayName)


        for (i in 0 until parashot.length())
        {
            val parashaObject = parashot.getJSONObject(i)
            val parashaKeys = parashaObject.keys()
            if (!parashaKeys.hasNext()) continue
            val firstKey = parashaKeys.next()
            if (parash.equals(firstKey,ignoreCase = true))
            {
                val parashaDays = parashaObject.getJSONArray(firstKey)
                for (j in 0 until parashaDays.length())
                {
                    val days = parashaDays.getJSONObject(j)
                    val daysKeys = days.keys()
                    if (!daysKeys.hasNext()) continue
                    val daysKeysFirstKey = daysKeys.next()
                    if (possibleKeysForDay.contains(daysKeysFirstKey))
                    {
                        val questionsArray = days.getJSONArray(daysKeysFirstKey)
                        for (k in 0 until questionsArray.length())
                        {
                            val q = questionsArray.getJSONObject(k)
                            val question = Question(
                                id = q.getInt("id"),
                                questionNumPerDay = q.getInt("question_num_per_day"),
                                question = q.getString("question"),
                                options = q.getJSONArray("options").let { arr ->
                                    List(arr.length()) { arr.getString(it) }
                                },
                                answer = q.getInt("answer")
                            )
                            questions.add(question)
                        }
                        break
                    }
                }
                break
            }
        }
        val seed = (userId!! + LocalDate.now().toString()).hashCode().toLong()
        questions.shuffle(Random(seed))

        return if (questions.size > 10) {
            questions.take(10)
        } else {
            questions
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun renderQuestion() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
            return
        }
        if (questions.isEmpty()) {
            getTodayQuestions(applicationContext, userId)
        }
        if (questions.isEmpty()) {
            Toast.makeText(this, "No questions available for today.", Toast.LENGTH_SHORT).show()
            btnAction.text = "התחל"
            tvQuestion.visibility = View.GONE
            radioGroup.visibility = View.GONE
            return
        }
        currentQ=questions[currentIndex]
        lastIndex=questions.size-1
        val q = questions[currentIndex]
        tvQuestion.text = questions[currentIndex].question
        tvProgress.visibility=View.VISIBLE
        tvFeedback.visibility=View.VISIBLE
        tvProgress.text =  " שאלה ${currentIndex + 1} מתוך ${questions.size}"
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

    private fun renderQuestionForReview() {
        currentQ = questions[currentIndex]
        tvQuestion.text = currentQ.question
        tvProgress.text = " שאלה ${currentIndex + 1} מתוך ${questions.size}"

        // Disable radio buttons and set the user's answer
        enableOptions(false)
        val userAnswerIndex = currentQ.userAnswer
        if (userAnswerIndex != -1) {
            (radioGroup.getChildAt(userAnswerIndex) as RadioButton).isChecked = true
        }

        // Show feedback
        val correctIndex = currentQ.answer
        if (userAnswerIndex == correctIndex) {
            tvFeedback.text = "נכון ✅"
        } else {
            tvFeedback.text = "טעות ❌ (התשובה: ${currentQ.options[correctIndex]})"
        }
    }

    private fun enableOptions(enable: Boolean) {
        for (i in 0 until radioGroup.childCount) {
            radioGroup.getChildAt(i).isEnabled = enable
        }
    }
    private fun checkAnswer(selectedIndex: Int) {
        currentQ.userAnswer = selectedIndex // Save user's answer
        val correctIndex =currentQ.answer
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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun nextQuestion() {
        if (currentIndex < lastIndex) {
            currentIndex++
            renderQuestion()
        } else {
            saveQuizResult()
            saveLastPlayedDate(parash, dayName)
            showResultDialog()

        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun showResultDialog() {
        val message = "הציון שלך $score מתוך ${lastIndex+1}"

        AlertDialog.Builder(this)
            .setTitle("החידון הסתיים")
            .setMessage(message)
            .setPositiveButton("תודה") { _, _ -> showListOfParticipants() }
            .setNegativeButton("עבור על התשובות") { _, _ ->
                inReviewMode = true
                btnAction.visibility = View.GONE
                reviewNavigation.visibility = View.VISIBLE
                currentIndex = 0
                renderQuestionForReview()
            }
            .setCancelable(false)
            .show()
    }

    private fun showListOfParticipants() {
        val db = FirebaseFirestore.getInstance()

        // 1. Get all results for the current Parasha
        db.collection("quiz_results")
            .whereEqualTo("parasha", parash)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Toast.makeText(this, "לא נמצאו תוצאות עבור פרשה זו", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // 2. Aggregate scores on the client
                val userScores = mutableMapOf<String, MutableMap<String, Double>>()
                val userNames = mutableMapOf<String, String>()

                for (document in result.documents) {
                    val userId = document.getString("userId") ?: continue
                    val day = document.getString("day") ?: continue
                    val score = document.getDouble("score") ?: 0.0
                    val name = document.getString("name") ?: "אלמוני"

                    userNames.getOrPut(userId) { name }
                    val dayScores = userScores.getOrPut(userId) { mutableMapOf() }
                    dayScores[day] = score
                }

                // 3. Create leaderboard entries with both total and current day scores
                val leaderboardEntries = userScores.map { (userId, dayScores) ->
                    val totalScore = dayScores.values.sum()
                    val currentDayScore = dayScores[dayName] ?: 0.0
                    val name = userNames[userId]!!
                    Triple(name, currentDayScore, totalScore)
                }

                // 4. Sort by total score and take top 10
                val sortedLeaderboard = leaderboardEntries.sortedByDescending { it.third }.take(10)

                // 5. Format for display
                val userList = sortedLeaderboard.mapIndexed { index, (name, currentDayScore, totalScore) ->
                    val formattedCurrentDayScore = String.format("%.2f", currentDayScore)
                    val formattedTotalScore = String.format("%.2f", totalScore)
                    "${index + 1}. $name - ציון ליום ${weekDay.text}: $formattedCurrentDayScore, ציון כולל: $formattedTotalScore"
                }

                val message = userList.joinToString("\n")

                AlertDialog.Builder(this)
                    .setTitle("10 התוצאות הגבוהות ביותר לפרשת $parash")
                    .setMessage(message)
                    .setPositiveButton("סגור", null)
                    .show()
            }
            .addOnFailureListener { exception ->
                Log.w("Firestore", "Error getting quiz results for leaderboard: ", exception)
                Toast.makeText(this, "שגיאה בקבלת התוצאות.", Toast.LENGTH_LONG).show()
            }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun restartQuiz() {
        currentIndex = 0
        score = 0
        renderQuestion()
    }
    private fun loadQuizFromAssets(context: Context): String {
        return context.assets.open("quiz_week_1.json").bufferedReader().use { it.readText() }
    }
}

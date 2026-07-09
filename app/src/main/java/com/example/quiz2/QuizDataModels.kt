package com.example.quiz2

// מחלקות הנתונים הללו משותפות ל-MainActivity ול-AdminActivity,
// ולכן ממוקמות בקובץ נפרד כדי למנוע בעיות תלות וטיפוסים.

data class Question(
    val id: Int = 0,
    val questionNumPerDay: Int = 0,
    val question: String = "",
    val options: List<String> = emptyList(),
    val answer: Int = 0,
    var userAnswer: Int = -1, // נשאר עבור מצב חידון, לאו דווקא ב-JSON
    val day: String = "" // נשאר עבור מצב חידון, לאו דווקא ב-JSON
)

// מייצג אוסף שאלות ליום ספציפי בתוך פרשה
data class Section(
    val name: String, // שם היום, לדוגמה "ראשון" או "rishon"
    val questions: List<Question>
)

// מייצג פרשה שלמה עם כל הסקשנים (ימים) שלה
data class Parasha(
    val name: String, // שם הפרשה, לדוגמה "שופטים"
    val sections: List<Section>
)

// מבנה השורש של קובץ ה-JSON המקומי
// Gson יטפל בזה בצורה טובה יותר אם נגדיר את זה כרשימה של אובייקטים
// שכל אחד מהם הוא מפה של שם הפרשה לרשימת הימים שלה.
data class Root(
    val parashot: List<Map<String, List<Map<String, List<Question>>>>>,
)

// מחלקת נתונים לטעינה מ-Firestore (אם מבנה ה-Firestore שונה)
data class ParashaQuiz(
    val hebrewName: String = "",
    val questions: List<Question> = emptyList() // רשימת שאלות שטוחה עבור פרשה, אם זה מבנה ה-Firestore
)

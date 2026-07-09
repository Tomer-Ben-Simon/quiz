const functions = require("firebase-functions");
const admin = require("firebase-admin");
const fetch = require("node-fetch"); // <-- Import node-fetch

// Initialize the admin SDK to access Firestore
admin.initializeApp();
const db = admin.firestore();

// Helper function to fetch Parasha from Hebcal
async function getCurrentParashaName(date) {
    // Format the date to YYYY-MM-DD for the API
    const year = date.getFullYear();
    const month = (date.getMonth() + 1).toString().padStart(2, '0');
    const day = date.getDate().toString().padStart(2, '0');
    const formattedDate = `${year}-${month}-${day}`;

    // Hebcal API URL for weekly Torah portion
    const hebcalUrl = `https://www.hebcal.com/converter?cfg=json&gy=${year}&gm=${month}&gd=${day}&g2h=1&nx=1&gs=1&sps=1&i=on`;

    try {
        const response = await fetch(hebcalUrl);
        if (!response.ok) {
            throw new Error(`Hebcal API request failed with status: ${response.status}`);
        }
        const data = await response.json();

        // Look for the "parashat hashavua" (weekly Torah portion) event
        const parashaEvent = data.events.find(event => event.category === "parashat");

        if (parashaEvent && parashaEvent.hebrew) {
            // The API provides the Hebrew name with "פרשת " prefix.
            // We need to extract just the parasha name.
            // Example: "פרשת משפטים" -> "משפטים"
            const parashaNameHebrew = parashaEvent.hebrew.replace("פרשת ", "").trim();

            // You might also want the English name for consistency if your DB uses it
            // For now, let's assume your DB stores the Hebrew names as used in your app.
            return parashaNameHebrew;

        } else {
            console.log("No Parasha event found for today:", formattedDate);
            return null; // Or throw an error if a Parasha is expected every week
        }
    } catch (error) {
        console.error("Error fetching Parasha from Hebcal:", error);
        throw error; // Re-throw to be caught by the main function's error handler
    }
}


// The Cloud Function for the weekly lottery
exports.runWeeklyLottery = functions.pubsub.schedule("59 23 * * 6")
    .timeZone("Asia/Jerusalem") // Important: Set to your target timezone
    .onRun(async (context) =>
    {
        console.log("Starting the weekly lottery process!");

        // 1. Calculate the start of the past week (last Sunday)
        const today = new Date();
        const lastSunday = new Date(today);
        lastSunday.setDate(today.getDate() - today.getDay()); // day 0 is Sunday
        lastSunday.setHours(0, 0, 0, 0); // Set to the beginning of the day

        try {
            // 2. Determine the current Parasha dynamically
            const currentParasha = await getCurrentParashaName(today); // Use today's date to get the parasha

            if (!currentParasha) {
                console.log("Could not determine current Parasha. Aborting lottery.");
                return null;
            }
            console.log(`Current Parasha for lottery: ${currentParasha}`);


            // 3. Fetch the first 10 participants for this week's parasha
            const participantsSnapshot = await db.collection("quizResults")
                .where("parasha", "==", currentParasha)
                .where("completionTime", ">=", lastSunday) // Only from the past week
                .orderBy("completionTime", "asc") // Order by time to get the first ones
                .limit(10) // Get only the top 10
                .get();

            if (participantsSnapshot.empty) {
                console.log("No participants for the lottery this week.");
                return null;
            }

            const participants = [];
            participantsSnapshot.forEach(doc => {
                participants.push(doc.data());
            });

            console.log(`Found ${participants.length} eligible participants.`);

            // 4. Randomly select one winner from the list
            const winnerIndex = Math.floor(Math.random() * participants.length);
            const winner = participants[winnerIndex];

            console.log(`The winner is user ID: ${winner.userId}, Name: ${winner.userName}, Parasha: ${winner.parasha}`);

            // 5. Save the winner to a separate "lotteryWinners" collection for records
            await db.collection("lotteryWinners").add({
                userId: winner.userId,
                userName: winner.userName, // Assuming you save userName in quizResults
                parasha: winner.parasha,
                drawDate: new Date()
            });

            console.log("Lottery winner has been successfully saved.");
            return null;

        } catch (error) {
            console.error("Error running the weekly lottery:", error);
            return null;
        }
    });
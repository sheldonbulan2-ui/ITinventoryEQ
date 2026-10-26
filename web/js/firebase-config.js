/**
 * Firebase Firestore Configuration (Optional Direct Real-Time Cloud Sync)
 * 
 * To switch the Web Admin Panel from the local Node.js Server to direct Firebase Firestore:
 * 1. Create a Firebase project at https://console.firebase.google.com
 * 2. Create a Firestore Database in Test / Production mode.
 * 3. Replace the placeholder config below with your Firebase Web App credentials.
 */

const firebaseConfig = {
  apiKey: "AIzaSyYOUR_FIREBASE_API_KEY_HERE",
  authDomain: "it-inventory-eq.firebaseapp.com",
  projectId: "it-inventory-eq",
  storageBucket: "it-inventory-eq.appspot.com",
  messagingSenderId: "123456789012",
  appId: "1:123456789012:web:abcdef1234567890"
};

// Check if Firebase SDK is loaded on page
if (typeof firebase !== 'undefined' && firebase.initializeApp) {
  try {
    firebase.initializeApp(firebaseConfig);
    const firestoreDb = firebase.firestore();
    console.log('[Firebase] Initialized Firestore successfully');
    
    // Export for use if direct cloud mode is toggled
    window.firestoreDb = firestoreDb;
  } catch (err) {
    console.warn('[Firebase] Config placeholder detected, using default server stream');
  }
}

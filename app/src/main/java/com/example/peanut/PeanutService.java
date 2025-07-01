package com.example.peanut;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.Locale;

public class PeanutService extends Service {

    private static final String TAG = "PeanutService";
    private static final int NOTIFICATION_ID = 1; // Unique ID for the foreground notification
    private static final String CHANNEL_ID = "PeanutServiceChannel"; // ID for the notification channel

    // Custom actions for Intents to control the service
    public static final String ACTION_START_CONVERSATION = "com.example.peanut.ACTION_START_CONVERSATION";
    public static final String ACTION_STOP_SERVICE = "com.example.peanut.ACTION_STOP_SERVICE";
    public static final String ACTION_START_SERVICE_ON_BOOT = "com.example.peanut.ACTION_START_SERVICE_ON_BOOT"; // Action from BootReceiver

    private static final String UTTERANCE_ID_LISTEN = "utterance_id_listen";
    private static final String UTTERANCE_ID_RESPONSE = "utterance_id_response";
    private static final String UTTERANCE_ID_GOODBYE = "utterance_id_goodbye"; // New ID for explicit goodbyes

    private TextToSpeech textToSpeech;
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private Handler mainHandler; // Handler to post tasks to the main thread

    // Flag to track if TTS is initialized successfully
    private boolean isTtsInitialized = false;

    // --- Service Lifecycle ---

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "PeanutService onCreate");
        mainHandler = new Handler(Looper.getMainLooper()); // Initialize mainHandler
        // Initialize TTS and STT here
        initializeTextToSpeech();
        initializeSpeechRecognizer(); // SpeechRecognizer creation also needs to be on the main thread
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "PeanutService onStartCommand");

        createNotificationChannel(); // Create channel for Android O+
        startForeground(NOTIFICATION_ID, createNotification()); // Start foreground service

        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START_CONVERSATION.equals(action)) {
                Log.d(TAG, "Received ACTION_START_CONVERSATION from MainActivity.");
                // Ensure TTS is ready before speaking
                if (isTtsInitialized) {
                    speak(getString(R.string.listening_prompt), UTTERANCE_ID_LISTEN);
                } else {
                    Log.w(TAG, "TTS not initialized yet. Cannot start conversation immediately.");
                    // Optionally, queue the speak call or show a message
                    showToast("Peanut's voice is not ready yet. Please try again in a moment.");
                }
            } else if (ACTION_STOP_SERVICE.equals(action)) {
                Log.d(TAG, "Received ACTION_STOP_SERVICE command.");
                // Speak goodbye, then stop the service.
                // Use a distinct utterance ID for goodbye so it doesn't trigger listening again.
                speak("Goodbye! Stopping Peanut service.", UTTERANCE_ID_GOODBYE);
                stopSelfDelayed(2000); // Give time for goodbye to be spoken
                return START_NOT_STICKY; // Service will not be restarted if stopped explicitly
            } else if (ACTION_START_SERVICE_ON_BOOT.equals(action)) {
                Log.d(TAG, "Received ACTION_START_SERVICE_ON_BOOT. Service initialized.");
                // On boot, don't immediately start listening unless specifically desired.
                // If you want Peanut to say something, ensure TTS is ready.
                // if (isTtsInitialized) {
                //     speak("Peanut is ready.", null);
                // }
            }
        }

        // We want the service to continue running until it is explicitly stopped
        return START_STICKY; // If service is killed by system, it will be restarted
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "PeanutService onDestroy");
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            Log.d(TAG, "TTS shut down");
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            Log.d(TAG, "SpeechRecognizer destroyed");
        }
        showToast("Peanut service stopped.");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // For this simple case, we are not using binding, so return null
        return null;
    }

    // --- Foreground Notification Management ---

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    // Use low importance to make it less intrusive but persistent
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription(getString(R.string.notification_channel_description));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification createNotification() {
        // Intent to open MainActivity when notification is tapped
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE); // FLAG_IMMUTABLE required for API 23+

        // Intent for "Stop" action button in notification
        Intent stopServiceIntent = new Intent(this, PeanutService.class);
        stopServiceIntent.setAction(ACTION_STOP_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getService(this,
                0, stopServiceIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Replace R.drawable.ic_stop_black_24dp with an actual icon in your project
        // If you don't have it, remove this line or add a suitable icon.
        // For example, you can use android.R.drawable.ic_media_pause for a generic stop icon.
        int stopIconResId = R.drawable.ic_stop_black_24dp; // Ensure this drawable exists
        try {
            // Check if the drawable exists to prevent crash if not found
            getResources().getDrawable(stopIconResId, null);
        } catch (android.content.res.Resources.NotFoundException e) {
            Log.e(TAG, "Missing drawable: ic_stop_black_24dp. Using default Android stop icon.", e);
            stopIconResId = android.R.drawable.ic_media_pause; // Fallback to a common Android icon
        }


        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(R.mipmap.ic_launcher) // Use your app's icon
                .setContentIntent(pendingIntent)
                // Add a "Stop" button to the notification
                .addAction(stopIconResId, getString(R.string.notification_stop_action), stopPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW) // Matches channel importance
                .build();
    }

    // --- Text-to-Speech (TTS) Initialization and Speaking ---

    private void initializeTextToSpeech() {
        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = textToSpeech.setLanguage(Locale.US); // Consider Locale.getDefault() if broader language support is needed
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e(TAG, "TTS Language not supported or data missing during init. Result: " + result);
                        showToast("Peanut's voice language not supported.");
                        isTtsInitialized = false;
                        // Offer to install language data if necessary
                        Intent installIntent = new Intent();
                        installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                        installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(installIntent);
                    } else {
                        Log.d(TAG, "TTS Initialized successfully. Language set result: " + result);
                        isTtsInitialized = true; // Set flag to true only on success
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                                @Override
                                public void onStart(String utteranceId) {
                                    Log.d(TAG, "TTS onStart: " + utteranceId);
                                    // Make sure speech recognizer is not listening before starting new TTS
                                    if (speechRecognizer != null) {
                                        speechRecognizer.cancel();
                                    }
                                }

                                @Override
                                public void onDone(String utteranceId) {
                                    Log.d(TAG, "TTS onDone: " + utteranceId);
                                    // Trigger STT only if the completed speech was meant to prompt listening
                                    if (utteranceId != null && (utteranceId.equals(UTTERANCE_ID_LISTEN) || utteranceId.equals(UTTERANCE_ID_RESPONSE))) {
                                        // *** CRITICAL FIX: Post the call to startListening to the main thread ***
                                        mainHandler.post(() -> {
                                            if (speechRecognizer != null) {
                                                try {
                                                    Log.d(TAG, "Attempting to start SpeechRecognizer from TTS onDone (main thread).");
                                                    speechRecognizer.startListening(speechRecognizerIntent);
                                                } catch (Exception e) {
                                                    Log.e(TAG, "Error starting SpeechRecognizer on main thread: " + e.getMessage(), e);
                                                    showToast("Failed to start listening. Please try again.");
                                                }
                                            } else {
                                                Log.e(TAG, "SpeechRecognizer is null when trying to start listening after TTS done.");
                                                showToast("Speech input is not ready.");
                                            }
                                        });
                                    } else if (utteranceId != null && utteranceId.equals(UTTERANCE_ID_GOODBYE)) {
                                        Log.d(TAG, "Goodbye utterance finished. Service will stop shortly.");
                                        // The stopSelfDelayed will handle stopping the service.
                                    }
                                }

                                @Override
                                public void onError(String utteranceId) {
                                    Log.e(TAG, "TTS onError: " + utteranceId);
                                    showToast("Peanut had an error speaking.");
                                }

                                @Override
                                public void onStop(String utteranceId, boolean interrupted) {
                                    Log.d(TAG, "TTS onStop: " + utteranceId + ", interrupted: " + interrupted);
                                }
                            });
                        } else {
                            Log.w(TAG, "UtteranceProgressListener not fully supported below API 21, speech timing might be less precise. Consider upgrading API level if possible.");
                            isTtsInitialized = false; // Treat as not fully initialized if listener isn't reliable
                        }
                    }
                } else {
                    Log.e(TAG, "TTS Initialization failed! Status: " + status);
                    showToast("Peanut's voice engine failed to initialize.");
                    isTtsInitialized = false;
                }
            }
        });
    }

    private void speak(String text, String utteranceId) {
        if (!isTtsInitialized) {
            Log.e(TAG, "TTS not initialized. Cannot speak: '" + text + "'");
            showToast("Peanut cannot speak right now (voice engine not ready).");
            return;
        }

        // Stop current speech if any, to prevent overlapping or queueing issues
        if (textToSpeech.isSpeaking()) {
            textToSpeech.stop();
        }

        // Check language availability before speaking
        int langAvailability = textToSpeech.isLanguageAvailable(Locale.US);
        Log.d(TAG, "speak: Attempting to speak '" + text + "'. Language availability for Locale.US: " + langAvailability);

        // Only proceed if language is available or partially available
        if (langAvailability >= TextToSpeech.LANG_AVAILABLE) {
            Bundle params = new Bundle();
            // Using a unique Utterance ID for better tracking in onDone/onError
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
        } else {
            Log.e(TAG, "TTS language not available to speak: '" + text + "' (Availability code: " + langAvailability + ")");
            showToast("Peanut cannot speak due to language issues.");
            // Optionally, try setting a different default language or prompt for installation
        }
    }

    // Overloaded speak method for general responses, which will trigger listening by default
    private void speak(String text) {
        speak(text, UTTERANCE_ID_RESPONSE);
    }

    // --- SpeechRecognizer (STT) Initialization and Listener ---

    private void initializeSpeechRecognizer() {
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault()); // Use device's default language
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, this.getPackageName());
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1); // Get only the best result

        // Set timeouts for speech input
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L); // Silence after speech to consider complete
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L); // Silence to consider timeout (no speech at all)

        // *** CRITICAL FIX: Ensure SpeechRecognizer.createSpeechRecognizer is called on the main thread ***
        mainHandler.post(() -> {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplicationContext()); // Use application context
                speechRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override
                    public void onReadyForSpeech(Bundle params) {
                        Log.d(TAG, "onReadyForSpeech: SpeechRecognizer is ready.");
                        // showToast("Listening...");
                    }

                    @Override
                    public void onBeginningOfSpeech() {
                        Log.d(TAG, "onBeginningOfSpeech: User has started speaking.");
                    }

                    @Override
                    public void onRmsChanged(float rmsdB) { /* Log.d(TAG, "onRmsChanged: " + rmsdB); */ }

                    @Override
                    public void onBufferReceived(byte[] buffer) { Log.d(TAG, "onBufferReceived"); }

                    @Override
                    public void onEndOfSpeech() { Log.d(TAG, "onEndOfSpeech: User has stopped speaking."); }

                    @Override
                    public void onError(int error) {
                        String errorMessage = getErrorText(error);
                        Log.e(TAG, "STT Error: " + errorMessage);
                        showToast("Speech recognition error: " + errorMessage);

                        // If no speech input (timeout), or no match, prompt the user again.
                        // These will call `speak(response)` which in turn will trigger new listening.
                        // Make sure to stop current speech recognition if it's still running
                        if (speechRecognizer != null) {
                            speechRecognizer.cancel();
                        }

                        if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            speak("I didn't hear anything. Please try again.");
                        } else if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                            speak("I didn't understand that. Can you please rephrase?");
                        } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                            speak("My speech recognition is busy. Please wait a moment and try again.");
                        }
                        // For other errors like network, permissions, etc., you might want to stop trying or show a persistent error.
                    }

                    @Override
                    public void onResults(Bundle results) {
                        Log.d(TAG, "onResults: Speech results received.");
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String userSpeech = matches.get(0);
                            Log.d(TAG, "User said: " + userSpeech);
                            processUserSpeech(userSpeech);
                        } else {
                            speak("I didn't catch that. Could you please repeat?");
                        }
                    }

                    @Override
                    public void onPartialResults(Bundle partialResults) { /* Log.d(TAG, "onPartialResults"); */ }

                    @Override
                    public void onEvent(int eventType, Bundle params) { Log.d(TAG, "onEvent: " + eventType); }
                });
                Log.d(TAG, "SpeechRecognizer initialized on main thread.");
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize SpeechRecognizer on main thread: " + e.getMessage(), e);
                showToast("Speech recognition engine failed to initialize.");
            }
        });
    }

    // Helper method to get human-readable error messages for SpeechRecognizer
    public static String getErrorText(int errorCode) {
        String message;
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO: message = "Audio recording error"; break;
            case SpeechRecognizer.ERROR_CLIENT: message = "Client side error"; break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: message = "Insufficient permissions. Please grant microphone access."; break;
            case SpeechRecognizer.ERROR_NETWORK: message = "Network error"; break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: message = "Network timeout"; break;
            case SpeechRecognizer.ERROR_NO_MATCH: message = "No match found"; break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: message = "Recognition service is busy"; break;
            case SpeechRecognizer.ERROR_SERVER: message = "Server error"; break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: message = "No speech input received"; break;
            default: message = "Unknown speech recognition error (" + errorCode + ")"; break;
        }
        return message;
    }

    // --- Core Logic: Processing User Speech ---
    private void processUserSpeech(String speech) {
        String lowerCaseSpeech = speech.toLowerCase(Locale.US);
        String response;

        if (lowerCaseSpeech.contains("hello") || lowerCaseSpeech.contains("hi")) {
            response = "Hello there! How can I help you?";
        } else if (lowerCaseSpeech.contains("peanut")) {
            response = "Yes!, how may i help?";
        }else if (lowerCaseSpeech.contains("how are you"))
        {
            response = "I'm doing great, thank you for asking!";

        } else if (lowerCaseSpeech.contains("what is your name"))
        {
            response = "My name is Peanut. Nice to meet you.";

        } else if (lowerCaseSpeech.contains("tell me a joke"))
        {
            response = "Why don't scientists trust atoms? Because they make up everything!";

        } else if (lowerCaseSpeech.contains("goodbye") || lowerCaseSpeech.contains("bye")) {
            response = "Goodbye! Have a great day.";
            // If it's a goodbye, stop the service after speaking the response
            speak(response, UTTERANCE_ID_GOODBYE); // Use a unique ID that will NOT trigger listening
            stopSelfDelayed(2000); // Give time for goodbye to be spoken (adjust as needed)
            return; // Don't proceed to listen again if stopping
        } else {
            response = "I didn't understand that. Can you please rephrase?";
        }

        speak(response); // Make Peanut speak the response, which will then trigger listening
    }

    // Helper to stop service after a delay (useful for "goodbye" message)
    private void stopSelfDelayed(long delayMillis) {
        mainHandler.postDelayed(this::stopSelf, delayMillis);
    }

    // Helper to show Toast messages safely from any thread
    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(PeanutService.this, message, Toast.LENGTH_SHORT).show());
    }
}
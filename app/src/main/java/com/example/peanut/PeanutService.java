// com.example.peanut/PeanutService.java (Updated portions)

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

    private boolean isTtsInitialized = false;
    private ConversationManager conversationManager; // NEW: Instance of ConversationManager

    // --- Service Lifecycle ---

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "PeanutService onCreate");
        mainHandler = new Handler(Looper.getMainLooper());
        conversationManager = new ConversationManager(); // Initialize ConversationManager
        initializeTextToSpeech();
        initializeSpeechRecognizer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "PeanutService onStartCommand");

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());

        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START_CONVERSATION.equals(action)) {
                Log.d(TAG, "Received ACTION_START_CONVERSATION from MainActivity.");
                conversationManager.resetConversation(); // Reset conversation state for a new interaction
                if (isTtsInitialized) {
                    speak(getString(R.string.listening_prompt), UTTERANCE_ID_LISTEN);
                } else {
                    Log.w(TAG, "TTS not initialized yet. Cannot start conversation immediately.");
                    showToast("Peanut's voice is not ready yet. Please try again in a moment.");
                }
            } else if (ACTION_STOP_SERVICE.equals(action)) {
                Log.d(TAG, "Received ACTION_STOP_SERVICE command.");
                speak("Goodbye! Stopping Peanut service.", UTTERANCE_ID_GOODBYE);
                stopSelfDelayed(2000);
                return START_NOT_STICKY;
            } else if (ACTION_START_SERVICE_ON_BOOT.equals(action)) {
                Log.d(TAG, "Received ACTION_START_SERVICE_ON_BOOT. Service initialized.");
                conversationManager.resetConversation(); // Reset on boot as well
            }
        }
        return START_STICKY;
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
        return null;
    }

    // --- Foreground Notification Management (No changes needed here from previous version) ---
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
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
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent stopServiceIntent = new Intent(this, PeanutService.class);
        stopServiceIntent.setAction(ACTION_STOP_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getService(this,
                0, stopServiceIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int stopIconResId = R.drawable.ic_stop_black_24dp; // Ensure this drawable exists
        try {
            getResources().getDrawable(stopIconResId, null);
        } catch (android.content.res.Resources.NotFoundException e) {
            Log.e(TAG, "Missing drawable: ic_stop_black_24dp. Using default Android stop icon.", e);
            stopIconResId = android.R.drawable.ic_media_pause;
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .addAction(stopIconResId, getString(R.string.notification_stop_action), stopPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    // --- Text-to-Speech (TTS) Initialization and Speaking (Mostly unchanged, but logic around UTTERANCE_ID_GOODBYE improved) ---
    private void initializeTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TTS Language not supported or data missing during init. Result: " + result);
                    showToast("Peanut's voice language not supported.");
                    isTtsInitialized = false;
                    Intent installIntent = new Intent();
                    installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                    installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(installIntent);
                } else {
                    Log.d(TAG, "TTS Initialized successfully. Language set result: " + result);
                    isTtsInitialized = true;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                            @Override
                            public void onStart(String utteranceId) {
                                Log.d(TAG, "TTS onStart: " + utteranceId);
                                if (speechRecognizer != null) {
                                    speechRecognizer.cancel(); // Stop current listening if TTS starts
                                }
                            }

                            @Override
                            public void onDone(String utteranceId) {
                                Log.d(TAG, "TTS onDone: " + utteranceId);
                                if (utteranceId != null && (utteranceId.equals(UTTERANCE_ID_LISTEN) || utteranceId.equals(UTTERANCE_ID_RESPONSE))) {
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
                        isTtsInitialized = false;
                    }
                }
            } else {
                Log.e(TAG, "TTS Initialization failed! Status: " + status);
                showToast("Peanut's voice engine failed to initialize.");
                isTtsInitialized = false;
            }
        });
    }

    private void speak(String text, String utteranceId) {
        if (!isTtsInitialized) {
            Log.e(TAG, "TTS not initialized. Cannot speak: '" + text + "'");
            showToast("Peanut cannot speak right now (voice engine not ready).");
            return;
        }

        if (textToSpeech.isSpeaking()) {
            textToSpeech.stop();
        }

        int langAvailability = textToSpeech.isLanguageAvailable(Locale.US);
        Log.d(TAG, "speak: Attempting to speak '" + text + "'. Language availability for Locale.US: " + langAvailability);

        if (langAvailability >= TextToSpeech.LANG_AVAILABLE) {
            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
        } else {
            Log.e(TAG, "TTS language not available to speak: '" + text + "' (Availability code: " + langAvailability + ")");
            showToast("Peanut cannot speak due to language issues.");
        }
    }

    private void speak(String text) {
        speak(text, UTTERANCE_ID_RESPONSE);
    }

    // --- SpeechRecognizer (STT) Initialization and Listener (No functional changes from previous version) ---
    private void initializeSpeechRecognizer() {
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, this.getPackageName());
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);

        mainHandler.post(() -> {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplicationContext());
                speechRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override
                    public void onReadyForSpeech(Bundle params) { Log.d(TAG, "onReadyForSpeech: SpeechRecognizer is ready."); }
                    @Override
                    public void onBeginningOfSpeech() { Log.d(TAG, "onBeginningOfSpeech: User has started speaking."); }
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

                        if (speechRecognizer != null) {
                            speechRecognizer.cancel();
                        }

                        if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            // If timeout and it's not a goodbye sequence, prompt again.
                            // This might need more sophisticated state management if you have complex dialogues.
                            speak("I didn't hear anything. Please try again.");
                        } else if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                            speak("I didn't understand that. Can you please rephrase?");
                        } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                            speak("My speech recognition is busy. Please wait a moment and try again.");
                        }
                    }

                    @Override
                    public void onResults(Bundle results) {
                        Log.d(TAG, "onResults: Speech results received.");
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String userSpeech = matches.get(0);
                            Log.d(TAG, "User said: " + userSpeech);
                            processUserSpeech(userSpeech); // Call the new processing method
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

    // --- Core Logic: Processing User Speech (NOW USES ConversationManager) ---
    private void processUserSpeech(String speech) {
        String lowerCaseSpeech = speech.toLowerCase(Locale.US).trim(); // Trim whitespace

        // Get response from the conversation manager
        String response = conversationManager.getResponse(lowerCaseSpeech);
        Log.d(TAG, "Peanut's response: " + response);

        // Check if the response signals an end to the conversation
        if (conversationManager.isGoodbyeResponse(response)) {
            speak(response, UTTERANCE_ID_GOODBYE); // Use a unique ID that will NOT trigger listening
            stopSelfDelayed(2000); // Give time for goodbye to be spoken (adjust as needed)
        } else {
            speak(response); // Otherwise, speak the response, which will then trigger listening
        }
    }

    private void stopSelfDelayed(long delayMillis) {
        mainHandler.postDelayed(this::stopSelf, delayMillis);
    }

    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(PeanutService.this, message, Toast.LENGTH_SHORT).show());
    }
}
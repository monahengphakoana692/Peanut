// C:\Users\Retshepile Sehloho\AndroidStudioProjects\Peanut\app\src\main\java\com\example\peanut\PeanutService.java

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
    private ConversationManager conversationManager; // Instance of ConversationManager

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
                    // Initial prompt when conversation starts
                    speak(getString(R.string.listening_prompt), UTTERANCE_ID_LISTEN);
                } else {
                    Log.w(TAG, "TTS not initialized yet. Cannot start conversation immediately.");
                    showToast("Peanut's voice is not ready yet. Please try again in a moment.");
                }
            } else if (ACTION_STOP_SERVICE.equals(action)) {
                Log.d(TAG, "Received ACTION_STOP_SERVICE command.");
                speak("Goodbye! Stopping Peanut service.", UTTERANCE_ID_GOODBYE);
                stopSelfDelayed(2000); // Give TTS time to finish speaking
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

    // --- Foreground Notification Management ---
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

        // Make sure R.drawable.ic_stop_black_24dp exists or use a default Android icon
        int stopIconResId = R.drawable.ic_stop_black_24dp;
        try {
            getResources().getDrawable(stopIconResId, null);
        } catch (android.content.res.Resources.NotFoundException e) {
            Log.e(TAG, "Missing drawable: ic_stop_black_24dp. Using default Android stop icon.", e);
            stopIconResId = android.R.drawable.ic_media_pause; // Fallback icon
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

    // --- Text-to-Speech (TTS) Initialization and Speaking ---
    private void initializeTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TTS Language not supported or data missing during init. Result: " + result);
                    showToast("Peanut's voice language not supported.");
                    isTtsInitialized = false;
                    // Prompt user to install missing TTS data
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
                                // Only start listening again if it's a regular response or initial prompt,
                                // NOT if it's a goodbye message (which stops the service)
                                if (utteranceId != null &&
                                        (utteranceId.equals(UTTERANCE_ID_LISTEN) || utteranceId.equals(UTTERANCE_ID_RESPONSE))) {
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
                        // For older APIs, TTS might speak but we can't reliably know when to restart listening.
                        isTtsInitialized = false; // Mark as not fully initialized for precise control
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

        // Stop any ongoing speech before starting a new one
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

    // Overload for convenience, uses UTTERANCE_ID_RESPONSE by default
    private void speak(String text) {
        speak(text, UTTERANCE_ID_RESPONSE);
    }

    // --- SpeechRecognizer (STT) Initialization and Listener ---
    private void initializeSpeechRecognizer() {
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, this.getPackageName());
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1); // Get only the top result for simplicity

        // Configure silence detection
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L); // How long after speech ends to consider input complete
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L); // How long of total silence to wait before giving up

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

                        // Cancel current recognition
                        if (speechRecognizer != null) {
                            speechRecognizer.cancel();
                        }

                        // Determine if we should prompt again based on error and conversation state
                        if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT && !conversationManager.isAwaitingClarification()) {
                            // If it's a timeout and we're not waiting for specific info, prompt generally
                            speak("I didn't hear anything. Please try again.");
                        } else if (error == SpeechRecognizer.ERROR_NO_MATCH && !conversationManager.isAwaitingClarification()) {
                            // If no match and not waiting for specific info, prompt generally
                            speak("I didn't understand that. Can you please rephrase?");
                        } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                            // If busy, ask user to wait
                            speak("My speech recognition is busy. Please wait a moment and try again.");
                        } else if (error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT && error != SpeechRecognizer.ERROR_NO_MATCH) {
                            // For other errors (e.g., network, audio), just apologize and don't re-listen immediately
                            speak("I'm sorry, I encountered an error and cannot process your request right now.");
                        }
                        // If awaiting clarification, and it's a timeout or no_match, we still need to wait for clarification
                        // so don't speak a generic "didn't hear" as the response will be handled by the next `getResponse` call
                        // which would recognize it's still waiting.
                    }

                    @Override
                    public void onResults(Bundle results) {
                        Log.d(TAG, "onResults: Speech results received.");
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String userSpeech = matches.get(0);
                            Log.d(TAG, "User said: " + userSpeech);
                            handleUserSpeech(userSpeech); // Process the user's speech
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

    // Helper to get descriptive error text for SpeechRecognizer errors
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

    // --- Core Logic: Handling User Speech ---
    private void handleUserSpeech(String speech) {
        String lowerCaseSpeech = speech.toLowerCase(Locale.US).trim();

        // Get the response directly from the conversation manager.
        // The conversation manager will handle intent recognition, entity extraction,
        // and dialogue state (like awaiting clarification).
        String response = conversationManager.getResponse(lowerCaseSpeech);
        Log.d(TAG, "Peanut's initial response from CM: " + response);

        // Check if the ConversationManager has set itself to await clarification for weather
        if (conversationManager.isAwaitingClarification()) {
            speak(response, UTTERANCE_ID_RESPONSE); // Speak the prompt (e.g., "Which city?") and re-listen
            return; // Exit, waiting for the user's next input for clarification
        }

        // If the intent (after processing by getResponse) is GET_WEATHER and a location was successfully extracted,
        // then initiate the asynchronous weather fetch.
        // The `response` at this point will contain "Ok, fetching the weather for [city]." from ConversationManager.
        // Ensure `lastIntent` is accessed correctly (it is public now).
        // Ensure `entities` is accessed correctly (it is public now).
        if (conversationManager.lastIntent == ConversationManager.Intent.GET_WEATHER && conversationManager.entities.containsKey("location")) {
            String location = conversationManager.entities.get("location");
            if (location != null && !location.isEmpty()) {
                speak(response); // Speak the "fetching..." message
                conversationManager.fetchWeather(location, new ConversationManager.WeatherCallback() {
                    @Override
                    public void onWeatherResult(String weatherInfo) {
                        mainHandler.post(() -> { // Ensure TTS call is on UI thread
                            speak(weatherInfo, UTTERANCE_ID_RESPONSE); // Speak result, then re-listen
                        });
                    }

                    @Override
                    public void onWeatherError(String errorMessage) {
                        mainHandler.post(() -> { // Ensure TTS call is on UI thread
                            speak(errorMessage + " Is there anything else I can help with?", UTTERANCE_ID_RESPONSE); // Speak error, then re-listen
                        });
                    }
                });
                return; // Return early, as weather fetch is asynchronous. Speaking/listening handled in callback.
            }
        }

        // For all other intents, or if weather intent couldn't extract location
        // (which would lead to clarification being set, handled by the first `if` block above),
        // speak the direct response from the ConversationManager.
        if (conversationManager.isGoodbyeResponse(response)) {
            speak(response, UTTERANCE_ID_GOODBYE);
            stopSelfDelayed(2000); // Give time for goodbye to be spoken before stopping
        } else {
            speak(response); // Speak the response, which will then trigger listening
        }
    }

    // Utility to stop the service after a delay
    private void stopSelfDelayed(long delayMillis) {
        mainHandler.postDelayed(this::stopSelf, delayMillis);
    }

    // Utility to show a Toast message on the main thread
    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(PeanutService.this, message, Toast.LENGTH_SHORT).show());
    }
}
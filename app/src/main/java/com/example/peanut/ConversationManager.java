// C:\Users\Retshepile Sehloho\AndroidStudioProjects\Peanut\app\src\main\java\com\example\peanut\ConversationManager.java

package com.example.peanut;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConversationManager {

    private static final String TAG = "ConversationManager";
    private final Random random = new Random();

    // --- Conversational State ---
    private String userName = "there"; // Default name
    public Intent lastIntent = Intent.UNKNOWN; // Stores the last recognized intent
    public Map<String, String> entities = new HashMap<>(); // Stores extracted entities (e.g., "location" for weather)
    private boolean awaitingClarification = false; // Flag if Peanut is waiting for specific info
    private boolean askedForName = false; // To track if Peanut has asked for the user's name

    // Callback for Gemini responses to be sent back to PeanutService
    public interface ExternalAiResponseCallback {
        void onResponseReady(String response);
    }

    // --- Intent Enumeration ---
    public enum Intent {
        GREETING,
        HOW_ARE_YOU,
        NAME_INQUIRY,
        SET_MY_NAME,
        TELL_JOKE,
        GOODBYE,
        THANK_YOU,
        WHAT_TIME,
        GET_WEATHER,
        GET_WEATHER_CLARIFICATION,
        EXTERNAL_AI_QUERY, // NEW Intent for queries sent to an external AI
        UNKNOWN,
        SMALL_TALK,
        AFFIRMATION,
        NEGATION
    }

    // --- Response Banks for Variety --- (Keep the same as before for humanity improvements)
    private final List<String> greetings = Arrays.asList(
            "Hello there, %s! How can I assist you today?",
            "Hi, %s! It's great to hear from you. What's on your mind?",
            "Greetings, %s! Ready to help. What can I do?",
            "Hey %s! Good to connect. How can I be of service?"
    );

    private final List<String> howAreYouResponses = Arrays.asList(
            "I'm doing wonderfully, thank you for asking! And how are you feeling today?",
            "As an AI, I don't experience emotions, but I'm fully operational and ready to assist! How about yourself, %s?",
            "All my systems are running smoothly! Thanks for checking in. How's your day progressing, %s?",
            "I'm in top digital shape! What about you, %s? Anything I can do to make your day better?"
    );

    private final List<String> nameInquiryResponses = Arrays.asList(
            "My name is Peanut, and I'm here to help you. What name should I call you by?",
            "I'm Peanut, your digital assistant! It's lovely to meet you. And you are?",
            "You can simply call me Peanut. May I know your name, %s?",
            "I am Peanut. What's your name, if you don't mind me asking?"
    );

    private final List<String> setNameConfirmation = Arrays.asList(
            "It's a pleasure to finally meet you, %s! I'll remember that.",
            "Got it, %s! Nice to put a name to the voice. How can I help you?",
            "Hello, %s! I've updated your name in my memory.",
            "Wonderful, %s! Now that I know your name, what's next?"
    );

    private final List<String> jokeResponses = Arrays.asList(
            "Why don't scientists trust atoms? Because they make up everything! chuckled %s",
            "What do you call a fake noodle? An impasta! Haha, %s.",
            "Why did the scarecrow win an award? Because he was outstanding in his field!",
            "I told my wife she was drawing her eyebrows too high. She looked surprised. (Hope that made you smile, %s!)",
            "Why don't skeletons fight each other? They don't have the guts! Get it, %s?"
    );

    private final List<String> goodbyeResponses = Arrays.asList(
            "Goodbye, %s! It was a pleasure assisting you. Have a fantastic day!",
            "See you later, %s! Don't hesitate to call if you need anything.",
            "Farewell for now, %s! I'll be here when you return.",
            "Until next time, %s! Take care."
    );

    private final List<String> thankYouResponses = Arrays.asList(
            "You're absolutely welcome, %s! I'm always happy to help.",
            "Anytime, %s! It's what I'm here for.",
            "Glad I could assist, %s. Is there anything else on your mind?",
            "No problem at all, %s! Happy to be of service."
    );

    private final List<String> timeResponses = Arrays.asList(
            "The current time is %s. Hope that helps!",
            "It's %s right now. Anything else you'd like to know?",
            "Right now it's %s. Is there anything else I can tell you?"
    );

    private final List<String> weatherLocationPrompt = Arrays.asList(
            "I can tell you the weather, %s! Which city are you interested in?",
            "For weather information, I need a specific city. Where would you like to know about?",
            "Please tell me the city name for the weather forecast. I'm ready to look it up!"
    );

    private final List<String> understandingFailureResponses = Arrays.asList(
            "I'm not quite sure I grasped that, %s. Could you try rephrasing?",
            "My apologies, I didn't catch that clearly. Could you say it a different way, %s?",
            "Hmm, I'm a bit confused. Can you elaborate on what you mean, %s?",
            "I'm still learning, %s. Could you give me more context or be more specific?",
            "I think I missed something there. Can you tell me again, %s?",
            "I'm sorry, I don't understand that request. Perhaps you could ask in a different way?"
    );

    private final List<String> smallTalkResponses = Arrays.asList(
            "That's interesting. What else is on your mind, %s?",
            "I see. Is there anything specific you'd like me to do or discuss, %s?",
            "Okay, %s. I'm here if you have more questions.",
            "I'm always ready for a new task, %s. What would you like to do?",
            "Thinking about anything exciting, %s?",
            "Tell me more, %s! Or perhaps you have a question for me?"
    );

    private final List<String> affirmationResponses = Arrays.asList(
            "Great!",
            "Alright then!",
            "Understood!",
            "Perfect!"
    );

    private final List<String> negationResponses = Arrays.asList(
            "Okay, no problem.",
            "Understood. Anything else?",
            "Alright. How can I help then?",
            "No worries."
    );

    // --- Constructor ---
    public ConversationManager() {
        GeminiApiClient.initialize(); // Initialize Gemini client when ConversationManager is created
    }

    /**
     * Processes user input and generates a conversational response based on intent and context.
     * For UNKNOWN intents, it delegates to an external AI and returns an "awaiting" message.
     *
     * @param userInput The lowercased, trimmed speech from the user.
     * @param callback Optional callback for asynchronous responses (e.g., from external AI or weather API).
     * @return An immediate response string. If an async operation is triggered, this string
     * will be an intermediate message (e.g., "thinking...") and the final response
     * will come via the callback.
     */
    public String getResponse(String userInput, ExternalAiResponseCallback callback) {
        // Reset entities and clarification status at the beginning of each turn
        entities.clear();
        boolean wasAwaitingClarification = awaitingClarification;
        awaitingClarification = false;

        Intent currentIntent = recognizeIntent(userInput);

        String immediateResponse = "";

        // --- Dialogue Management: Handle Clarification first ---
        if (wasAwaitingClarification && lastIntent == Intent.GET_WEATHER_CLARIFICATION) {
            if (extractLocationFromFallback(userInput)) {
                currentIntent = Intent.GET_WEATHER; // Now we have the location, proceed
            } else {
                immediateResponse = String.format(randomChoice(understandingFailureResponses), userName) + " I'm still waiting for the city name for the weather.";
                awaitingClarification = true;
                lastIntent = Intent.GET_WEATHER_CLARIFICATION;
                return immediateResponse; // Exit early
            }
        }

        // --- Intent-based Response Generation ---
        switch (currentIntent) {
            case GREETING:
                immediateResponse = String.format(randomChoice(greetings), userName);
                if (!askedForName && userName.equals("there")) {
                    immediateResponse += " By the way, what name should I use to call you?";
                    askedForName = true;
                }
                break;
            case HOW_ARE_YOU:
                immediateResponse = String.format(randomChoice(howAreYouResponses), userName);
                break;
            case NAME_INQUIRY:
                immediateResponse = String.format(randomChoice(nameInquiryResponses), userName);
                askedForName = true;
                break;
            case SET_MY_NAME:
                immediateResponse = String.format(randomChoice(setNameConfirmation), userName);
                askedForName = true;
                break;
            case TELL_JOKE:
                immediateResponse = String.format(randomChoice(jokeResponses), userName);
                immediateResponse += " Did that make you smile? What else can I do for you?";
                break;
            case GOODBYE:
                immediateResponse = String.format(randomChoice(goodbyeResponses), userName);
                break;
            case THANK_YOU:
                immediateResponse = String.format(randomChoice(thankYouResponses), userName);
                break;
            case WHAT_TIME:
                immediateResponse = String.format(randomChoice(timeResponses), getCurrentTime());
                break;
            case GET_WEATHER:
                String location = entities.get("location");
                if (location != null && !location.isEmpty()) {
                    immediateResponse = "Ok, fetching the weather for " + location + ".";
                    // Trigger actual weather fetch asynchronously
                    fetchWeather(location, new WeatherCallback() {
                        @Override
                        public void onWeatherResult(String weatherInfo) {
                            callback.onResponseReady(weatherInfo);
                        }

                        @Override
                        public void onWeatherError(String errorMessage) {
                            callback.onResponseReady(errorMessage + " Is there anything else I can help with?");
                        }
                    });
                } else {
                    immediateResponse = String.format(randomChoice(weatherLocationPrompt), userName);
                    awaitingClarification = true;
                    currentIntent = Intent.GET_WEATHER_CLARIFICATION;
                }
                break;
            case AFFIRMATION:
                immediateResponse = randomChoice(affirmationResponses);
                if (lastIntent == Intent.TELL_JOKE) {
                    immediateResponse += " Glad to hear it! Anything else?";
                } else {
                    immediateResponse += " How can I proceed?";
                }
                break;
            case NEGATION:
                immediateResponse = randomChoice(negationResponses);
                immediateResponse += " What would you like to do instead?";
                break;
            case SMALL_TALK:
                immediateResponse = String.format(randomChoice(smallTalkResponses), userName);
                break;
            case UNKNOWN:
            default:
                // When intent is UNKNOWN, delegate to Gemini
                immediateResponse = "Hmm, let me think about that for a moment..."; // Immediate response while Gemini processes
                lastIntent = Intent.EXTERNAL_AI_QUERY; // Set intent to signify awaiting external AI
                Log.d(TAG, "Delegating UNKNOWN query to Gemini: " + userInput);

                // Call Gemini API asynchronously
                GeminiApiClient.generateTextFromInput(userInput, new GeminiApiClient.GeminiResponseCallback() {
                    @Override
                    public void onGeminiResponse(String response) {
                        // Pass the Gemini's response back via the callback
                        callback.onResponseReady(response);
                        // After Gemini responds, reset lastIntent to UNKNOWN or SMALL_TALK if no specific follow-up needed
                        lastIntent = Intent.SMALL_TALK; // Or another appropriate post-Gemini state
                    }

                    @Override
                    public void onGeminiError(String error) {
                        // Pass the error message back via the callback
                        callback.onResponseReady(error);
                        lastIntent = Intent.UNKNOWN; // Remain in UNKNOWN state
                    }
                });
                break;
        }

        // Only update lastIntent if it's not an EXTERNAL_AI_QUERY,
        // as EXTERNAL_AI_QUERY state will be managed by its callback
        if (currentIntent != Intent.UNKNOWN || lastIntent != Intent.EXTERNAL_AI_QUERY) {
            lastIntent = currentIntent;
        }

        return immediateResponse; // Return the immediate response
    }

    /**
     * Determines the user's intent from their input.
     * (Keep this method the same as the previous "humanity" update)
     */
    public Intent recognizeIntent(String userInput) {
        if (userInput.matches(".*\\b(hello|hi|hey|greetings|good morning|good afternoon|good evening)\\b.*")) {
            return Intent.GREETING;
        }
        if (userInput.matches(".*\\b(how are you|how you doing|how's it going)\\b.*")) {
            return Intent.HOW_ARE_YOU;
        }
        if (userInput.matches(".*\\b(what is your name|who are you|your name|what do you call yourself)\\b.*")) {
            return Intent.NAME_INQUIRY;
        }
        if (userInput.matches(".*\\b(my name is|i am called|you can call me|i'm)\\s+([a-zA-Z]+).*")) {
            Pattern pattern = Pattern.compile("my name is\\s+([a-zA-Z]+)|i am called\\s+([a-zA-Z]+)|you can call me\\s+([a-zA-Z]+)|i'm\\s+([a-zA-Z]+)");
            Matcher matcher = pattern.matcher(userInput);
            if (matcher.find()) {
                String name = null;
                for (int i = 1; i <= matcher.groupCount(); i++) {
                    if (matcher.group(i) != null) {
                        name = matcher.group(i);
                        break;
                    }
                }
                if (name != null) {
                    this.userName = name.trim();
                    Log.d(TAG, "Extracted user name: " + userName);
                }
            }
            return Intent.SET_MY_NAME;
        }
        if (userInput.matches(".*\\b(tell me a joke|joke please|make me laugh|tell a funny story)\\b.*")) {
            return Intent.TELL_JOKE;
        }
        if (userInput.matches(".*\\b(goodbye|bye|see you later|farewell|i'm leaving|i'm done|exit)\\b.*")) {
            return Intent.GOODBYE;
        }
        if (userInput.matches(".*\\b(thank you|thanks|i appreciate it|cheers)\\b.*")) {
            return Intent.THANK_YOU;
        }
        if (userInput.matches(".*\\b(what time is it|current time|time now|do you know the time)\\b.*")) {
            return Intent.WHAT_TIME;
        }
        if (userInput.matches(".*\\b(weather|forecast|how's the weather|temperature)\\b.*")) {
            extractWeatherLocation(userInput);
            return Intent.GET_WEATHER;
        }
        if (userInput.matches(".*\\b(yes|yeah|yep|okay|sure|alright|fine)\\b.*")) {
            return Intent.AFFIRMATION;
        }
        if (userInput.matches(".*\\b(no|nope|not really|nah)\\b.*")) {
            return Intent.NEGATION;
        }
        if (userInput.matches(".*\\b(okay|alright|right|hmm|what about|tell me more|interesting)\\b.*")) {
            return Intent.SMALL_TALK;
        }
        return Intent.UNKNOWN;
    }

    private void extractWeatherLocation(String userInput) {
        Pattern pattern = Pattern.compile("weather (in|for|of|at)\\s+([a-zA-Z\\s]+)|([a-zA-Z\\s]+) weather");
        Matcher matcher = pattern.matcher(userInput);
        if (matcher.find()) {
            String location = matcher.group(2);
            if (location == null) {
                location = matcher.group(3);
            }
            if (location != null) {
                entities.put("location", location.trim());
                Log.d(TAG, "Extracted location from weather intent: " + location.trim());
            }
        }
    }

    private boolean extractLocationFromFallback(String userInput) {
        if (userInput.length() > 2 && recognizeIntent(userInput) == Intent.UNKNOWN) {
            if (userInput.matches("[a-zA-Z\\s]+")) {
                entities.put("location", userInput.trim());
                Log.d(TAG, "Extracted fallback location during clarification: " + userInput.trim());
                return true;
            }
        }
        return false;
    }

    private String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.US);
        return sdf.format(new Date());
    }

    public interface WeatherCallback {
        void onWeatherResult(String weatherInfo);
        void onWeatherError(String errorMessage);
    }

    public void fetchWeather(String location, WeatherCallback callback) {
        Log.d(TAG, "Simulating weather fetch for: " + location);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (location.toLowerCase(Locale.US).contains("maseru")) {
                callback.onWeatherResult("The weather in Maseru is currently clear with a temperature of 10 degrees Celsius. Perfect for a cool evening!");
            } else if (location.toLowerCase(Locale.US).contains("london")) {
                callback.onWeatherResult("The weather in London is cloudy with a temperature of 15 degrees Celsius. Don't forget your umbrella!");
            } else if (location.toLowerCase(Locale.US).contains("new york")) {
                callback.onWeatherResult("The weather in New York is partly cloudy with a temperature of 22 degrees Celsius. A pleasant day!");
            }
            else {
                callback.onWeatherError("I couldn't find the weather for " + location + ". My apologies!");
            }
        }, 1500);
    }

    private String randomChoice(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "I don't have a response for that right now.";
        }
        return list.get(random.nextInt(list.size()));
    }

    public void resetConversation() {
        userName = "there";
        lastIntent = Intent.UNKNOWN;
        entities.clear();
        awaitingClarification = false;
        askedForName = false;
        Log.d(TAG, "Conversation state reset.");
    }

    public boolean isGoodbyeResponse(String response) {
        return lastIntent == Intent.GOODBYE;
    }

    public boolean isAwaitingClarification() {
        return awaitingClarification;
    }
}
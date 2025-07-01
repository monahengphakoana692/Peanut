// com.example.peanut/ConversationManager.java (Corrected Access Modifier)

package com.example.peanut;

import android.os.Handler; // Add this import
import android.os.Looper; // Add this import
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
    public Map<String, String> entities = new HashMap<>(); // Public to allow PeanutService to read extracted entities
    private boolean awaitingClarification = false; // Flag if Peanut is waiting for specific info

    // --- Intent Enumeration ---
    // Represents the user's goal
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
        GET_WEATHER_CLARIFICATION, // For when location is missing
        UNKNOWN,
        SMALL_TALK // General conversational filler or non-specific chatter
    }

    // --- Response Banks for Variety (unchanged) ---
    private final List<String> greetings = Arrays.asList(
            "Hello! How can I assist you today, %s?",
            "Hi there, %s! What's on your mind?",
            "Greetings, %s! How may I help you?",
            "Hey, %s! It's good to hear from you."
    );

    private final List<String> howAreYouResponses = Arrays.asList(
            "I'm doing great, thank you for asking! How are you doing today?",
            "As an AI, I don't have feelings, but I'm ready to help! What about you?",
            "I am functioning optimally! And yourself, %s?",
            "All systems nominal! How's your day going, %s?"
    );

    private final List<String> nameInquiryResponses = Arrays.asList(
            "My name is Peanut. Nice to meet you, %s.",
            "You can call me Peanut, %s. What's your name?",
            "I am Peanut. What can I do for you today, %s?"
    );

    private final List<String> jokeResponses = Arrays.asList(
            "Why don't scientists trust atoms? Because they make up everything!",
            "What do you call a fake noodle? An impasta!",
            "Why did the scarecrow win an award? Because he was outstanding in his field!",
            "I told my wife she was drawing her eyebrows too high. She looked surprised.",
            "Why don't skeletons fight each other? They don't have the guts!"
    );

    private final List<String> goodbyeResponses = Arrays.asList(
            "Goodbye! Have a great day, %s!",
            "See you later, %s!",
            "Farewell for now, %s! Come back anytime.",
            "Until next time, %s!"
    );

    private final List<String> thankYouResponses = Arrays.asList(
            "You're welcome, %s! Happy to help.",
            "Anytime, %s!",
            "Glad I could assist, %s.",
            "No problem, %s!"
    );

    private final List<String> timeResponses = Arrays.asList(
            "The current time is %s.",
            "It's %s right now.",
            "Right now it's %s."
    );

    private final List<String> weatherLocationPrompt = Arrays.asList(
            "Which city would you like the weather for, %s?",
            "Please tell me the city for the weather forecast.",
            "I need a city name to tell you the weather. Where are you thinking of, %s?"
    );

    private final List<String> understandingFailureResponses = Arrays.asList(
            "I'm not quite sure I understand. Could you please rephrase that, %s?",
            "My apologies, I didn't catch that. Could you say it differently, %s?",
            "Hmm, I'm a bit confused. Can you explain what you mean, %s?",
            "Could you elaborate on that, please, %s?",
            "I'm still learning. Can you give me more context, %s?"
    );

    private final List<String> smallTalkResponses = Arrays.asList(
            "That's interesting. What else is on your mind?",
            "I see. Anything else you wanted to talk about, %s?",
            "Okay, %s. What's next on your agenda?",
            "Do you have any other questions for me, %s?",
            "I'm here if you need anything else, %s.",
            "Is there something specific you'd like to do or discuss?"
    );

    // --- Constructor ---
    public ConversationManager() {
        // You could initialize more complex models or data here if needed
    }

    // --- Public Interface for PeanutService ---

    /**
     * Processes user input and generates a conversational response based on intent and context.
     *
     * @param userInput The lowercased, trimmed speech from the user.
     * @return A generated response, potentially incorporating context and external data.
     */
    public String getResponse(String userInput) {
        // Reset entities and clarification status at the beginning of each turn
        entities.clear();
        awaitingClarification = false;

        Intent currentIntent = recognizeIntent(userInput); // This will populate 'entities' and set 'userName' if applicable
        // The call to extractEntities is now handled inside recognizeIntent if needed,
        // or more precisely, getResponse directly utilizes the extracted entities if the intent requires it.
        // For example, SET_MY_NAME modifies userName directly from within recognizeIntent.

        String response = "";

        // --- Dialogue Management: Handle Clarification first ---
        // If we were awaiting a location and the user provided something that looks like a location.
        if (lastIntent == Intent.GET_WEATHER_CLARIFICATION && currentIntent == Intent.UNKNOWN && extractLocationFromFallback(userInput)) {
            // User provided location in response to clarification prompt
            currentIntent = Intent.GET_WEATHER; // Now we have the location, proceed with GET_WEATHER intent
            awaitingClarification = false;
        } else if (lastIntent == Intent.GET_WEATHER_CLARIFICATION && currentIntent == Intent.UNKNOWN && !entities.containsKey("location")) {
            // User did not provide a location or a recognized intent while clarification was awaited
            response = String.format(randomChoice(understandingFailureResponses), userName) + " I'm still waiting for the city name for the weather.";
            awaitingClarification = true; // Keep awaiting
            lastIntent = Intent.GET_WEATHER_CLARIFICATION; // Maintain clarification state
            return response; // Exit early to wait for a valid location
        }


        // --- Intent-based Response Generation ---
        switch (currentIntent) {
            case GREETING:
                response = String.format(randomChoice(greetings), userName);
                break;
            case HOW_ARE_YOU:
                response = String.format(randomChoice(howAreYouResponses), userName);
                break;
            case NAME_INQUIRY:
                response = String.format(randomChoice(nameInquiryResponses), userName);
                break;
            case SET_MY_NAME:
                // Name extraction already handled in recognizeIntent
                response = "It's a pleasure to meet you, " + userName + "! How can I help you today?";
                break;
            case TELL_JOKE:
                response = randomChoice(jokeResponses);
                break;
            case GOODBYE:
                response = String.format(randomChoice(goodbyeResponses), userName);
                break;
            case THANK_YOU:
                response = String.format(randomChoice(thankYouResponses), userName);
                break;
            case WHAT_TIME:
                response = String.format(randomChoice(timeResponses), getCurrentTime());
                break;
            case GET_WEATHER:
                String location = entities.get("location");
                if (location != null && !location.isEmpty()) {
                    // This is where you would call your weather API.
                    // For now, it's a placeholder. The actual fetch will be done by PeanutService.
                    response = "Ok, fetching the weather for " + location + ".";
                } else {
                    response = String.format(randomChoice(weatherLocationPrompt), userName);
                    awaitingClarification = true; // Set flag to expect a location next
                    currentIntent = Intent.GET_WEATHER_CLARIFICATION; // Update last intent for context
                }
                break;
            case SMALL_TALK: // If a general phrase, use a small talk response
                response = String.format(randomChoice(smallTalkResponses), userName);
                break;
            case UNKNOWN:
            default:
                response = String.format(randomChoice(understandingFailureResponses), userName);
                break;
        }

        // Update the last recognized intent for the next turn
        lastIntent = currentIntent;

        return response;
    }

    /**
     * Determines the user's intent from their input.
     * This is a rule-based NLU for now, expandable to ML models.
     * Public because PeanutService needs to check the intent before calling getResponse in some cases (like async weather).
     */
    public Intent recognizeIntent(String userInput) { // Changed from private to public
        if (userInput.matches(".*\\b(hello|hi|hey|greetings)\\b.*")) {
            return Intent.GREETING;
        }
        if (userInput.matches(".*\\b(how are you|how you doing)\\b.*")) {
            return Intent.HOW_ARE_YOU;
        }
        if (userInput.matches(".*\\b(what is your name|who are you|your name)\\b.*")) {
            return Intent.NAME_INQUIRY;
        }
        if (userInput.matches(".*\\b(my name is|i am called)\\s+([a-zA-Z]+).*")) {
            // Extract name immediately when this intent is recognized
            Pattern pattern = Pattern.compile("my name is\\s+([a-zA-Z]+)|i am called\\s+([a-zA-Z]+)");
            Matcher matcher = pattern.matcher(userInput);
            if (matcher.find()) {
                String name = matcher.group(1);
                if (name == null) {
                    name = matcher.group(2);
                }
                if (name != null) {
                    this.userName = name.trim();
                    Log.d(TAG, "Extracted user name: " + userName);
                }
            }
            return Intent.SET_MY_NAME;
        }
        if (userInput.matches(".*\\b(tell me a joke|joke please|make me laugh)\\b.*")) {
            return Intent.TELL_JOKE;
        }
        if (userInput.matches(".*\\b(goodbye|bye|see you later|farewell)\\b.*")) {
            return Intent.GOODBYE;
        }
        if (userInput.matches(".*\\b(thank you|thanks|i appreciate it)\\b.*")) {
            return Intent.THANK_YOU;
        }
        if (userInput.matches(".*\\b(what time is it|current time|time now)\\b.*")) {
            return Intent.WHAT_TIME;
        }
        if (userInput.matches(".*\\b(weather|forecast|how's the weather)\\b.*")) {
            // Extract location when weather intent is recognized
            extractWeatherLocation(userInput);
            return Intent.GET_WEATHER;
        }
        // General small talk/acknowledgements that don't fit specific intents
        if (userInput.matches(".*\\b(okay|alright|right|hmm|tell me more|what about)\\b.*")) {
            return Intent.SMALL_TALK;
        }
        return Intent.UNKNOWN;
    }

    /**
     * Extracts entities (like location) from the user input for specific intents.
     * This is private as it's called internally by recognizeIntent or getResponse.
     */
    private void extractWeatherLocation(String userInput) {
        // Example: "weather in London", "weather for Paris"
        Pattern pattern = Pattern.compile("weather (in|for|of|at)\\s+([a-zA-Z\\s]+)");
        Matcher matcher = pattern.matcher(userInput);
        if (matcher.find()) {
            String location = matcher.group(2).trim();
            entities.put("location", location);
            Log.d(TAG, "Extracted location from weather intent: " + location);
        }
        // Add more patterns if user might just say "London weather"
        // e.g., Pattern.compile("([a-zA-Z\\s]+) weather"); might be too broad
    }

    /**
     * Attempts to extract a location when Peanut is awaiting clarification.
     * This handles cases where the user just says the city name.
     */
    private boolean extractLocationFromFallback(String userInput) {
        // Simple check: if the input is not a recognized intent and not very short,
        // assume it's a location if we're awaiting one.
        // This is a heuristic and can be improved with a list of known cities or more advanced NLU.
        if (lastIntent == Intent.GET_WEATHER_CLARIFICATION && !recognizeIntent(userInput).equals(Intent.UNKNOWN) && userInput.length() > 2) {
            // If user says something like "weather in london" after clarification was requested,
            // then recognizeIntent already handled it. This branch handles just the city name.
            // We need to re-extract location if it's a new input.
            extractWeatherLocation(userInput); // Try to extract if it's a full weather phrase
            if(!entities.containsKey("location")) { // If not extracted by a full phrase, assume the whole input is the city
                entities.put("location", userInput);
            }
            Log.d(TAG, "Extracted fallback location: " + userInput);
            return true;
        } else if (lastIntent == Intent.GET_WEATHER_CLARIFICATION && recognizeIntent(userInput).equals(Intent.UNKNOWN) && userInput.length() > 2) {
            // If it's a general unknown input, and we're awaiting clarification, assume it's the location
            entities.put("location", userInput);
            Log.d(TAG, "Extracted raw user input as location: " + userInput);
            return true;
        }
        return false;
    }


    // --- External Knowledge Retrieval (Simulated/Direct) ---

    private String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.US); // e.g., "9:03 PM"
        return sdf.format(new Date());
    }

    // Placeholder for actual weather API call
    // This would typically involve a network request and callbacks in a real app.
    public interface WeatherCallback {
        void onWeatherResult(String weatherInfo);
        void onWeatherError(String errorMessage);
    }

    public void fetchWeather(String location, WeatherCallback callback) {
        // In a real application, this would be a network request.
        // For demonstration, let's simulate a delay and a response.
        Log.d(TAG, "Simulating weather fetch for: " + location);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (location.toLowerCase(Locale.US).contains("maseru")) {
                callback.onWeatherResult("The weather in Maseru is currently sunny with a temperature of 25 degrees Celsius.");
            } else if (location.toLowerCase(Locale.US).contains("london")) {
                callback.onWeatherResult("The weather in London is cloudy with a temperature of 15 degrees Celsius.");
            } else if (location.toLowerCase(Locale.US).contains("new york")) {
                callback.onWeatherResult("The weather in New York is partly cloudy with a temperature of 22 degrees Celsius.");
            }
            else {
                callback.onWeatherError("I couldn't find the weather for " + location + ". Please try a different city.");
            }
        }, 1500); // Simulate network delay
    }


    // --- Helper Methods ---

    /**
     * Helper to pick a random string from a list.
     */
    private String randomChoice(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "I don't have a response for that right now.";
        }
        return list.get(random.nextInt(list.size()));
    }

    /**
     * Resets any conversational state, useful when the service restarts or a new conversation begins.
     */
    public void resetConversation() {
        userName = "there";
        lastIntent = Intent.UNKNOWN;
        entities.clear();
        awaitingClarification = false;
        Log.d(TAG, "Conversation state reset.");
    }

    /**
     * Check if the response implies stopping the conversation.
     *
     * @param response The generated response.
     * @return True if the response indicates a stop (e.g., "Goodbye!").
     */
    public boolean isGoodbyeResponse(String response) {
        return lastIntent == Intent.GOODBYE; // Simpler check using the recognized intent
    }

    /**
     * Checks if Peanut is currently waiting for a specific piece of information from the user.
     * @return true if clarification is awaited, false otherwise.
     */
    public boolean isAwaitingClarification() {
        return awaitingClarification;
    }
}
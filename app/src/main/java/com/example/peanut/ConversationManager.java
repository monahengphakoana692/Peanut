// com.example.peanut/ConversationManager.java

package com.example.peanut;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ConversationManager {

    private final Random random = new Random();
    private String userName = "there"; // Default name
    private String lastTopic = ""; // Simple state for context

    // Response banks for variety
    private final List<String> greetings = Arrays.asList(
            "Hello! How can I assist you today?",
            "Hi there! What's on your mind?",
            "Greetings! How may I help you?",
            "Hey! It's good to hear from you."
    );

    private final List<String> howAreYouResponses = Arrays.asList(
            "I'm doing great, thank you for asking! How are you?",
            "As an AI, I don't have feelings, but I'm ready to help! What about you?",
            "I am functioning optimally! And yourself?",
            "All systems nominal! How's your day going?"
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
            "I told my wife she was drawing her eyebrows too high. She looked surprised."
    );

    private final List<String> goodbyeResponses = Arrays.asList(
            "Goodbye! Have a great day, %s!",
            "See you later, %s!",
            "Farewell for now, %s! Come back anytime.",
            "Until next time, %s!"
    );

    private final List<String> understandingFailureResponses = Arrays.asList(
            "I'm not quite sure I understand. Could you please rephrase that?",
            "My apologies, I didn't catch that. Could you say it differently?",
            "Hmm, I'm a bit confused. Can you explain what you mean?",
            "Could you elaborate on that, please?",
            "I'm still learning. Can you give me more context?"
    );

    private final List<String> conversationalPrompts = Arrays.asList(
            "Is there anything else you wanted to talk about?",
            "What's next on your agenda?",
            "Do you have any other questions for me?",
            "Anything else I can assist you with, %s?",
            "I'm here if you need anything else."
    );

    // Simple keyword mapping (can be expanded significantly)
    private final Map<String, String> keywordResponses = new HashMap<>();

    public ConversationManager() {
        // Initialize keyword responses
        keywordResponses.put("weather", "I can tell you about the weather if you specify a location!");
        keywordResponses.put("time", "I don't have a clock myself, but I can help you find out the current time if you have an app for that!");
        keywordResponses.put("news", "I can't browse the news directly, but I can connect you to an app that does.");
        // Add more general knowledge or domain-specific responses
    }

    /**
     * Processes user input and generates a conversational response.
     * @param userInput The lowercased, trimmed speech from the user.
     * @return A generated response, potentially incorporating context.
     */
    public String getResponse(String userInput) {
        String response = "";
        boolean understood = false;

        // 1. Check for immediate actions/commands (like setting name, jokes, goodbyes)
        if (userInput.contains("my name is") || userInput.contains("i am called")) {
            String[] parts = userInput.split("my name is|i am called");
            if (parts.length > 1) {
                userName = parts[1].trim().split(" ")[0]; // Take the first word after "my name is"
                response = "It's a pleasure to meet you, " + userName + "! How can I help you today?";
                understood = true;
                lastTopic = "introduction";
            }
        } else if (userInput.contains("hello") || userInput.contains("hi")) {
            response = String.format(randomChoice(greetings), userName);
            understood = true;
            lastTopic = "greeting";
        } else if (userInput.contains("how are you")) {
            response = randomChoice(howAreYouResponses);
            understood = true;
            lastTopic = "well-being";
        } else if (userInput.contains("what is your name")) {
            response = String.format(randomChoice(nameInquiryResponses), userName);
            understood = true;
            lastTopic = "self-introduction";
        } else if (userInput.contains("tell me a joke") || userInput.contains("joke please")) {
            response = randomChoice(jokeResponses);
            understood = true;
            lastTopic = "entertainment";
        } else if (userInput.contains("goodbye") || userInput.contains("bye")) {
            // Special handling for goodbye, as service stops after this.
            // The service will handle the actual stop.
            response = String.format(randomChoice(goodbyeResponses), userName);
            understood = true;
            lastTopic = "goodbye";
        } else if (userInput.contains("thank you") || userInput.contains("thanks")) {
            response = "You're welcome, " + userName + "! Happy to help.";
            understood = true;
            lastTopic = "gratitude";
        }
        // Add more conversational phrases here
        else if (userInput.contains("how old are you")) {
            response = "I don't have an age in the human sense. I was just created!";
            understood = true;
            lastTopic = "personal_info";
        } else if (userInput.contains("where are you from")) {
            response = "I exist in the digital realm, but my code was written by a human.";
            understood = true;
            lastTopic = "origin";
        }


        // 2. Keyword-based responses (if not an immediate action)
        if (!understood) {
            for (Map.Entry<String, String> entry : keywordResponses.entrySet()) {
                if (userInput.contains(entry.getKey())) {
                    response = entry.getValue();
                    understood = true;
                    lastTopic = entry.getKey(); // Set topic to the matched keyword
                    break;
                }
            }
        }

        // 3. Simple Contextual Response (e.g., follow-up questions)
        if (!understood && lastTopic.equals("joke") && (userInput.contains("another") || userInput.contains("more"))) {
            response = randomChoice(jokeResponses);
            understood = true;
        }
        // More complex contextual logic can be built here.
        // E.g., if last topic was "weather" and user says "how about tomorrow?"

        // 4. Fallback if still not understood
        if (!understood) {
            response = randomChoice(understandingFailureResponses);
            lastTopic = "misunderstanding"; // Update topic for potential follow-up
        }

        // 5. Add a conversational prompt sometimes
        if (understood && random.nextFloat() < 0.3) { // 30% chance to add a prompt
            response += " " + String.format(randomChoice(conversationalPrompts), userName);
        }

        return response;
    }

    /**
     * Helper to pick a random string from a list.
     */
    private String randomChoice(List<String> list) {
        return list.get(random.nextInt(list.size()));
    }

    /**
     * Resets any conversational state, useful when the service restarts or a new conversation begins.
     */
    public void resetConversation() {
        userName = "there";
        lastTopic = "";
    }

    /**
     * Check if the response implies stopping the conversation.
     * @param response The generated response.
     * @return True if the response indicates a stop (e.g., "Goodbye!").
     */
    public boolean isGoodbyeResponse(String response) {
        // A simple heuristic: check if the response starts with a goodbye phrase.
        // This is coupled to your goodbyeResponses list.
        for (String gb : goodbyeResponses) {
            // Consider variations: "Goodbye! Have a great day, [name]!"
            // Just check the start of the response.
            if (response.startsWith(gb.split("!")[0])) { // Check prefix before variable part
                return true;
            }
        }
        return false;
    }
}
// C:\Users\Retshepile Sehloho\AndroidStudioProjects\Peanut\app\src\main\java\com\example\peanut\GeminiApiClient.java

package com.example.peanut;

import android.util.Log;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content; // <--- ADD THIS IMPORT
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GeminiApiClient {

    private static final String TAG = "GeminiApiClient";
    private static GenerativeModelFutures model;
    private static final Executor executor = Executors.newSingleThreadExecutor(); // For async operations

    // Callback interface for sending the Gemini response back
    public interface GeminiResponseCallback {
        void onGeminiResponse(String response);
        void onGeminiError(String error);
    }

    // Initialize the Gemini Model with the API key from BuildConfig
    public static void initialize() {
        if (model == null) {
            try {
                // Access the API key from BuildConfig (ensure you put it in local.properties)
                String apiKey = BuildConfig.GEMINI_API_KEY;
                if (apiKey == null || apiKey.equals("YOUR_FALLBACK_KEY_IF_NOT_FOUND") || apiKey.isEmpty()) {
                    Log.e(TAG, "Gemini API key is not configured in local.properties or build.gradle.");
                    Log.e(TAG, "Please regenerate your API key and set GEMINI_API_KEY=YOUR_NEW_API_KEY in your local.properties file.");
                    return;
                }
                model = GenerativeModelFutures.from(
                        new GenerativeModel("gemini-pro", apiKey) // Use "gemini-pro" for general chat
                );
                Log.d(TAG, "Gemini GenerativeModel initialized.");
            } catch (Exception e) {
                Log.e(TAG, "Error initializing Gemini model: " + e.getMessage());
                // Handle initialization error, e.g., show a toast or log
            }
        }
    }

    // Method to send a text query to Gemini and get a response
    public static void generateTextFromInput(String prompt, final GeminiResponseCallback callback) {
        if (model == null) {
            callback.onGeminiError("Gemini model not initialized. Check API key configuration.");
            return;
        }

        Log.d(TAG, "Sending prompt to Gemini: " + prompt);

        // --- MODIFIED LINE HERE ---
        ListenableFuture<GenerateContentResponse> responseFuture =
                model.generateContent(new Content.Builder().addText(prompt).build());
        // --------------------------

        // Add a listener to handle the asynchronous response
        responseFuture.addListener(() -> {
            try {
                GenerateContentResponse response = responseFuture.get(); // Get the actual response
                String generatedText = response.getText();
                if (generatedText != null && !generatedText.isEmpty()) {
                    Log.d(TAG, "Gemini response: " + generatedText);
                    callback.onGeminiResponse(generatedText);
                } else {
                    Log.w(TAG, "Gemini returned an empty response.");
                    callback.onGeminiResponse("I'm sorry, I couldn't generate a clear response from my knowledge base.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting Gemini response: " + e.getMessage(), e);
                // Handle various exceptions, e.g., network issues, API errors
                callback.onGeminiError("I'm sorry, I encountered an error trying to process that. Please try again.");
            }
        }, executor); // Execute the listener on the defined executor (background thread)
    }
}
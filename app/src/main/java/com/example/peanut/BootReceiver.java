package com.example.peanut;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.widget.Toast; // Consider removing Toast in a real boot receiver, as UI might not be ready

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "PeanutBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Boot completed broadcast received. Starting PeanutService.");
            // Toast is generally not recommended in BootReceivers as UI might not be ready
            Toast.makeText(context, "Peanut is starting up...", Toast.LENGTH_LONG).show();

            // Start the PeanutService as a foreground service
            Intent serviceIntent = new Intent(context, PeanutService.class);
            // We use a custom action here to differentiate from other service starts if needed
            serviceIntent.setAction(PeanutService.ACTION_START_SERVICE_ON_BOOT);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // For Android 8.0 (API 26) and above, use startForegroundService()
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}
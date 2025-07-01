package com.example.peanut;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.imageview.ShapeableImageView;
import java.util.ArrayList; // <--- Make sure this line is present!
// import java.util.Locale; /

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1; // Combined request code for simplicity
    private static final String TAG = "PeanutMainActivity";

    private ShapeableImageView peanutCircle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        peanutCircle = findViewById(R.id.peanutCircle);

        // 1. Request necessary permissions at Runtime
        checkAndRequestPermissions();

        // 2. Set OnClickListener for the circular image
        peanutCircle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check if all critical permissions are granted before trying to start service
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                                ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)) {

                    Toast.makeText(MainActivity.this, "Peanut: Starting service and initiating conversation...", Toast.LENGTH_SHORT).show();
                    // Start PeanutService and tell it to initiate conversation
                    Intent serviceIntent = new Intent(MainActivity.this, PeanutService.class);
                    serviceIntent.setAction(PeanutService.ACTION_START_CONVERSATION);

                    // Use startForegroundService for Android O (API 26) and above
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(MainActivity.this, serviceIntent);
                    } else {
                        startService(serviceIntent);
                    }

                } else {
                    Toast.makeText(MainActivity.this, "Required permissions are missing. Please grant them.", Toast.LENGTH_LONG).show();
                    checkAndRequestPermissions(); // Re-request if needed
                }
            }
        });
    }

    // --- Permission Handling ---
    private void checkAndRequestPermissions() {
        // List of permissions we need
        ArrayList<String> permissionsToRequest = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO);
        }

        // POST_NOTIFICATIONS is required for foreground services on Android 13 (API 33) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        // RECEIVE_BOOT_COMPLETED is a normal permission, doesn't require runtime request on Android 6+

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                int grantResult = grantResults[i];

                if (permission.equals(Manifest.permission.RECORD_AUDIO)) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "Microphone permission granted!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Microphone permission denied. Voice input will not work.", Toast.LENGTH_LONG).show();
                    }
                } else if (permission.equals(Manifest.permission.POST_NOTIFICATIONS)) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "Notification permission granted!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Notification permission denied. Cannot show persistent notification for service.", Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MainActivity onDestroy");
        // TTS and STT resources are now managed by PeanutService
    }
}
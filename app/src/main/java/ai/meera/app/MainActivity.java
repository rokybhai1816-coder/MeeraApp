package ai.meera.app;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    Button btnSpeak, btnAccessibility;
    TextView tvStatus, tvAccessibility;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnSpeak = findViewById(R.id.btnSpeak);
        btnAccessibility = findViewById(R.id.btnAccessibility);
        tvStatus = findViewById(R.id.tvStatus);
        tvAccessibility = findViewById(R.id.tvAccessibility);

        btnSpeak.setOnClickListener(v -> {
            Intent i = new Intent(this, MeeraVoiceService.class);
            startForegroundService(i);
            tvStatus.setText("💖 Meera sun rahi hai...");
        });

        btnAccessibility.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(i);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isAccessibilityEnabled()) {
            tvAccessibility.setText("✅ Accessibility ON");
            tvAccessibility.setTextColor(0xFF4CAF50);
        } else {
            tvAccessibility.setText("⚠️ Accessibility OFF");
            tvAccessibility.setTextColor(0xFFFF9800);
        }
    }

    private boolean isAccessibilityEnabled() {
        String service = getPackageName() + "/" + MeeraAccessibilityService.class.getName();
        try {
            int enabled = Settings.Secure.getInt(
                getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED
            );
            if (enabled == 1) {
                String services = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                );
                if (services != null) {
                    return services.contains(service);
                }
            }
        } catch (Exception e) {}
        return false;
    }
}

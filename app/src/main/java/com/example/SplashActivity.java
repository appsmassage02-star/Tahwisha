package com.example;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.util.ThemeManager;
import com.example.util.UiUtils;

public class SplashActivity extends Activity {

    private static final long SPLASH_DELAY_MS = 1500L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable launchMainRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isFinishing() && !isDestroyed()) {
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        UiUtils.setupRtlAndInsets(this, findViewById(R.id.rootSplash));

        ImageView ivLogo = findViewById(R.id.ivSplashLogo);
        if (ivLogo != null) {
            ivLogo.setClipToOutline(true);
        }

        TextView tvVersion = findViewById(R.id.tvSplashVersion);
        if (tvVersion != null) {
            tvVersion.setText("الإصدار " + resolveVersionName());
        }

        handler.postDelayed(launchMainRunnable, SPLASH_DELAY_MS);
    }

    private String resolveVersionName() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (pInfo.versionName != null && !pInfo.versionName.trim().isEmpty()) {
                return pInfo.versionName;
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return "1.0";
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(launchMainRunnable);
        super.onDestroy();
    }
}

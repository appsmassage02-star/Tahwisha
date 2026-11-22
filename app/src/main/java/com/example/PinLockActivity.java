package com.example;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.example.security.AppLockManager;
import com.example.util.ThemeManager;
import com.example.util.UiUtils;

public class PinLockActivity extends Activity {

    private final StringBuilder enteredPin = new StringBuilder();
    private View[] dots;
    private TextView tvPinError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin_lock);
        UiUtils.setupRtlAndInsets(this, findViewById(R.id.rootPinLock));

        if (!AppLockManager.isPinEnabled(this)) {
            AppLockManager.unlockSession();
            finish();
            return;
        }

        tvPinError = findViewById(R.id.tvPinError);
        dots = new View[]{
                findViewById(R.id.dotPin1),
                findViewById(R.id.dotPin2),
                findViewById(R.id.dotPin3),
                findViewById(R.id.dotPin4)
        };

        int[] digitIds = new int[]{
                R.id.btnPin0, R.id.btnPin1, R.id.btnPin2, R.id.btnPin3, R.id.btnPin4,
                R.id.btnPin5, R.id.btnPin6, R.id.btnPin7, R.id.btnPin8, R.id.btnPin9
        };

        for (int i = 0; i < digitIds.length; i++) {
            final String digit = String.valueOf(i);
            Button btn = findViewById(digitIds[i]);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onDigitPressed(digit);
                }
            });
        }

        findViewById(R.id.btnPinBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (enteredPin.length() > 0) {
                    enteredPin.deleteCharAt(enteredPin.length() - 1);
                    updateDots();
                }
            }
        });

        findViewById(R.id.btnPinClear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enteredPin.setLength(0);
                tvPinError.setText(R.string.pin_lock_subtitle);
                tvPinError.setTextColor(ThemeManager.resolveAttrColor(PinLockActivity.this, R.attr.colorTextSecondary));
                updateDots();
            }
        });

        updateDots();
    }

    private void onDigitPressed(String digit) {
        if (enteredPin.length() >= 4) {
            return;
        }
        enteredPin.append(digit);
        updateDots();

        if (enteredPin.length() == 4) {
            String candidate = enteredPin.toString();
            if (AppLockManager.verifyPin(this, candidate)) {
                finish();
            } else {
                enteredPin.setLength(0);
                updateDots();
                tvPinError.setText("رمز PIN غير صحيح، حاول مرة أخرى");
                tvPinError.setTextColor(ThemeManager.resolveAttrColor(this, R.attr.colorStatusOverdueText));
            }
        }
    }

    private void updateDots() {
        for (int i = 0; i < dots.length; i++) {
            dots[i].setBackgroundResource(
                    i < enteredPin.length()
                            ? R.drawable.bg_pin_dot_filled
                            : R.drawable.bg_pin_dot_empty
            );
        }
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }
}

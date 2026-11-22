package com.example.util;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

public final class UiUtils {

    private UiUtils() {
    }

    public static void setupRtlAndInsets(Activity activity, View rootView) {
        Window window = activity.getWindow();
        if (window != null) {
            window.getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            boolean dark = ThemeManager.isDarkMode(activity);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    int navFlag = dark ? 0 : WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    controller.setSystemBarsAppearance(
                            navFlag,
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    );
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                int flags = window.getDecorView().getSystemUiVisibility();
                if (dark) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                } else {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
                window.getDecorView().setSystemUiVisibility(flags);
            }
        }
        if (rootView != null) {
            rootView.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                rootView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                    @Override
                    public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                        android.graphics.Insets bars = insets.getInsets(
                                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                        );
                        v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                        return WindowInsets.CONSUMED;
                    }
                });
            } else {
                rootView.setFitsSystemWindows(true);
            }
        }
    }
}

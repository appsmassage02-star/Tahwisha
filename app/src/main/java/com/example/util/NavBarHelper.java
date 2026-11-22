package com.example.util;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.ArchiveActivity;
import com.example.Gam3yaDetailsActivity;
import com.example.MainActivity;
import com.example.R;
import com.example.ReportsActivity;
import com.example.SettingsActivity;

public final class NavBarHelper {

    private NavBarHelper() {
    }

    public static void setupHeader(final Activity activity, String pageTitle, boolean showBackButton) {
        ImageView ivLogo = activity.findViewById(R.id.ivHeaderLogo);
        if (ivLogo != null) {
            ivLogo.setClipToOutline(true);
        }

        ImageButton btnBack = activity.findViewById(R.id.btnHeaderBack);
        if (btnBack != null) {
            if (showBackButton) {
                btnBack.setVisibility(View.VISIBLE);
                btnBack.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        activity.finish();
                    }
                });
            } else {
                btnBack.setVisibility(View.GONE);
            }
        }

        TextView tvPageTitle = activity.findViewById(R.id.tvHeaderPageTitle);
        if (tvPageTitle != null) {
            if (pageTitle != null && !pageTitle.trim().isEmpty()) {
                tvPageTitle.setText(pageTitle);
                tvPageTitle.setVisibility(View.VISIBLE);
            } else {
                tvPageTitle.setVisibility(View.GONE);
            }
        }
    }

    public static void setupBottomNav(final Activity activity, final int activeNavId) {
        int activeColor = ThemeManager.resolveAttrColor(activity, R.attr.colorStatusPaidText);
        int inactiveColor = ThemeManager.resolveAttrColor(activity, R.attr.colorTextSecondary);

        styleNavItem(activity, R.id.ivNavHome, R.id.tvNavHome, activeNavId == R.id.navHome, activeColor, inactiveColor);
        styleNavItem(activity, R.id.ivNavReports, R.id.tvNavReports, activeNavId == R.id.navReports, activeColor, inactiveColor);
        styleNavItem(activity, R.id.ivNavArchive, R.id.tvNavArchive, activeNavId == R.id.navArchive, activeColor, inactiveColor);
        styleNavItem(activity, R.id.ivNavSettings, R.id.tvNavSettings, activeNavId == R.id.navSettings, activeColor, inactiveColor);

        wireNavClick(activity, R.id.navHome, activeNavId, MainActivity.class);
        wireNavClick(activity, R.id.navReports, activeNavId, ReportsActivity.class);
        wireNavClick(activity, R.id.navArchive, activeNavId, ArchiveActivity.class);
        wireNavClick(activity, R.id.navSettings, activeNavId, SettingsActivity.class);
    }

    private static void styleNavItem(
            Activity activity,
            int iconViewId,
            int textViewId,
            boolean isActive,
            int activeColor,
            int inactiveColor
    ) {
        int color = isActive ? activeColor : inactiveColor;
        ImageView iv = activity.findViewById(iconViewId);
        if (iv != null) {
            iv.setColorFilter(color);
        }
        TextView tv = activity.findViewById(textViewId);
        if (tv != null) {
            tv.setTextColor(color);
            tv.setTypeface(null, isActive ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private static void wireNavClick(
            final Activity activity,
            final int navId,
            final int activeNavId,
            final Class<? extends Activity> targetActivity
    ) {
        LinearLayout navItem = activity.findViewById(navId);
        if (navItem == null) {
            return;
        }
        navItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (navId == activeNavId && !(activity instanceof Gam3yaDetailsActivity)) {
                    return;
                }
                if (targetActivity.equals(activity.getClass())) {
                    return;
                }
                Intent intent = new Intent(activity, targetActivity);
                if (targetActivity.equals(MainActivity.class)) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                }
                activity.startActivity(intent);
                if (!(activity instanceof MainActivity)) {
                    activity.finish();
                }
            }
        });
    }
}

package com.example;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.backup.BackupManager;
import com.example.db.DatabaseHelper;
import com.example.notifications.NotificationHelper;
import com.example.security.AppLockManager;
import com.example.util.NavBarHelper;
import com.example.util.ThemeManager;
import com.example.util.UiUtils;

import java.util.List;

public class SettingsActivity extends Activity {

    private static final int REQ_STORAGE_WRITE = 3001;
    private static final int REQ_STORAGE_READ = 3002;
    private static final int REQ_PICK_BACKUP_FILE = 3003;

    private DatabaseHelper dbHelper;
    private TextView tvLastBackupStatus;
    private TextView tvAppVersion;
    private TextView tvNoBackupsFound;
    private LinearLayout llAvailableBackups;

    private TextView tvThemeStatusBadge;
    private Button btnToggleTheme;

    private TextView tvPinStatusBadge;
    private Button btnTogglePin;
    private Button btnChangePin;
    private boolean appliedDarkMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appliedDarkMode = ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        UiUtils.setupRtlAndInsets(this, findViewById(R.id.rootSettings));

        NavBarHelper.setupHeader(this, getString(R.string.settings_title), true);
        NavBarHelper.setupBottomNav(this, R.id.navSettings);

        dbHelper = new DatabaseHelper(this);
        tvLastBackupStatus = findViewById(R.id.tvLastBackupStatus);
        tvAppVersion = findViewById(R.id.tvAppVersion);
        tvNoBackupsFound = findViewById(R.id.tvNoBackupsFound);
        llAvailableBackups = findViewById(R.id.llAvailableBackups);

        tvThemeStatusBadge = findViewById(R.id.tvThemeStatusBadge);
        btnToggleTheme = findViewById(R.id.btnToggleTheme);

        tvPinStatusBadge = findViewById(R.id.tvPinStatusBadge);
        btnTogglePin = findViewById(R.id.btnTogglePin);
        btnChangePin = findViewById(R.id.btnChangePin);

        Button btnBackupNow = findViewById(R.id.btnBackupNow);
        Button btnRestoreBackup = findViewById(R.id.btnRestoreBackup);

        loadVersionInfo();
        updateThemeUi();
        updatePinUi();
        updateBackupUi();

        btnToggleTheme.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean nextDark = !ThemeManager.isDarkMode(SettingsActivity.this);
                ThemeManager.setDarkMode(SettingsActivity.this, nextDark);
                recreate();
            }
        });

        btnTogglePin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (AppLockManager.isPinEnabled(SettingsActivity.this)) {
                    showDisablePinDialog();
                } else {
                    showSetNewPinDialog(false);
                }
            }
        });

        btnChangePin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showVerifyCurrentThenChangePinDialog();
            }
        });

        btnBackupNow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startBackupFlow();
            }
        });

        btnRestoreBackup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startExternalRestorePickerFlow();
            }
        });
    }

    private void loadVersionInfo() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            tvAppVersion.setText(pInfo.versionName != null ? pInfo.versionName : "1.0");
        } catch (PackageManager.NameNotFoundException e) {
            tvAppVersion.setText("1.0");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appliedDarkMode != ThemeManager.isDarkMode(this)) {
            recreate();
            return;
        }
        updateThemeUi();
    }

    private void updateThemeUi() {
        boolean isDark = ThemeManager.isDarkMode(this);
        if (isDark) {
            tvThemeStatusBadge.setText(R.string.theme_mode_dark);
            tvThemeStatusBadge.setBackgroundResource(R.drawable.bg_badge_payout);
            tvThemeStatusBadge.setTextColor(ThemeManager.resolveAttrColor(this, R.attr.colorStatusPayoutText));
            btnToggleTheme.setText(R.string.btn_switch_to_light);
        } else {
            tvThemeStatusBadge.setText(R.string.theme_mode_light);
            tvThemeStatusBadge.setBackgroundResource(R.drawable.bg_badge_paid);
            tvThemeStatusBadge.setTextColor(ThemeManager.resolveAttrColor(this, R.attr.colorStatusPaidText));
            btnToggleTheme.setText(R.string.btn_switch_to_dark);
        }
    }

    // ==================== AppLock PIN Management ====================

    private void updatePinUi() {
        if (AppLockManager.isPinEnabled(this)) {
            tvPinStatusBadge.setText(R.string.applock_status_enabled);
            tvPinStatusBadge.setBackgroundResource(R.drawable.bg_badge_paid);
            tvPinStatusBadge.setTextColor(ThemeManager.resolveAttrColor(this, R.attr.colorStatusPaidText));
            btnTogglePin.setText(R.string.btn_disable_pin);
            btnTogglePin.setBackgroundResource(R.drawable.bg_button_danger);
            btnChangePin.setVisibility(View.VISIBLE);
        } else {
            tvPinStatusBadge.setText(R.string.applock_status_disabled);
            tvPinStatusBadge.setBackgroundResource(R.drawable.bg_badge_upcoming);
            tvPinStatusBadge.setTextColor(ThemeManager.resolveAttrColor(this, R.attr.colorStatusUpcomingText));
            btnTogglePin.setText(R.string.btn_enable_pin);
            btnTogglePin.setBackgroundResource(R.drawable.bg_button_primary);
            btnChangePin.setVisibility(View.GONE);
        }
    }

    private EditText createPinEditText(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        et.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        return et;
    }

    private void showSetNewPinDialog(final boolean isChange) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(48, 24, 48, 8);
        container.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        final EditText etPin1 = createPinEditText("أدخل رمز PIN الجديد (4 أرقام)");
        final EditText etPin2 = createPinEditText("تأكيد رمز PIN الجديد");
        container.addView(etPin1);
        container.addView(etPin2);

        new AlertDialog.Builder(this)
                .setTitle(isChange ? "تعيين رمز PIN جديد" : "تفعيل قفل التطبيق")
                .setView(container)
                .setPositiveButton("حفظ", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String p1 = etPin1.getText().toString().trim();
                        String p2 = etPin2.getText().toString().trim();
                        if (!AppLockManager.isValidPinFormat(p1)) {
                            Toast.makeText(SettingsActivity.this, "يجب أن يتكون الرمز من 4 أرقام", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (!p1.equals(p2)) {
                            Toast.makeText(SettingsActivity.this, "الرمزان غير متطابقين", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        AppLockManager.setPin(SettingsActivity.this, p1);
                        updatePinUi();
                        Toast.makeText(SettingsActivity.this,
                                isChange ? "تم تغيير رمز PIN بنجاح" : "تم تفعيل قفل التطبيق بنجاح",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showDisablePinDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(48, 24, 48, 8);
        final EditText etCurrent = createPinEditText("أدخل رمز PIN الحالي لتعطيل القفل");
        container.addView(etCurrent);

        new AlertDialog.Builder(this)
                .setTitle("تعطيل رمز القفل")
                .setView(container)
                .setPositiveButton("تعطيل", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String current = etCurrent.getText().toString().trim();
                        if (AppLockManager.disablePin(SettingsActivity.this, current)) {
                            updatePinUi();
                            Toast.makeText(SettingsActivity.this, "تم تعطيل قفل التطبيق", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(SettingsActivity.this, "رمز PIN الحالي غير صحيح", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showVerifyCurrentThenChangePinDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(48, 24, 48, 8);
        final EditText etCurrent = createPinEditText("أدخل رمز PIN الحالي");
        container.addView(etCurrent);

        new AlertDialog.Builder(this)
                .setTitle("التحقق من الرمز الحالي")
                .setView(container)
                .setPositiveButton("متابعة", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String current = etCurrent.getText().toString().trim();
                        if (AppLockManager.verifyPin(SettingsActivity.this, current)) {
                            showSetNewPinDialog(true);
                        } else {
                            Toast.makeText(SettingsActivity.this, "رمز PIN الحالي غير صحيح", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    // ==================== Multi-Backup & Restore ====================

    private void updateBackupUi() {
        String lastTime = BackupManager.getLastBackupTime(this);
        if (lastTime != null && !lastTime.isEmpty()) {
            tvLastBackupStatus.setText("آخر نسخة احتياطية: " + lastTime);
        } else {
            String direct = BackupManager.findDirectBackupJson(this);
            if (direct != null) {
                tvLastBackupStatus.setText("توجد نسخة احتياطية محفوظة في مجلد المستندات");
            } else {
                tvLastBackupStatus.setText("لم يتم إنشاء نسخة احتياطية بعد");
            }
        }
        populateAvailableBackupsList();
    }

    private void populateAvailableBackupsList() {
        llAvailableBackups.removeAllViews();
        List<BackupManager.BackupEntry> entries = BackupManager.listAvailableBackups(this);
        if (entries.isEmpty()) {
            tvNoBackupsFound.setVisibility(View.VISIBLE);
            return;
        }
        tvNoBackupsFound.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (final BackupManager.BackupEntry entry : entries) {
            View row = inflater.inflate(R.layout.item_backup_file, llAvailableBackups, false);
            TextView tvName = row.findViewById(R.id.tvBackupFileName);
            TextView tvMeta = row.findViewById(R.id.tvBackupFileMeta);
            Button btnRestore = row.findViewById(R.id.btnRestoreThisBackup);

            tvName.setText(entry.fileName);
            tvMeta.setText(entry.metaLabel);
            btnRestore.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmRestoreEntry(entry);
                }
            });
            llAvailableBackups.addView(row);
        }
    }

    private void confirmRestoreEntry(final BackupManager.BackupEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle("تأكيد استعادة النسخة")
                .setMessage("سيتم استبدال البيانات الحالية بمحتوى الملف:\n" + entry.fileName + "\nهل تريد المتابعة؟")
                .setPositiveButton("استعادة الآن", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        boolean ok = BackupManager.restoreFromBackupEntry(SettingsActivity.this, entry, dbHelper);
                        onRestoreFinished(ok);
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void startBackupFlow() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQ_STORAGE_WRITE
                );
                return;
            }
        }
        performBackup();
    }

    private void performBackup() {
        boolean ok = BackupManager.backupNow(this, dbHelper);
        if (ok) {
            Toast.makeText(
                    this,
                    "تم حفظ gam3ya_latest.json ونسخة مؤرخة في Documents/Gam3yaBackup",
                    Toast.LENGTH_LONG
            ).show();
            updateBackupUi();
        } else {
            Toast.makeText(this, "تعذر حفظ النسخة الاحتياطية", Toast.LENGTH_SHORT).show();
        }
    }

    private void startExternalRestorePickerFlow() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQ_STORAGE_READ
                );
                return;
            }
        }
        openBackupFilePicker();
    }

    private void openBackupFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain"});
        startActivityForResult(intent, REQ_PICK_BACKUP_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_BACKUP_FILE && resultCode == RESULT_OK && data != null) {
            final Uri uri = data.getData();
            if (uri != null) {
                new AlertDialog.Builder(this)
                        .setTitle("تأكيد الاستعادة")
                        .setMessage("هل تريد استعادة البيانات من الملف المختار؟")
                        .setPositiveButton("استعادة", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                boolean ok = BackupManager.restoreFromUri(SettingsActivity.this, uri, dbHelper);
                                onRestoreFinished(ok);
                            }
                        })
                        .setNegativeButton("إلغاء", null)
                        .show();
            }
        }
    }

    private void onRestoreFinished(boolean ok) {
        if (ok) {
            Toast.makeText(this, "تمت استعادة النسخة الاحتياطية بنجاح", Toast.LENGTH_LONG).show();
            NotificationHelper.scheduleDailyCheck(this);
            NotificationHelper.checkAndNotifyNow(this);
            updateBackupUi();
        } else {
            Toast.makeText(this, "فشلت الاستعادة: تأكد من صلاحية ملف JSON", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == REQ_STORAGE_WRITE) {
                performBackup();
            } else if (requestCode == REQ_STORAGE_READ) {
                openBackupFilePicker();
            }
        } else {
            Toast.makeText(this, "يرجى منح إذن التخزين لإتمام العملية", Toast.LENGTH_SHORT).show();
        }
    }
}

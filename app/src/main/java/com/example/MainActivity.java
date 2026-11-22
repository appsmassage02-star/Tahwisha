package com.example;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.adapter.Gam3yaAdapter;
import com.example.backup.BackupManager;
import com.example.db.DatabaseHelper;
import com.example.model.Gam3ya;
import com.example.notifications.NotificationHelper;
import com.example.util.DateUtils;
import com.example.util.NavBarHelper;
import com.example.util.ThemeManager;
import com.example.util.UiUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_POST_NOTIFICATIONS = 2001;

    private DatabaseHelper dbHelper;
    private TextView tvSummaryActiveCount;
    private TextView tvSummaryTotalPaid;
    private TextView tvSummaryTotalRemaining;
    private TextView tvInsight7DaysDue;
    private TextView tvInsight7DaysPayout;
    private ListView lvGam3yat;
    private LinearLayout layoutEmptyMain;

    private final List<Gam3ya> activeList = new ArrayList<>();
    private Gam3yaAdapter adapter;
    private boolean appliedDarkMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appliedDarkMode = ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        UiUtils.setupRtlAndInsets(this, findViewById(R.id.rootMain));

        NavBarHelper.setupHeader(this, null, false);
        NavBarHelper.setupBottomNav(this, R.id.navHome);

        dbHelper = new DatabaseHelper(this);

        tvSummaryActiveCount = findViewById(R.id.tvSummaryActiveCount);
        tvSummaryTotalPaid = findViewById(R.id.tvSummaryTotalPaid);
        tvSummaryTotalRemaining = findViewById(R.id.tvSummaryTotalRemaining);
        tvInsight7DaysDue = findViewById(R.id.tvInsight7DaysDue);
        tvInsight7DaysPayout = findViewById(R.id.tvInsight7DaysPayout);
        lvGam3yat = findViewById(R.id.lvGam3yat);
        layoutEmptyMain = findViewById(R.id.layoutEmptyMain);

        adapter = new Gam3yaAdapter(this, activeList);
        lvGam3yat.setAdapter(adapter);

        lvGam3yat.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Gam3ya selected = activeList.get(position);
                Intent intent = new Intent(MainActivity.this, Gam3yaDetailsActivity.class);
                intent.putExtra(Gam3yaDetailsActivity.EXTRA_GAM3YA_ID, selected.id);
                intent.putExtra(Gam3yaDetailsActivity.EXTRA_READ_ONLY, false);
                startActivity(intent);
            }
        });

        ImageButton fabAdd = findViewById(R.id.fabAddGam3ya);
        fabAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddGam3yaDialog();
            }
        });

        requestNotificationPermissionIfNeeded();
        NotificationHelper.scheduleDailyCheck(this);
        NotificationHelper.checkAndNotifyNow(this);
        checkAutoBackupRestoreOnStartup();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appliedDarkMode != ThemeManager.isDarkMode(this)) {
            recreate();
            return;
        }
        loadData();
    }

    private void loadData() {
        String todayIso = DateUtils.getTodayIso();
        List<Gam3ya> loaded = dbHelper.getActiveGam3yatWithSummaries(todayIso);

        activeList.clear();
        activeList.addAll(loaded);
        adapter.notifyDataSetChanged();

        double totalPaidAll = 0.0;
        double totalRemainingAll = 0.0;
        for (Gam3ya g : activeList) {
            totalPaidAll += g.paidAmount;
            totalRemainingAll += g.remainingAmount;
        }

        tvSummaryActiveCount.setText(String.valueOf(activeList.size()));
        tvSummaryTotalPaid.setText(DateUtils.formatAmount(totalPaidAll));
        tvSummaryTotalRemaining.setText(DateUtils.formatAmount(totalRemainingAll));

        DatabaseHelper.SevenDayInsights insights = dbHelper.getSevenDayInsights(todayIso);
        if (insights.dueOrOverdueCount > 0) {
            tvInsight7DaysDue.setText(
                    DateUtils.formatAmount(insights.dueOrOverdueAmount) +
                            " (" + insights.dueOrOverdueCount + " قسط)"
            );
        } else {
            tvInsight7DaysDue.setText("لا يوجد (0)");
        }

        if (insights.expectedPayoutCount > 0) {
            tvInsight7DaysPayout.setText(
                    DateUtils.formatAmount(insights.expectedPayoutAmount) +
                            " (" + insights.expectedPayoutCount + " جمعية)"
            );
        } else {
            tvInsight7DaysPayout.setText("لا يوجد خلال 7 أيام");
        }

        if (activeList.isEmpty()) {
            lvGam3yat.setVisibility(View.GONE);
            layoutEmptyMain.setVisibility(View.VISIBLE);
        } else {
            lvGam3yat.setVisibility(View.VISIBLE);
            layoutEmptyMain.setVisibility(View.GONE);
        }
    }

    private void showAddGam3yaDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_gam3ya, null);
        final EditText etName = dialogView.findViewById(R.id.etGam3yaName);
        final EditText etMonthlyAmount = dialogView.findViewById(R.id.etMonthlyAmount);
        final EditText etTotalMonths = dialogView.findViewById(R.id.etTotalMonths);
        final EditText etPayoutMonth = dialogView.findViewById(R.id.etPayoutMonth);
        final Button btnPickDate = dialogView.findViewById(R.id.btnPickStartDate);
        final TextView tvSelectedDateInfo = dialogView.findViewById(R.id.tvSelectedDateInfo);
        Button btnSave = dialogView.findViewById(R.id.btnSaveGam3ya);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelGam3ya);

        final int[] pickedDate = new int[]{-1, -1, -1};

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnPickDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Calendar cal = Calendar.getInstance();
                int initYear = pickedDate[0] != -1 ? pickedDate[0] : cal.get(Calendar.YEAR);
                int initMonth0 = pickedDate[1] != -1 ? (pickedDate[1] - 1) : cal.get(Calendar.MONTH);
                int initDay = pickedDate[2] != -1 ? pickedDate[2] : cal.get(Calendar.DAY_OF_MONTH);

                DatePickerDialog dpd = new DatePickerDialog(
                        MainActivity.this,
                        new DatePickerDialog.OnDateSetListener() {
                            @Override
                            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                                pickedDate[0] = year;
                                pickedDate[1] = month + 1;
                                pickedDate[2] = dayOfMonth;

                                String iso = DateUtils.formatIso(year, month + 1, dayOfMonth);
                                btnPickDate.setText("التاريخ المختار: " + DateUtils.formatDisplayDate(iso));
                                tvSelectedDateInfo.setVisibility(View.VISIBLE);
                                tvSelectedDateInfo.setText(
                                        "بداية القسط الأول: " + DateUtils.getArabicMonthYear(iso) +
                                                "  •  يوم الاستحقاق الشهري الثابت: يوم " + dayOfMonth
                                );
                            }
                        },
                        initYear, initMonth0, initDay
                );
                dpd.show();
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = etName.getText().toString().trim();
                String amountStr = etMonthlyAmount.getText().toString().trim();
                String monthsStr = etTotalMonths.getText().toString().trim();
                String payoutStr = etPayoutMonth.getText().toString().trim();

                if (name.isEmpty()) {
                    etName.setError("يرجى إدخال اسم الجمعية");
                    return;
                }
                if (amountStr.isEmpty()) {
                    etMonthlyAmount.setError("يرجى إدخال قيمة القسط");
                    return;
                }
                if (monthsStr.isEmpty()) {
                    etTotalMonths.setError("يرجى إدخال عدد الأقساط");
                    return;
                }
                if (payoutStr.isEmpty()) {
                    etPayoutMonth.setError("يرجى إدخال رقم شهر القبض");
                    return;
                }

                double monthlyAmount;
                int totalMonths;
                int payoutMonth;
                try {
                    monthlyAmount = Double.parseDouble(amountStr);
                    totalMonths = Integer.parseInt(monthsStr);
                    payoutMonth = Integer.parseInt(payoutStr);
                } catch (NumberFormatException e) {
                    Toast.makeText(MainActivity.this, "يرجى إدخال أرقام صحيحة", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (monthlyAmount <= 0) {
                    etMonthlyAmount.setError("يجب أن تكون قيمة القسط أكبر من صفر");
                    return;
                }
                if (totalMonths < 1 || totalMonths > 240) {
                    etTotalMonths.setError("عدد الأقساط يجب أن يكون بين 1 و 240");
                    return;
                }
                if (payoutMonth < 1 || payoutMonth > totalMonths) {
                    etPayoutMonth.setError("شهر القبض يجب أن يكون بين 1 و " + totalMonths);
                    return;
                }
                if (pickedDate[0] == -1) {
                    Toast.makeText(MainActivity.this, "يرجى اختيار تاريخ البداية من التقويم", Toast.LENGTH_SHORT).show();
                    return;
                }

                long insertedId = dbHelper.insertGam3yaWithInstallments(
                        name, monthlyAmount, totalMonths, payoutMonth,
                        pickedDate[0], pickedDate[1], pickedDate[2]
                );
                if (insertedId != -1) {
                    Toast.makeText(MainActivity.this, "تم حفظ الجمعية بنجاح", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    loadData();
                    NotificationHelper.checkAndNotifyNow(MainActivity.this);
                } else {
                    Toast.makeText(MainActivity.this, "حدث خطأ أثناء الحفظ", Toast.LENGTH_SHORT).show();
                }
            }
        });

        dialog.show();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_POST_NOTIFICATIONS
                );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_POST_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                NotificationHelper.scheduleDailyCheck(this);
                NotificationHelper.checkAndNotifyNow(this);
            }
        }
    }

    private void checkAutoBackupRestoreOnStartup() {
        if (BackupManager.wasInitialRestorePrompted(this)) {
            return;
        }
        if (dbHelper.getTotalGam3yatCount() > 0) {
            BackupManager.markInitialRestorePrompted(this);
            return;
        }

        final String backupJson = BackupManager.findDirectBackupJson(this);
        BackupManager.markInitialRestorePrompted(this);
        if (backupJson == null) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("استعادة نسخة احتياطية سابقة")
                .setMessage("تم العثور على أحدث نسخة احتياطية (gam3ya_latest.json) في مجلد المستندات. هل تريد استعادة بيانات الجمعيات الآن؟")
                .setPositiveButton("استعادة الآن", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        boolean ok = BackupManager.restoreFromJsonString(dbHelper, backupJson);
                        if (ok) {
                            Toast.makeText(MainActivity.this, "تمت استعادة البيانات بنجاح", Toast.LENGTH_LONG).show();
                            loadData();
                            NotificationHelper.checkAndNotifyNow(MainActivity.this);
                        } else {
                            Toast.makeText(MainActivity.this, "تعذر قراءة ملف النسخة الاحتياطية", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("تجاهل", null)
                .show();
    }
}

package com.example;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.adapter.PaymentLogAdapter;
import com.example.db.DatabaseHelper;
import com.example.model.PaymentLog;
import com.example.util.DateUtils;
import com.example.util.NavBarHelper;
import com.example.util.ThemeManager;
import com.example.util.UiUtils;

import java.util.ArrayList;
import java.util.List;

public class ReportsActivity extends Activity {

    private DatabaseHelper dbHelper;
    private TextView tvReportsCount;
    private TextView tvReportsTotalAmount;
    private Spinner spFilterGam3ya;
    private Spinner spFilterMonth;
    private ListView lvReports;
    private LinearLayout layoutEmptyReports;

    private final List<String> gam3yaFilterValues = new ArrayList<>();
    private final List<String> monthFilterValues = new ArrayList<>();

    private String selectedGam3ya = null;
    private String selectedMonthPrefix = null;
    private boolean isSettingUpSpinners = false;
    private boolean appliedDarkMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appliedDarkMode = ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);
        UiUtils.setupRtlAndInsets(this, findViewById(R.id.rootReports));

        NavBarHelper.setupHeader(this, getString(R.string.reports_title), true);
        NavBarHelper.setupBottomNav(this, R.id.navReports);

        dbHelper = new DatabaseHelper(this);
        tvReportsCount = findViewById(R.id.tvReportsCount);
        tvReportsTotalAmount = findViewById(R.id.tvReportsTotalAmount);
        spFilterGam3ya = findViewById(R.id.spFilterGam3ya);
        spFilterMonth = findViewById(R.id.spFilterMonth);
        lvReports = findViewById(R.id.lvReports);
        layoutEmptyReports = findViewById(R.id.layoutEmptyReports);

        spFilterGam3ya.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isSettingUpSpinners) return;
                selectedGam3ya = (position == 0) ? null : gam3yaFilterValues.get(position);
                loadFilteredReports();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        spFilterMonth.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isSettingUpSpinners) return;
                selectedMonthPrefix = (position == 0) ? null : monthFilterValues.get(position);
                loadFilteredReports();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appliedDarkMode != ThemeManager.isDarkMode(this)) {
            recreate();
            return;
        }
        setupFilterSpinners();
        loadFilteredReports();
    }

    private void setupFilterSpinners() {
        isSettingUpSpinners = true;

        // 1. Gam3ya Filter
        List<String> gam3yaLabels = new ArrayList<>();
        gam3yaFilterValues.clear();
        gam3yaLabels.add(getString(R.string.filter_all_gam3yat));
        gam3yaFilterValues.add("");

        List<String> distinctNames = dbHelper.getDistinctLogGam3yaNames();
        for (String name : distinctNames) {
            gam3yaLabels.add(name);
            gam3yaFilterValues.add(name);
        }

        ArrayAdapter<String> gAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, gam3yaLabels
        );
        gAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spFilterGam3ya.setAdapter(gAdapter);
        if (selectedGam3ya != null) {
            int idx = gam3yaFilterValues.indexOf(selectedGam3ya);
            if (idx >= 0) {
                spFilterGam3ya.setSelection(idx);
            } else {
                selectedGam3ya = null;
            }
        }

        // 2. Month/Year Filter
        List<String> monthLabels = new ArrayList<>();
        monthFilterValues.clear();
        monthLabels.add(getString(R.string.filter_all_months));
        monthFilterValues.add("");

        List<String> distinctMonths = dbHelper.getDistinctLogMonthPrefixes();
        for (String ym : distinctMonths) {
            monthLabels.add(DateUtils.getArabicMonthYear(ym + "-01"));
            monthFilterValues.add(ym);
        }

        ArrayAdapter<String> mAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, monthLabels
        );
        mAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spFilterMonth.setAdapter(mAdapter);
        if (selectedMonthPrefix != null) {
            int idx = monthFilterValues.indexOf(selectedMonthPrefix);
            if (idx >= 0) {
                spFilterMonth.setSelection(idx);
            } else {
                selectedMonthPrefix = null;
            }
        }

        isSettingUpSpinners = false;
    }

    private void loadFilteredReports() {
        List<PaymentLog> logs = dbHelper.getFilteredPaymentLogs(selectedGam3ya, selectedMonthPrefix);
        double netPaid = DatabaseHelper.computeNetPaidAmount(logs);

        tvReportsCount.setText(String.valueOf(logs.size()));
        tvReportsTotalAmount.setText(DateUtils.formatAmount(netPaid));

        if (logs.isEmpty()) {
            lvReports.setVisibility(View.GONE);
            layoutEmptyReports.setVisibility(View.VISIBLE);
        } else {
            lvReports.setVisibility(View.VISIBLE);
            layoutEmptyReports.setVisibility(View.GONE);
            lvReports.setAdapter(new PaymentLogAdapter(this, logs));
        }
    }
}

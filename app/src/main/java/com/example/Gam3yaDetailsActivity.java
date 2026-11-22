package com.example;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.adapter.InstallmentAdapter;
import com.example.backup.BackupManager;
import com.example.db.DatabaseHelper;
import com.example.model.Gam3ya;
import com.example.model.Installment;
import com.example.util.DateUtils;
import com.example.util.NavBarHelper;
import com.example.util.ThemeManager;
import com.example.util.UiUtils;

import java.util.ArrayList;
import java.util.List;

public class Gam3yaDetailsActivity extends Activity {

    public static final String EXTRA_GAM3YA_ID = "extra_gam3ya_id";
    public static final String EXTRA_READ_ONLY = "extra_read_only";

    private DatabaseHelper dbHelper;
    private long gam3yaId = -1;
    private boolean isReadOnly = false;
    private boolean appliedDarkMode;

    private TextView tvDetailTotal;
    private TextView tvDetailPaid;
    private TextView tvDetailThirdLabel;
    private TextView tvDetailThirdValue;
    private TextView tvDetailNextDueLabel;
    private TextView tvDetailNextDueValue;
    private TextView tvDetailPayoutInfo;
    private LinearLayout llDetailActions;
    private Button btnPayNext;
    private Button btnUndoLast;
    private ListView lvInstallments;

    private final List<Installment> installments = new ArrayList<>();
    private InstallmentAdapter adapter;
    private View archiveFooterView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appliedDarkMode = ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gam3ya_details);
        UiUtils.setupRtlAndInsets(this, findViewById(R.id.rootDetails));

        gam3yaId = getIntent().getLongExtra(EXTRA_GAM3YA_ID, -1);
        isReadOnly = getIntent().getBooleanExtra(EXTRA_READ_ONLY, false);

        if (gam3yaId == -1) {
            finish();
            return;
        }

        dbHelper = new DatabaseHelper(this);

        NavBarHelper.setupHeader(this, getString(R.string.details_title), true);
        NavBarHelper.setupBottomNav(this, isReadOnly ? R.id.navArchive : R.id.navHome);

        tvDetailTotal = findViewById(R.id.tvDetailTotal);
        tvDetailPaid = findViewById(R.id.tvDetailPaid);
        tvDetailThirdLabel = findViewById(R.id.tvDetailThirdLabel);
        tvDetailThirdValue = findViewById(R.id.tvDetailThirdValue);
        tvDetailNextDueLabel = findViewById(R.id.tvDetailNextDueLabel);
        tvDetailNextDueValue = findViewById(R.id.tvDetailNextDueValue);
        tvDetailPayoutInfo = findViewById(R.id.tvDetailPayoutInfo);
        llDetailActions = findViewById(R.id.llDetailActions);
        btnPayNext = findViewById(R.id.btnPayNext);
        btnUndoLast = findViewById(R.id.btnUndoLast);
        lvInstallments = findViewById(R.id.lvInstallments);

        if (isReadOnly) {
            llDetailActions.setVisibility(View.GONE);
            archiveFooterView = LayoutInflater.from(this).inflate(R.layout.footer_archive_delete, lvInstallments, false);
            Button btnDeleteArchived = archiveFooterView.findViewById(R.id.btnDeleteArchivedGam3ya);
            btnDeleteArchived.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmDeleteArchived();
                }
            });
            lvInstallments.addFooterView(archiveFooterView);
        } else {
            btnPayNext.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handlePayNext();
                }
            });
            btnUndoLast.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmUndoLast();
                }
            });
        }

        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appliedDarkMode != ThemeManager.isDarkMode(this)) {
            recreate();
            return;
        }
        refreshUi();
    }

    private void refreshUi() {
        String todayIso = DateUtils.getTodayIso();
        Gam3ya g = dbHelper.getGam3yaById(gam3yaId, todayIso);
        if (g == null) {
            finish();
            return;
        }

        if (isReadOnly || g.isArchived) {
            NavBarHelper.setupHeader(this, g.name + " (مؤرشفة)", true);
            llDetailActions.setVisibility(View.GONE);

            tvDetailTotal.setText(DateUtils.formatAmount(g.totalAmount));
            tvDetailPaid.setText(DateUtils.formatAmount(g.paidAmount));
            tvDetailThirdLabel.setText(R.string.label_installment_value);
            tvDetailThirdValue.setText(DateUtils.formatAmount(g.monthlyAmount));
            tvDetailNextDueLabel.setText(R.string.label_archived_at);
            tvDetailNextDueValue.setText(DateUtils.formatDisplayDate(g.archivedAt));
        } else {
            NavBarHelper.setupHeader(this, g.name, true);
            llDetailActions.setVisibility(View.VISIBLE);

            tvDetailTotal.setText(DateUtils.formatAmount(g.totalAmount));
            tvDetailPaid.setText(DateUtils.formatAmount(g.paidAmount));
            tvDetailThirdLabel.setText(R.string.label_remaining);
            tvDetailThirdValue.setText(DateUtils.formatAmount(g.remainingAmount));
            tvDetailNextDueLabel.setText(R.string.label_next_due);

            Installment nextUnpaid = dbHelper.getNextUnpaidInstallment(gam3yaId);
            if (nextUnpaid != null) {
                String nextMonth = DateUtils.getArabicMonthYear(nextUnpaid.dueDate);
                tvDetailNextDueValue.setText(nextMonth + " (" + DateUtils.formatDisplayDate(nextUnpaid.dueDate) + ")");
                btnPayNext.setEnabled(true);
                btnPayNext.setAlpha(1.0f);
                btnPayNext.setText(
                        "سداد قسط شهر " + DateUtils.getArabicMonthName(nextUnpaid.dueDate) +
                                " (قسط " + nextUnpaid.installmentNumber + ")"
                );
            } else {
                tvDetailNextDueValue.setText("مكتملة");
                btnPayNext.setEnabled(false);
                btnPayNext.setAlpha(0.5f);
                btnPayNext.setText("تم سداد جميع الأقساط");
            }

            if (g.paidCount > 0) {
                btnUndoLast.setEnabled(true);
                btnUndoLast.setAlpha(1.0f);
            } else {
                btnUndoLast.setEnabled(false);
                btnUndoLast.setAlpha(0.45f);
            }
        }

        tvDetailPayoutInfo.setText(
                "ميعاد القبض المحدد: القسط رقم " + g.payoutMonth +
                        " من أصل " + g.totalMonths + " (القسط الشهري: " + DateUtils.formatAmount(g.monthlyAmount) + ")"
        );

        installments.clear();
        installments.addAll(dbHelper.getInstallmentsForGam3ya(gam3yaId));
        adapter = new InstallmentAdapter(this, installments, g.payoutMonth, todayIso);
        lvInstallments.setAdapter(adapter);
    }

    private void handlePayNext() {
        // Silent automatic backup before archiving when paying the final installment
        if (dbHelper.willNextPaymentArchiveGam3ya(gam3yaId)) {
            BackupManager.backupLatestSilently(this, dbHelper);
        }

        String todayIso = DateUtils.getTodayIso();
        DatabaseHelper.PayResult res = dbHelper.payNextUnpaidInstallment(gam3yaId, todayIso);
        if (!res.success) {
            Toast.makeText(this, "لا توجد أقساط متبقية للسداد", Toast.LENGTH_SHORT).show();
            return;
        }

        if (res.archivedNow) {
            new AlertDialog.Builder(this)
                    .setTitle("تم اكتمال الجمعية")
                    .setMessage("تم سداد القسط الأخير (#" + res.installmentNumber + ") بنجاح، وتم نقل الجمعية إلى الأرشيف تلقائياً.")
                    .setCancelable(false)
                    .setPositiveButton("حسناً", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .show();
        } else {
            Toast.makeText(this, "تم تسجيل سداد القسط رقم " + res.installmentNumber, Toast.LENGTH_SHORT).show();
            refreshUi();
        }
    }

    private void confirmUndoLast() {
        new AlertDialog.Builder(this)
                .setTitle("تراجع عن آخر سداد")
                .setMessage("هل تريد إلغاء آخر عملية سداد وإعادة القسط لحالته غير المسددة مع تسجيل العملية في سجل التدقيق؟")
                .setPositiveButton("نعم، تراجع", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int undoneNum = dbHelper.undoLastPayment(gam3yaId);
                        if (undoneNum != -1) {
                            Toast.makeText(Gam3yaDetailsActivity.this,
                                    "تم التراجع عن سداد القسط رقم " + undoneNum, Toast.LENGTH_SHORT).show();
                            refreshUi();
                        } else {
                            Toast.makeText(Gam3yaDetailsActivity.this,
                                    "لا توجد عمليات سداد للتراجع عنها", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void confirmDeleteArchived() {
        new AlertDialog.Builder(this)
                .setTitle("حذف الجمعية نهائياً")
                .setMessage("سيتم أخذ نسخة احتياطية صامتة ثم حذف هذه الجمعية وجميع أقساطها وسجلاتها نهائياً. هل أنت متأكد؟")
                .setPositiveButton("حذف نهائي", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        BackupManager.backupLatestSilently(Gam3yaDetailsActivity.this, dbHelper);
                        dbHelper.deleteGam3yaPermanently(gam3yaId);
                        Toast.makeText(Gam3yaDetailsActivity.this, "تم حذف الجمعية نهائياً", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }
}

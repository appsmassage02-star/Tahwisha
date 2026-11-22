package com.example.notifications;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;

import com.example.Gam3yaDetailsActivity;
import com.example.MainActivity;
import com.example.R;
import com.example.db.DatabaseHelper;
import com.example.model.Gam3ya;
import com.example.model.Installment;
import com.example.util.DateUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class NotificationHelper {

    public static final String CHANNEL_ID = "gam3ya_reminders_channel";
    private static final int ALARM_REQUEST_CODE = 5001;
    private static final int NOTIF_OVERDUE_ID = 1001;
    private static final int NOTIF_PAYOUT_ID = 1002;
    private static final String PREFS_NOTIF = "gam3ya_notif_prefs";
    private static final String KEY_NOTIFIED_PAYOUTS = "notified_payout_installments";

    private NotificationHelper() {
    }

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                return;
            }
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(context.getString(R.string.notif_channel_desc));
            channel.enableVibration(true);
            nm.createNotificationChannel(channel);
        }
    }

    public static void scheduleDailyCheck(Context context) {
        createNotificationChannel(context);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        Intent intent = new Intent(context, NotificationReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags);

        Calendar nextRun = Calendar.getInstance();
        nextRun.set(Calendar.HOUR_OF_DAY, 9);
        nextRun.set(Calendar.MINUTE, 0);
        nextRun.set(Calendar.SECOND, 0);
        nextRun.set(Calendar.MILLISECOND, 0);

        if (nextRun.getTimeInMillis() <= System.currentTimeMillis()) {
            nextRun.add(Calendar.DAY_OF_YEAR, 1);
        }

        alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextRun.getTimeInMillis(),
                pendingIntent
        );
    }

    public static void checkAndNotifyNow(Context context) {
        createNotificationChannel(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        DatabaseHelper dbHelper = new DatabaseHelper(context);
        String todayIso = DateUtils.getTodayIso();
        List<Gam3ya> activeGam3yat = dbHelper.getActiveGam3yatWithSummaries(todayIso);

        List<String> overdueNames = new ArrayList<>();
        int totalOverdueInstallments = 0;
        double totalOverdueAmount = 0.0;
        long singleOverdueGam3yaId = -1;

        List<Gam3ya> payoutDueGam3yat = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NOTIF, Context.MODE_PRIVATE);
        Set<String> notifiedPayouts = new HashSet<>(prefs.getStringSet(KEY_NOTIFIED_PAYOUTS, new HashSet<String>()));
        boolean payoutPrefsChanged = false;

        for (Gam3ya g : activeGam3yat) {
            List<Installment> installments = dbHelper.getInstallmentsForGam3ya(g.id);
            boolean hasOverdueForThis = false;

            for (Installment inst : installments) {
                if (inst.isPaid) {
                    continue;
                }
                int status = DateUtils.computeInstallmentStatus(inst, todayIso);
                if (status == Installment.STATUS_OVERDUE) {
                    totalOverdueInstallments++;
                    totalOverdueAmount += inst.amount;
                    hasOverdueForThis = true;
                }

                if (inst.installmentNumber == g.payoutMonth) {
                    long daysUntil = DateUtils.daysBetween(todayIso, inst.dueDate);
                    String key = g.id + "_" + inst.id + "_" + todayIso;
                    if (daysUntil == 0 && !notifiedPayouts.contains(key)) {
                        payoutDueGam3yat.add(g);
                        notifiedPayouts.add(key);
                        payoutPrefsChanged = true;
                    }
                }
            }

            if (hasOverdueForThis) {
                overdueNames.add(g.name);
                singleOverdueGam3yaId = g.id;
            }
        }

        if (payoutPrefsChanged) {
            prefs.edit().putStringSet(KEY_NOTIFIED_PAYOUTS, notifiedPayouts).apply();
        }

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }

        // 1. Single Grouped Summary Notification for All Overdue Installments
        if (totalOverdueInstallments > 0) {
            String title = "تنبيه: أقساط متأخرة عن السداد";
            StringBuilder body = new StringBuilder();
            body.append("لديك ").append(totalOverdueInstallments)
                    .append(" قسط متأخر بإجمالي ").append(DateUtils.formatAmount(totalOverdueAmount))
                    .append(" في: ");
            for (int i = 0; i < overdueNames.size(); i++) {
                if (i > 0) body.append("، ");
                body.append(overdueNames.get(i));
            }

            Intent openIntent;
            if (overdueNames.size() == 1 && singleOverdueGam3yaId != -1) {
                openIntent = new Intent(context, Gam3yaDetailsActivity.class);
                openIntent.putExtra(Gam3yaDetailsActivity.EXTRA_GAM3YA_ID, singleOverdueGam3yaId);
            } else {
                openIntent = new Intent(context, MainActivity.class);
            }
            openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent contentPi = PendingIntent.getActivity(
                    context, NOTIF_OVERDUE_ID, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            Notification notification = buildNotification(
                    context, title, body.toString(), contentPi,
                    context.getString(R.string.notif_action_open)
            );
            nm.notify(NOTIF_OVERDUE_ID, notification);
        }

        // 2. Direct Notification for Payout Month Arrival
        if (!payoutDueGam3yat.isEmpty()) {
            String title = "ميعاد القبض اليوم!";
            StringBuilder body = new StringBuilder();
            if (payoutDueGam3yat.size() == 1) {
                Gam3ya g = payoutDueGam3yat.get(0);
                body.append("اليوم هو موعد قبض جمعية \"").append(g.name)
                        .append("\" بقيمة إجمالية ").append(DateUtils.formatAmount(g.totalAmount));
            } else {
                double sumPayout = 0.0;
                body.append("اليوم موعد قبض: ");
                for (int i = 0; i < payoutDueGam3yat.size(); i++) {
                    if (i > 0) body.append("، ");
                    body.append(payoutDueGam3yat.get(i).name);
                    sumPayout += payoutDueGam3yat.get(i).totalAmount;
                }
                body.append(" بإجمالي ").append(DateUtils.formatAmount(sumPayout));
            }

            Intent openIntent;
            if (payoutDueGam3yat.size() == 1) {
                openIntent = new Intent(context, Gam3yaDetailsActivity.class);
                openIntent.putExtra(Gam3yaDetailsActivity.EXTRA_GAM3YA_ID, payoutDueGam3yat.get(0).id);
            } else {
                openIntent = new Intent(context, MainActivity.class);
            }
            openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent contentPi = PendingIntent.getActivity(
                    context, NOTIF_PAYOUT_ID, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            Notification notification = buildNotification(
                    context, title, body.toString(), contentPi,
                    context.getString(R.string.notif_action_open)
            );
            nm.notify(NOTIF_PAYOUT_ID, notification);
        }
    }

    @SuppressWarnings("deprecation")
    private static Notification buildNotification(
            Context context,
            String title,
            String body,
            PendingIntent contentIntent,
            String actionLabel
    ) {
        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);

        builder.setSmallIcon(R.drawable.ic_wallet)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(contentIntent);

        Notification.Action action = new Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.ic_wallet),
                actionLabel,
                contentIntent
        ).build();
        builder.addAction(action);

        return builder.build();
    }
}

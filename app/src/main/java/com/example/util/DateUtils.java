package com.example.util;

import com.example.model.Installment;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class DateUtils {

    private static final String[] ARABIC_MONTHS = new String[]{
            "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
            "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
    };

    private DateUtils() {
    }

    public static String getTodayIso() {
        Calendar cal = Calendar.getInstance();
        return formatIso(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
        );
    }

    public static String getNowDateTimeIso() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        return sdf.format(new Date());
    }

    public static String formatIso(int year, int month1To12, int day) {
        return String.format(Locale.US, "%04d-%02d-%02d", year, month1To12, day);
    }

    /**
     * Calculates the exact due date for installment N (1-based) from startYear/startMonth1To12,
     * clamping fixedDueDay to the last valid day of shorter months (e.g., Feb 28/29, Apr 30).
     */
    public static String calculateInstallmentDueDate(
            int startYear,
            int startMonth1To12,
            int fixedDueDay,
            int installmentIndex1Based
    ) {
        int totalMonth0Based = (startMonth1To12 - 1) + (installmentIndex1Based - 1);
        int targetYear = startYear + (totalMonth0Based / 12);
        int targetMonth0Based = totalMonth0Based % 12;

        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(targetYear, targetMonth0Based, 1);
        int maxDaysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int clampedDay = Math.min(Math.max(fixedDueDay, 1), maxDaysInMonth);

        return formatIso(targetYear, targetMonth0Based + 1, clampedDay);
    }

    public static String formatDisplayDate(String isoDate) {
        if (isoDate == null || isoDate.trim().isEmpty()) {
            return "-";
        }
        String[] parts = isoDate.split("-");
        if (parts.length != 3) {
            return isoDate;
        }
        return parts[2] + "/" + parts[1] + "/" + parts[0];
    }

    public static String getArabicMonthName(String isoDate) {
        if (isoDate == null) {
            return "";
        }
        String[] parts = isoDate.split("-");
        if (parts.length != 3) {
            return "";
        }
        try {
            int monthIndex = Integer.parseInt(parts[1]) - 1;
            if (monthIndex >= 0 && monthIndex < ARABIC_MONTHS.length) {
                return ARABIC_MONTHS[monthIndex];
            }
        } catch (NumberFormatException ignored) {
        }
        return "";
    }

    public static String getArabicMonthYear(String isoDate) {
        if (isoDate == null) {
            return "";
        }
        String[] parts = isoDate.split("-");
        if (parts.length != 3) {
            return isoDate;
        }
        String monthName = getArabicMonthName(isoDate);
        if (monthName.isEmpty()) {
            return isoDate;
        }
        return monthName + " " + parts[0];
    }

    /**
     * Returns (toIso - fromIso) in whole calendar days using UTC to prevent DST drift.
     */
    public static long daysBetween(String fromIso, String toIso) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        sdf.setLenient(false);
        try {
            Date fromDate = sdf.parse(fromIso);
            Date toDate = sdf.parse(toIso);
            if (fromDate == null || toDate == null) {
                return 0;
            }
            long diffMs = toDate.getTime() - fromDate.getTime();
            return diffMs / 86400000L;
        } catch (ParseException e) {
            return 0;
        }
    }

    public static int computeInstallmentStatus(Installment installment, String todayIso) {
        if (installment.isPaid) {
            return Installment.STATUS_PAID;
        }
        long daysUntilDue = daysBetween(todayIso, installment.dueDate);
        if (daysUntilDue < 0) {
            return Installment.STATUS_OVERDUE;
        } else if (daysUntilDue <= 3) {
            return Installment.STATUS_DUE;
        } else {
            return Installment.STATUS_UPCOMING;
        }
    }

    public static String formatAmount(double amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        if (Math.abs(amount - Math.rint(amount)) < 0.001) {
            DecimalFormat df = new DecimalFormat("#,##0", symbols);
            return df.format(Math.rint(amount));
        }
        DecimalFormat df = new DecimalFormat("#,##0.##", symbols);
        return df.format(amount);
    }
}

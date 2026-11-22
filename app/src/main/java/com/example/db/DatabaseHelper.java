package com.example.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.model.Gam3ya;
import com.example.model.Installment;
import com.example.model.PaymentLog;
import com.example.util.DateUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "gam3yat_tahwisha.db";
    public static final int DATABASE_VERSION = 2;

    public static final String TABLE_GAM3YAT = "gam3yat";
    public static final String TABLE_INSTALLMENTS = "installments";
    public static final String TABLE_PAYMENTS_LOG = "payments_log";

    public static class PayResult {
        public boolean success;
        public int installmentNumber;
        public boolean archivedNow;
        public String gam3yaName;
    }

    public static class SevenDayInsights {
        public int dueOrOverdueCount;
        public double dueOrOverdueAmount;
        public int expectedPayoutCount;
        public double expectedPayoutAmount;
    }

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE " + TABLE_GAM3YAT + " (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT NOT NULL," +
                        "monthly_amount REAL NOT NULL," +
                        "total_months INTEGER NOT NULL," +
                        "payout_month INTEGER NOT NULL," +
                        "due_day INTEGER NOT NULL," +
                        "start_date TEXT NOT NULL," +
                        "total_amount REAL NOT NULL," +
                        "is_archived INTEGER NOT NULL DEFAULT 0," +
                        "archived_at TEXT" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE " + TABLE_INSTALLMENTS + " (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "gam3ya_id INTEGER NOT NULL," +
                        "installment_number INTEGER NOT NULL," +
                        "due_date TEXT NOT NULL," +
                        "amount REAL NOT NULL," +
                        "is_paid INTEGER NOT NULL DEFAULT 0," +
                        "paid_date TEXT," +
                        "FOREIGN KEY(gam3ya_id) REFERENCES " + TABLE_GAM3YAT + "(id) ON DELETE CASCADE" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE " + TABLE_PAYMENTS_LOG + " (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "gam3ya_id INTEGER NOT NULL," +
                        "gam3ya_name TEXT NOT NULL," +
                        "installment_id INTEGER NOT NULL," +
                        "installment_number INTEGER NOT NULL," +
                        "amount REAL NOT NULL," +
                        "paid_date TEXT NOT NULL," +
                        "created_at INTEGER NOT NULL," +
                        "event_type TEXT NOT NULL DEFAULT '" + PaymentLog.TYPE_PAY + "'" +
                        ")"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL(
                    "ALTER TABLE " + TABLE_PAYMENTS_LOG +
                            " ADD COLUMN event_type TEXT NOT NULL DEFAULT '" + PaymentLog.TYPE_PAY + "'"
            );
        }
    }

    public long insertGam3yaWithInstallments(
            String name,
            double monthlyAmount,
            int totalMonths,
            int payoutMonth,
            int startYear,
            int startMonth1To12,
            int fixedDueDay
    ) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        long gam3yaId = -1;
        try {
            String startDateIso = DateUtils.formatIso(startYear, startMonth1To12, fixedDueDay);
            double totalAmount = monthlyAmount * totalMonths;

            ContentValues gValues = new ContentValues();
            gValues.put("name", name.trim());
            gValues.put("monthly_amount", monthlyAmount);
            gValues.put("total_months", totalMonths);
            gValues.put("payout_month", payoutMonth);
            gValues.put("due_day", fixedDueDay);
            gValues.put("start_date", startDateIso);
            gValues.put("total_amount", totalAmount);
            gValues.put("is_archived", 0);
            gValues.putNull("archived_at");

            gam3yaId = db.insertOrThrow(TABLE_GAM3YAT, null, gValues);

            for (int i = 1; i <= totalMonths; i++) {
                String dueDateIso = DateUtils.calculateInstallmentDueDate(
                        startYear, startMonth1To12, fixedDueDay, i
                );
                ContentValues iValues = new ContentValues();
                iValues.put("gam3ya_id", gam3yaId);
                iValues.put("installment_number", i);
                iValues.put("due_date", dueDateIso);
                iValues.put("amount", monthlyAmount);
                iValues.put("is_paid", 0);
                iValues.putNull("paid_date");
                db.insertOrThrow(TABLE_INSTALLMENTS, null, iValues);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return gam3yaId;
    }

    public List<Gam3ya> getActiveGam3yatWithSummaries(String todayIso) {
        List<Gam3ya> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT id, name, monthly_amount, total_months, payout_month, due_day, " +
                        "start_date, total_amount, is_archived, archived_at " +
                        "FROM " + TABLE_GAM3YAT + " WHERE is_archived = 0 ORDER BY id DESC",
                null
        );
        try {
            while (cursor.moveToNext()) {
                Gam3ya g = readGam3yaFromCursor(cursor);
                populateGam3yaSummary(db, g, todayIso);
                list.add(g);
            }
        } finally {
            cursor.close();
        }
        return list;
    }

    public SevenDayInsights getSevenDayInsights(String todayIso) {
        SevenDayInsights insights = new SevenDayInsights();
        List<Gam3ya> active = getActiveGam3yatWithSummaries(todayIso);
        for (Gam3ya g : active) {
            List<Installment> installments = getInstallmentsForGam3ya(g.id);
            for (Installment inst : installments) {
                if (inst.isPaid) {
                    continue;
                }
                long diff = DateUtils.daysBetween(todayIso, inst.dueDate);
                if (diff <= 7) {
                    insights.dueOrOverdueCount++;
                    insights.dueOrOverdueAmount += inst.amount;
                }
                if (inst.installmentNumber == g.payoutMonth && diff >= 0 && diff <= 7) {
                    insights.expectedPayoutCount++;
                    insights.expectedPayoutAmount += g.totalAmount;
                }
            }
        }
        return insights;
    }

    public List<Gam3ya> getArchivedGam3yat() {
        List<Gam3ya> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT id, name, monthly_amount, total_months, payout_month, due_day, " +
                        "start_date, total_amount, is_archived, archived_at " +
                        "FROM " + TABLE_GAM3YAT + " WHERE is_archived = 1 ORDER BY archived_at DESC, id DESC",
                null
        );
        try {
            while (cursor.moveToNext()) {
                Gam3ya g = readGam3yaFromCursor(cursor);
                populateGam3yaSummary(db, g, DateUtils.getTodayIso());
                list.add(g);
            }
        } finally {
            cursor.close();
        }
        return list;
    }

    public Gam3ya getGam3yaById(long gam3yaId, String todayIso) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT id, name, monthly_amount, total_months, payout_month, due_day, " +
                        "start_date, total_amount, is_archived, archived_at " +
                        "FROM " + TABLE_GAM3YAT + " WHERE id = ?",
                new String[]{String.valueOf(gam3yaId)}
        );
        try {
            if (cursor.moveToFirst()) {
                Gam3ya g = readGam3yaFromCursor(cursor);
                populateGam3yaSummary(db, g, todayIso);
                return g;
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    private Gam3ya readGam3yaFromCursor(Cursor cursor) {
        Gam3ya g = new Gam3ya();
        g.id = cursor.getLong(0);
        g.name = cursor.getString(1);
        g.monthlyAmount = cursor.getDouble(2);
        g.totalMonths = cursor.getInt(3);
        g.payoutMonth = cursor.getInt(4);
        g.dueDay = cursor.getInt(5);
        g.startDate = cursor.getString(6);
        g.totalAmount = cursor.getDouble(7);
        g.isArchived = cursor.getInt(8) == 1;
        g.archivedAt = cursor.getString(9);
        return g;
    }

    private void populateGam3yaSummary(SQLiteDatabase db, Gam3ya g, String todayIso) {
        double paid = 0.0;
        int paidCount = 0;
        int overdueCount = 0;
        int dueSoonCount = 0;
        String nextDue = null;
        int nextNum = -1;
        boolean nextIsPayout = false;

        Cursor c = db.rawQuery(
                "SELECT installment_number, due_date, amount, is_paid " +
                        "FROM " + TABLE_INSTALLMENTS + " WHERE gam3ya_id = ? ORDER BY installment_number ASC",
                new String[]{String.valueOf(g.id)}
        );
        try {
            while (c.moveToNext()) {
                int num = c.getInt(0);
                String dueDate = c.getString(1);
                double amount = c.getDouble(2);
                boolean isPaid = c.getInt(3) == 1;

                if (isPaid) {
                    paid += amount;
                    paidCount++;
                } else {
                    if (nextDue == null) {
                        nextDue = dueDate;
                        nextNum = num;
                        nextIsPayout = (num == g.payoutMonth);
                    }
                    Installment temp = new Installment();
                    temp.isPaid = false;
                    temp.dueDate = dueDate;
                    int status = DateUtils.computeInstallmentStatus(temp, todayIso);
                    if (status == Installment.STATUS_OVERDUE) {
                        overdueCount++;
                    } else if (status == Installment.STATUS_DUE) {
                        dueSoonCount++;
                    }
                }
            }
        } finally {
            c.close();
        }

        g.paidAmount = paid;
        g.remainingAmount = Math.max(0.0, g.totalAmount - paid);
        g.paidCount = paidCount;
        g.nextDueDate = nextDue;
        g.nextInstallmentNumber = nextNum;
        g.overdueCount = overdueCount;
        g.dueSoonCount = dueSoonCount;
        g.isNextPayoutMonth = nextIsPayout;
    }

    public List<Installment> getInstallmentsForGam3ya(long gam3yaId) {
        List<Installment> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT id, gam3ya_id, installment_number, due_date, amount, is_paid, paid_date " +
                        "FROM " + TABLE_INSTALLMENTS + " WHERE gam3ya_id = ? ORDER BY installment_number ASC",
                new String[]{String.valueOf(gam3yaId)}
        );
        try {
            while (c.moveToNext()) {
                Installment inst = new Installment();
                inst.id = c.getLong(0);
                inst.gam3yaId = c.getLong(1);
                inst.installmentNumber = c.getInt(2);
                inst.dueDate = c.getString(3);
                inst.amount = c.getDouble(4);
                inst.isPaid = c.getInt(5) == 1;
                inst.paidDate = c.getString(6);
                list.add(inst);
            }
        } finally {
            c.close();
        }
        return list;
    }

    public Installment getNextUnpaidInstallment(long gam3yaId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT id, gam3ya_id, installment_number, due_date, amount, is_paid, paid_date " +
                        "FROM " + TABLE_INSTALLMENTS + " WHERE gam3ya_id = ? AND is_paid = 0 " +
                        "ORDER BY installment_number ASC LIMIT 1",
                new String[]{String.valueOf(gam3yaId)}
        );
        try {
            if (c.moveToFirst()) {
                Installment inst = new Installment();
                inst.id = c.getLong(0);
                inst.gam3yaId = c.getLong(1);
                inst.installmentNumber = c.getInt(2);
                inst.dueDate = c.getString(3);
                inst.amount = c.getDouble(4);
                inst.isPaid = false;
                inst.paidDate = null;
                return inst;
            }
        } finally {
            c.close();
        }
        return null;
    }

    /**
     * Checks if paying the next unpaid installment will complete and archive the gam3ya.
     */
    public boolean willNextPaymentArchiveGam3ya(long gam3yaId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_INSTALLMENTS + " WHERE gam3ya_id = ? AND is_paid = 0",
                new String[]{String.valueOf(gam3yaId)}
        );
        try {
            if (c.moveToFirst()) {
                return c.getInt(0) == 1;
            }
        } finally {
            c.close();
        }
        return false;
    }

    public PayResult payNextUnpaidInstallment(long gam3yaId, String paidDateIso) {
        PayResult result = new PayResult();
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String gam3yaName = "";
            double totalAmount = 0.0;
            Cursor gCur = db.rawQuery(
                    "SELECT name, total_amount FROM " + TABLE_GAM3YAT + " WHERE id = ?",
                    new String[]{String.valueOf(gam3yaId)}
            );
            try {
                if (gCur.moveToFirst()) {
                    gam3yaName = gCur.getString(0);
                    totalAmount = gCur.getDouble(1);
                } else {
                    return result;
                }
            } finally {
                gCur.close();
            }

            long instId = -1;
            int instNumber = 0;
            double amount = 0.0;
            Cursor iCur = db.rawQuery(
                    "SELECT id, installment_number, amount FROM " + TABLE_INSTALLMENTS +
                            " WHERE gam3ya_id = ? AND is_paid = 0 ORDER BY installment_number ASC LIMIT 1",
                    new String[]{String.valueOf(gam3yaId)}
            );
            try {
                if (iCur.moveToFirst()) {
                    instId = iCur.getLong(0);
                    instNumber = iCur.getInt(1);
                    amount = iCur.getDouble(2);
                } else {
                    return result;
                }
            } finally {
                iCur.close();
            }

            ContentValues instValues = new ContentValues();
            instValues.put("is_paid", 1);
            instValues.put("paid_date", paidDateIso);
            db.update(TABLE_INSTALLMENTS, instValues, "id = ?", new String[]{String.valueOf(instId)});

            long nowMs = System.currentTimeMillis();
            ContentValues logValues = new ContentValues();
            logValues.put("gam3ya_id", gam3yaId);
            logValues.put("gam3ya_name", gam3yaName);
            logValues.put("installment_id", instId);
            logValues.put("installment_number", instNumber);
            logValues.put("amount", amount);
            logValues.put("paid_date", paidDateIso);
            logValues.put("created_at", nowMs);
            logValues.put("event_type", PaymentLog.TYPE_PAY);
            db.insertOrThrow(TABLE_PAYMENTS_LOG, null, logValues);

            int remainingUnpaid = 0;
            Cursor cntCur = db.rawQuery(
                    "SELECT COUNT(*) FROM " + TABLE_INSTALLMENTS + " WHERE gam3ya_id = ? AND is_paid = 0",
                    new String[]{String.valueOf(gam3yaId)}
            );
            try {
                if (cntCur.moveToFirst()) {
                    remainingUnpaid = cntCur.getInt(0);
                }
            } finally {
                cntCur.close();
            }

            boolean archivedNow = false;
            if (remainingUnpaid == 0) {
                ContentValues archiveValues = new ContentValues();
                archiveValues.put("is_archived", 1);
                archiveValues.put("archived_at", paidDateIso);
                db.update(TABLE_GAM3YAT, archiveValues, "id = ?", new String[]{String.valueOf(gam3yaId)});

                ContentValues archiveLog = new ContentValues();
                archiveLog.put("gam3ya_id", gam3yaId);
                archiveLog.put("gam3ya_name", gam3yaName);
                archiveLog.put("installment_id", instId);
                archiveLog.put("installment_number", instNumber);
                archiveLog.put("amount", totalAmount);
                archiveLog.put("paid_date", paidDateIso);
                archiveLog.put("created_at", nowMs + 1);
                archiveLog.put("event_type", PaymentLog.TYPE_ARCHIVE);
                db.insertOrThrow(TABLE_PAYMENTS_LOG, null, archiveLog);

                archivedNow = true;
            }

            db.setTransactionSuccessful();
            result.success = true;
            result.installmentNumber = instNumber;
            result.archivedNow = archivedNow;
            result.gam3yaName = gam3yaName;
        } finally {
            db.endTransaction();
        }
        return result;
    }

    public int undoLastPayment(long gam3yaId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        int undoneNumber = -1;
        try {
            String gam3yaName = "";
            Cursor gCur = db.rawQuery(
                    "SELECT name FROM " + TABLE_GAM3YAT + " WHERE id = ?",
                    new String[]{String.valueOf(gam3yaId)}
            );
            try {
                if (gCur.moveToFirst()) {
                    gam3yaName = gCur.getString(0);
                }
            } finally {
                gCur.close();
            }

            long instId = -1;
            double amount = 0.0;
            Cursor c = db.rawQuery(
                    "SELECT id, installment_number, amount FROM " + TABLE_INSTALLMENTS +
                            " WHERE gam3ya_id = ? AND is_paid = 1 ORDER BY installment_number DESC LIMIT 1",
                    new String[]{String.valueOf(gam3yaId)}
            );
            try {
                if (c.moveToFirst()) {
                    instId = c.getLong(0);
                    undoneNumber = c.getInt(1);
                    amount = c.getDouble(2);
                } else {
                    return -1;
                }
            } finally {
                c.close();
            }

            ContentValues instValues = new ContentValues();
            instValues.put("is_paid", 0);
            instValues.putNull("paid_date");
            db.update(TABLE_INSTALLMENTS, instValues, "id = ?", new String[]{String.valueOf(instId)});

            ContentValues undoLog = new ContentValues();
            undoLog.put("gam3ya_id", gam3yaId);
            undoLog.put("gam3ya_name", gam3yaName);
            undoLog.put("installment_id", instId);
            undoLog.put("installment_number", undoneNumber);
            undoLog.put("amount", amount);
            undoLog.put("paid_date", DateUtils.getTodayIso());
            undoLog.put("created_at", System.currentTimeMillis());
            undoLog.put("event_type", PaymentLog.TYPE_UNDO);
            db.insertOrThrow(TABLE_PAYMENTS_LOG, null, undoLog);

            ContentValues unarchiveValues = new ContentValues();
            unarchiveValues.put("is_archived", 0);
            unarchiveValues.putNull("archived_at");
            db.update(TABLE_GAM3YAT, unarchiveValues, "id = ?", new String[]{String.valueOf(gam3yaId)});

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return undoneNumber;
    }

    public void deleteGam3yaPermanently(long gam3yaId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_PAYMENTS_LOG, "gam3ya_id = ?", new String[]{String.valueOf(gam3yaId)});
            db.delete(TABLE_INSTALLMENTS, "gam3ya_id = ?", new String[]{String.valueOf(gam3yaId)});
            db.delete(TABLE_GAM3YAT, "id = ?", new String[]{String.valueOf(gam3yaId)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<PaymentLog> getAllPaymentLogs() {
        return getFilteredPaymentLogs(null, null);
    }

    public List<PaymentLog> getFilteredPaymentLogs(String gam3yaNameFilter, String monthPrefixFilter) {
        List<PaymentLog> logs = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<String> args = new ArrayList<>();
        if (gam3yaNameFilter != null && !gam3yaNameFilter.isEmpty()) {
            where.append(" AND gam3ya_name = ?");
            args.add(gam3yaNameFilter);
        }
        if (monthPrefixFilter != null && !monthPrefixFilter.isEmpty()) {
            where.append(" AND paid_date LIKE ?");
            args.add(monthPrefixFilter + "-%");
        }

        Cursor c = db.rawQuery(
                "SELECT id, gam3ya_id, gam3ya_name, installment_id, installment_number, amount, paid_date, created_at, event_type " +
                        "FROM " + TABLE_PAYMENTS_LOG + where +
                        " ORDER BY paid_date DESC, created_at DESC, id DESC",
                args.toArray(new String[0])
        );
        try {
            while (c.moveToNext()) {
                PaymentLog log = new PaymentLog();
                log.id = c.getLong(0);
                log.gam3yaId = c.getLong(1);
                log.gam3yaName = c.getString(2);
                log.installmentId = c.getLong(3);
                log.installmentNumber = c.getInt(4);
                log.amount = c.getDouble(5);
                log.paidDate = c.getString(6);
                log.createdAt = c.getLong(7);
                log.eventType = c.getString(8);
                logs.add(log);
            }
        } finally {
            c.close();
        }
        return logs;
    }

    public List<String> getDistinctLogGam3yaNames() {
        List<String> names = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT DISTINCT gam3ya_name FROM " + TABLE_PAYMENTS_LOG + " ORDER BY gam3ya_name ASC",
                null
        );
        try {
            while (c.moveToNext()) {
                String n = c.getString(0);
                if (n != null && !n.isEmpty()) {
                    names.add(n);
                }
            }
        } finally {
            c.close();
        }
        return names;
    }

    public List<String> getDistinctLogMonthPrefixes() {
        List<String> months = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT DISTINCT substr(paid_date, 1, 7) AS ym FROM " + TABLE_PAYMENTS_LOG + " ORDER BY ym DESC",
                null
        );
        try {
            while (c.moveToNext()) {
                String ym = c.getString(0);
                if (ym != null && ym.length() == 7) {
                    months.add(ym);
                }
            }
        } finally {
            c.close();
        }
        return months;
    }

    public static double computeNetPaidAmount(List<PaymentLog> logs) {
        double net = 0.0;
        for (PaymentLog log : logs) {
            if (PaymentLog.TYPE_PAY.equals(log.eventType)) {
                net += log.amount;
            } else if (PaymentLog.TYPE_UNDO.equals(log.eventType)) {
                net -= log.amount;
            }
        }
        return Math.max(0.0, net);
    }

    public int getTotalGam3yatCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_GAM3YAT, null);
        try {
            if (c.moveToFirst()) {
                return c.getInt(0);
            }
        } finally {
            c.close();
        }
        return 0;
    }

    public String exportDatabaseToJson() throws JSONException {
        SQLiteDatabase db = getReadableDatabase();
        JSONObject root = new JSONObject();
        root.put("schema_version", DATABASE_VERSION);
        root.put("exported_at", DateUtils.getNowDateTimeIso());

        JSONArray gam3yatArray = new JSONArray();
        Cursor gc = db.rawQuery(
                "SELECT id, name, monthly_amount, total_months, payout_month, due_day, start_date, total_amount, is_archived, archived_at FROM " + TABLE_GAM3YAT,
                null
        );
        try {
            while (gc.moveToNext()) {
                JSONObject obj = new JSONObject();
                obj.put("id", gc.getLong(0));
                obj.put("name", gc.getString(1));
                obj.put("monthly_amount", gc.getDouble(2));
                obj.put("total_months", gc.getInt(3));
                obj.put("payout_month", gc.getInt(4));
                obj.put("due_day", gc.getInt(5));
                obj.put("start_date", gc.getString(6));
                obj.put("total_amount", gc.getDouble(7));
                obj.put("is_archived", gc.getInt(8));
                obj.put("archived_at", gc.isNull(9) ? JSONObject.NULL : gc.getString(9));
                gam3yatArray.put(obj);
            }
        } finally {
            gc.close();
        }
        root.put("gam3yat", gam3yatArray);

        JSONArray instArray = new JSONArray();
        Cursor ic = db.rawQuery(
                "SELECT id, gam3ya_id, installment_number, due_date, amount, is_paid, paid_date FROM " + TABLE_INSTALLMENTS,
                null
        );
        try {
            while (ic.moveToNext()) {
                JSONObject obj = new JSONObject();
                obj.put("id", ic.getLong(0));
                obj.put("gam3ya_id", ic.getLong(1));
                obj.put("installment_number", ic.getInt(2));
                obj.put("due_date", ic.getString(3));
                obj.put("amount", ic.getDouble(4));
                obj.put("is_paid", ic.getInt(5));
                obj.put("paid_date", ic.isNull(6) ? JSONObject.NULL : ic.getString(6));
                instArray.put(obj);
            }
        } finally {
            ic.close();
        }
        root.put("installments", instArray);

        JSONArray logsArray = new JSONArray();
        Cursor lc = db.rawQuery(
                "SELECT id, gam3ya_id, gam3ya_name, installment_id, installment_number, amount, paid_date, created_at, event_type FROM " + TABLE_PAYMENTS_LOG,
                null
        );
        try {
            while (lc.moveToNext()) {
                JSONObject obj = new JSONObject();
                obj.put("id", lc.getLong(0));
                obj.put("gam3ya_id", lc.getLong(1));
                obj.put("gam3ya_name", lc.getString(2));
                obj.put("installment_id", lc.getLong(3));
                obj.put("installment_number", lc.getInt(4));
                obj.put("amount", lc.getDouble(5));
                obj.put("paid_date", lc.getString(6));
                obj.put("created_at", lc.getLong(7));
                obj.put("event_type", lc.getString(8));
                logsArray.put(obj);
            }
        } finally {
            lc.close();
        }
        root.put("payments_log", logsArray);

        return root.toString(2);
    }

    public void restoreDatabaseFromJson(String jsonString) throws JSONException {
        JSONObject root = new JSONObject(jsonString);
        JSONArray gam3yatArray = root.getJSONArray("gam3yat");
        JSONArray instArray = root.getJSONArray("installments");
        JSONArray logsArray = root.optJSONArray("payments_log");

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_PAYMENTS_LOG, null, null);
            db.delete(TABLE_INSTALLMENTS, null, null);
            db.delete(TABLE_GAM3YAT, null, null);

            for (int i = 0; i < gam3yatArray.length(); i++) {
                JSONObject obj = gam3yatArray.getJSONObject(i);
                ContentValues v = new ContentValues();
                v.put("id", obj.getLong("id"));
                v.put("name", obj.getString("name"));
                v.put("monthly_amount", obj.getDouble("monthly_amount"));
                v.put("total_months", obj.getInt("total_months"));
                v.put("payout_month", obj.getInt("payout_month"));
                v.put("due_day", obj.getInt("due_day"));
                v.put("start_date", obj.getString("start_date"));
                v.put("total_amount", obj.getDouble("total_amount"));
                v.put("is_archived", obj.getInt("is_archived"));
                if (obj.isNull("archived_at")) {
                    v.putNull("archived_at");
                } else {
                    v.put("archived_at", obj.getString("archived_at"));
                }
                db.insertOrThrow(TABLE_GAM3YAT, null, v);
            }

            for (int i = 0; i < instArray.length(); i++) {
                JSONObject obj = instArray.getJSONObject(i);
                ContentValues v = new ContentValues();
                v.put("id", obj.getLong("id"));
                v.put("gam3ya_id", obj.getLong("gam3ya_id"));
                v.put("installment_number", obj.getInt("installment_number"));
                v.put("due_date", obj.getString("due_date"));
                v.put("amount", obj.getDouble("amount"));
                v.put("is_paid", obj.getInt("is_paid"));
                if (obj.isNull("paid_date")) {
                    v.putNull("paid_date");
                } else {
                    v.put("paid_date", obj.getString("paid_date"));
                }
                db.insertOrThrow(TABLE_INSTALLMENTS, null, v);
            }

            if (logsArray != null) {
                for (int i = 0; i < logsArray.length(); i++) {
                    JSONObject obj = logsArray.getJSONObject(i);
                    ContentValues v = new ContentValues();
                    v.put("id", obj.getLong("id"));
                    v.put("gam3ya_id", obj.getLong("gam3ya_id"));
                    v.put("gam3ya_name", obj.getString("gam3ya_name"));
                    v.put("installment_id", obj.getLong("installment_id"));
                    v.put("installment_number", obj.getInt("installment_number"));
                    v.put("amount", obj.getDouble("amount"));
                    v.put("paid_date", obj.getString("paid_date"));
                    v.put("created_at", obj.getLong("created_at"));
                    v.put("event_type", obj.optString("event_type", PaymentLog.TYPE_PAY));
                    db.insertOrThrow(TABLE_PAYMENTS_LOG, null, v);
                }
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}

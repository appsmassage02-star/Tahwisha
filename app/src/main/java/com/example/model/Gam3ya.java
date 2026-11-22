package com.example.model;

public class Gam3ya {
    public long id;
    public String name;
    public double monthlyAmount;
    public int totalMonths;
    public int payoutMonth;
    public int dueDay;
    public String startDate;
    public double totalAmount;
    public boolean isArchived;
    public String archivedAt;

    // Computed summary fields
    public double paidAmount;
    public double remainingAmount;
    public int paidCount;
    public String nextDueDate;
    public int nextInstallmentNumber;
    public int overdueCount;
    public int dueSoonCount;
    public boolean isNextPayoutMonth;
}

package com.example.model;

public class Installment {
    public static final int STATUS_PAID = 0;
    public static final int STATUS_OVERDUE = 1;
    public static final int STATUS_DUE = 2;
    public static final int STATUS_UPCOMING = 3;

    public long id;
    public long gam3yaId;
    public int installmentNumber;
    public String dueDate;
    public double amount;
    public boolean isPaid;
    public String paidDate;
}

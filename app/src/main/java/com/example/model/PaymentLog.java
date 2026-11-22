package com.example.model;

public class PaymentLog {
    public static final String TYPE_PAY = "PAY";
    public static final String TYPE_UNDO = "UNDO";
    public static final String TYPE_ARCHIVE = "ARCHIVE";

    public long id;
    public long gam3yaId;
    public String gam3yaName;
    public long installmentId;
    public int installmentNumber;
    public double amount;
    public String paidDate;
    public long createdAt;
    public String eventType = TYPE_PAY;
}

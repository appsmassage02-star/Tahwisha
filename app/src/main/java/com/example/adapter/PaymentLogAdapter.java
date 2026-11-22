package com.example.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.R;
import com.example.model.PaymentLog;
import com.example.util.DateUtils;
import com.example.util.ThemeManager;

import java.util.List;

public class PaymentLogAdapter extends BaseAdapter {

    private final Context context;
    private final List<PaymentLog> items;
    private final LayoutInflater inflater;

    public PaymentLogAdapter(Context context, List<PaymentLog> items) {
        this.context = context;
        this.items = items;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_payment_log, parent, false);
            holder = new ViewHolder();
            holder.tvName = convertView.findViewById(R.id.tvReportGam3yaName);
            holder.tvEventType = convertView.findViewById(R.id.tvReportEventType);
            holder.tvPaidDate = convertView.findViewById(R.id.tvReportPaidDate);
            holder.tvAmount = convertView.findViewById(R.id.tvReportAmount);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        PaymentLog log = items.get(position);
        holder.tvName.setText(log.gam3yaName);

        String type = log.eventType != null ? log.eventType : PaymentLog.TYPE_PAY;
        if (PaymentLog.TYPE_UNDO.equals(type)) {
            int overdueTextColor = ThemeManager.resolveAttrColor(context, R.attr.colorStatusOverdueText);
            holder.tvEventType.setText(R.string.audit_undo);
            holder.tvEventType.setBackgroundResource(R.drawable.bg_badge_overdue);
            holder.tvEventType.setTextColor(overdueTextColor);

            holder.tvAmount.setText("-" + DateUtils.formatAmount(log.amount));
            holder.tvAmount.setBackgroundResource(R.drawable.bg_badge_overdue);
            holder.tvAmount.setTextColor(overdueTextColor);
            holder.tvPaidDate.setText(
                    "تاريخ التراجع: " + DateUtils.formatDisplayDate(log.paidDate) +
                            "  •  قسط #" + log.installmentNumber
            );
        } else if (PaymentLog.TYPE_ARCHIVE.equals(type)) {
            int payoutTextColor = ThemeManager.resolveAttrColor(context, R.attr.colorStatusPayoutText);
            holder.tvEventType.setText(R.string.audit_archive);
            holder.tvEventType.setBackgroundResource(R.drawable.bg_badge_payout);
            holder.tvEventType.setTextColor(payoutTextColor);

            holder.tvAmount.setText(DateUtils.formatAmount(log.amount));
            holder.tvAmount.setBackgroundResource(R.drawable.bg_badge_payout);
            holder.tvAmount.setTextColor(payoutTextColor);
            holder.tvPaidDate.setText(
                    "تاريخ الأرشفة: " + DateUtils.formatDisplayDate(log.paidDate) +
                            "  •  اكتمال " + log.installmentNumber + " أقساط"
            );
        } else {
            int paidTextColor = ThemeManager.resolveAttrColor(context, R.attr.colorStatusPaidText);
            holder.tvEventType.setText(R.string.audit_pay);
            holder.tvEventType.setBackgroundResource(R.drawable.bg_badge_paid);
            holder.tvEventType.setTextColor(paidTextColor);

            holder.tvAmount.setText("+" + DateUtils.formatAmount(log.amount));
            holder.tvAmount.setBackgroundResource(R.drawable.bg_badge_paid);
            holder.tvAmount.setTextColor(paidTextColor);
            holder.tvPaidDate.setText(
                    "تاريخ السداد: " + DateUtils.formatDisplayDate(log.paidDate) +
                            "  •  قسط #" + log.installmentNumber
            );
        }

        return convertView;
    }

    private static class ViewHolder {
        TextView tvName;
        TextView tvEventType;
        TextView tvPaidDate;
        TextView tvAmount;
    }
}

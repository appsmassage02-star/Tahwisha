package com.example.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.R;
import com.example.model.Installment;
import com.example.util.DateUtils;
import com.example.util.ThemeManager;

import java.util.List;

public class InstallmentAdapter extends BaseAdapter {

    private final Context context;
    private final List<Installment> items;
    private final int payoutMonth;
    private final String todayIso;
    private final LayoutInflater inflater;

    public InstallmentAdapter(Context context, List<Installment> items, int payoutMonth, String todayIso) {
        this.context = context;
        this.items = items;
        this.payoutMonth = payoutMonth;
        this.todayIso = todayIso;
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
            convertView = inflater.inflate(R.layout.item_installment, parent, false);
            holder = new ViewHolder();
            holder.tvTitle = convertView.findViewById(R.id.tvInstallmentTitle);
            holder.tvPayoutBadge = convertView.findViewById(R.id.tvPayoutBadge);
            holder.tvDueDate = convertView.findViewById(R.id.tvInstallmentDueDate);
            holder.tvPaidDate = convertView.findViewById(R.id.tvInstallmentPaidDate);
            holder.tvAmount = convertView.findViewById(R.id.tvInstallmentAmount);
            holder.tvStatus = convertView.findViewById(R.id.tvInstallmentStatus);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Installment inst = items.get(position);
        String monthLabel = DateUtils.getArabicMonthYear(inst.dueDate);
        holder.tvTitle.setText("القسط #" + inst.installmentNumber + " - " + monthLabel);
        holder.tvDueDate.setText("تاريخ الاستحقاق: " + DateUtils.formatDisplayDate(inst.dueDate));
        holder.tvAmount.setText(DateUtils.formatAmount(inst.amount));

        if (inst.installmentNumber == payoutMonth) {
            holder.tvPayoutBadge.setVisibility(View.VISIBLE);
        } else {
            holder.tvPayoutBadge.setVisibility(View.GONE);
        }

        int status = DateUtils.computeInstallmentStatus(inst, todayIso);
        switch (status) {
            case Installment.STATUS_PAID:
                holder.tvStatus.setText(R.string.status_paid);
                holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_paid);
                holder.tvStatus.setTextColor(ThemeManager.resolveAttrColor(context, R.attr.colorStatusPaidText));
                if (inst.paidDate != null && !inst.paidDate.isEmpty()) {
                    holder.tvPaidDate.setVisibility(View.VISIBLE);
                    holder.tvPaidDate.setText("تم السداد: " + DateUtils.formatDisplayDate(inst.paidDate));
                } else {
                    holder.tvPaidDate.setVisibility(View.GONE);
                }
                break;

            case Installment.STATUS_OVERDUE:
                holder.tvStatus.setText(R.string.status_overdue);
                holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_overdue);
                holder.tvStatus.setTextColor(ThemeManager.resolveAttrColor(context, R.attr.colorStatusOverdueText));
                holder.tvPaidDate.setVisibility(View.GONE);
                break;

            case Installment.STATUS_DUE:
                holder.tvStatus.setText(R.string.status_due);
                holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_due);
                holder.tvStatus.setTextColor(ThemeManager.resolveAttrColor(context, R.attr.colorStatusDueText));
                holder.tvPaidDate.setVisibility(View.GONE);
                break;

            default:
                holder.tvStatus.setText(R.string.status_upcoming);
                holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_upcoming);
                holder.tvStatus.setTextColor(ThemeManager.resolveAttrColor(context, R.attr.colorStatusUpcomingText));
                holder.tvPaidDate.setVisibility(View.GONE);
                break;
        }

        return convertView;
    }

    private static class ViewHolder {
        TextView tvTitle;
        TextView tvPayoutBadge;
        TextView tvDueDate;
        TextView tvPaidDate;
        TextView tvAmount;
        TextView tvStatus;
    }
}

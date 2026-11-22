package com.example.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.R;
import com.example.model.Gam3ya;
import com.example.util.DateUtils;
import com.example.util.ThemeManager;

import java.util.List;

public class Gam3yaAdapter extends BaseAdapter {

    private final Context context;
    private final List<Gam3ya> items;
    private final LayoutInflater inflater;

    public Gam3yaAdapter(Context context, List<Gam3ya> items) {
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
            convertView = inflater.inflate(R.layout.item_gam3ya, parent, false);
            holder = new ViewHolder();
            holder.tvName = convertView.findViewById(R.id.tvItemGam3yaName);
            holder.tvPayoutBadge = convertView.findViewById(R.id.tvItemPayoutBadge);
            holder.tvAlertBadge = convertView.findViewById(R.id.tvItemAlertBadge);
            holder.pbProgress = convertView.findViewById(R.id.pbItemProgress);
            holder.tvProgressText = convertView.findViewById(R.id.tvItemProgressText);
            holder.tvTotal = convertView.findViewById(R.id.tvItemTotal);
            holder.tvPaid = convertView.findViewById(R.id.tvItemPaid);
            holder.tvRemaining = convertView.findViewById(R.id.tvItemRemaining);
            holder.tvPayoutNote = convertView.findViewById(R.id.tvItemPayoutNote);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Gam3ya g = items.get(position);
        holder.tvName.setText(g.name);
        holder.tvTotal.setText(DateUtils.formatAmount(g.totalAmount));
        holder.tvPaid.setText(DateUtils.formatAmount(g.paidAmount));
        holder.tvRemaining.setText(DateUtils.formatAmount(g.remainingAmount));

        int progressPercent = g.totalMonths > 0
                ? (int) Math.round((g.paidCount * 100.0) / g.totalMonths)
                : 0;
        holder.pbProgress.setProgress(progressPercent);
        holder.tvProgressText.setText(
                g.paidCount + " من " + g.totalMonths + " أشهر - " + progressPercent + "%"
        );

        if (g.overdueCount > 0) {
            holder.tvAlertBadge.setVisibility(View.VISIBLE);
            holder.tvAlertBadge.setText("متأخر (" + g.overdueCount + ")");
            holder.tvAlertBadge.setBackgroundResource(R.drawable.bg_badge_overdue);
            holder.tvAlertBadge.setTextColor(ThemeManager.resolveAttrColor(context, R.attr.colorStatusOverdueText));
        } else if (g.dueSoonCount > 0) {
            holder.tvAlertBadge.setVisibility(View.VISIBLE);
            holder.tvAlertBadge.setText("مستحق الآن");
            holder.tvAlertBadge.setBackgroundResource(R.drawable.bg_badge_due);
            holder.tvAlertBadge.setTextColor(ThemeManager.resolveAttrColor(context, R.attr.colorStatusDueText));
        } else {
            holder.tvAlertBadge.setVisibility(View.GONE);
        }

        if (g.isNextPayoutMonth && g.nextDueDate != null) {
            holder.tvPayoutBadge.setVisibility(View.VISIBLE);
            holder.tvPayoutNote.setVisibility(View.VISIBLE);
            holder.tvPayoutNote.setText(
                    "ميعاد القبض القادم: قسط " + g.payoutMonth + " (" +
                            DateUtils.getArabicMonthYear(g.nextDueDate) + " - " +
                            DateUtils.formatDisplayDate(g.nextDueDate) + ")"
            );
        } else {
            holder.tvPayoutBadge.setVisibility(View.GONE);
            holder.tvPayoutNote.setVisibility(View.GONE);
        }

        return convertView;
    }

    private static class ViewHolder {
        TextView tvName;
        TextView tvPayoutBadge;
        TextView tvAlertBadge;
        ProgressBar pbProgress;
        TextView tvProgressText;
        TextView tvTotal;
        TextView tvPaid;
        TextView tvRemaining;
        TextView tvPayoutNote;
    }
}

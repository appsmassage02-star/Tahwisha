package com.example.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.R;
import com.example.model.Gam3ya;
import com.example.util.DateUtils;

import java.util.List;

public class ArchiveAdapter extends BaseAdapter {

    private final List<Gam3ya> items;
    private final LayoutInflater inflater;

    public ArchiveAdapter(Context context, List<Gam3ya> items) {
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
            convertView = inflater.inflate(R.layout.item_archive_gam3ya, parent, false);
            holder = new ViewHolder();
            holder.tvName = convertView.findViewById(R.id.tvArchiveName);
            holder.tvDate = convertView.findViewById(R.id.tvArchiveDate);
            holder.tvTotal = convertView.findViewById(R.id.tvArchiveTotal);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Gam3ya g = items.get(position);
        holder.tvName.setText(g.name);
        holder.tvDate.setText("تاريخ الأرشفة: " + DateUtils.formatDisplayDate(g.archivedAt));
        holder.tvTotal.setText(DateUtils.formatAmount(g.totalAmount));

        return convertView;
    }

    private static class ViewHolder {
        TextView tvName;
        TextView tvDate;
        TextView tvTotal;
    }
}

package com.burdew.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.burdew.app.R;

import java.util.ArrayList;
import java.util.List;

/** One adapter for every two-line list in the app. */
public class RowAdapter<T> extends RecyclerView.Adapter<RowAdapter.Holder> {

    public interface Binder<T> {
        /** Returns {title, subtitle, badge}; badge may be null to hide it. */
        String[] bind(T item);
    }

    public interface Click<T> { void onClick(T item); }

    private final List<T> items = new ArrayList<>();
    private final Binder<T> binder;
    private final Click<T> click;

    public RowAdapter(Binder<T> binder, Click<T> click) {
        this.binder = binder;
        this.click = click;
    }

    public void submit(List<T> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        T item = items.get(position);
        String[] t = binder.bind(item);
        h.title.setText(t[0]);
        h.subtitle.setText(t.length > 1 ? t[1] : "");
        h.subtitle.setVisibility(t.length > 1 && t[1] != null && !t[1].isEmpty() ? View.VISIBLE : View.GONE);
        boolean badge = t.length > 2 && t[2] != null;
        h.badge.setVisibility(badge ? View.VISIBLE : View.GONE);
        if (badge) h.badge.setText(t[2]);
        h.itemView.setOnClickListener(v -> { if (click != null) click.onClick(item); });
    }

    @Override public int getItemCount() { return items.size(); }

    public static class Holder extends RecyclerView.ViewHolder {
        final TextView title, subtitle, badge;
        Holder(View v) {
            super(v);
            title = v.findViewById(R.id.title);
            subtitle = v.findViewById(R.id.subtitle);
            badge = v.findViewById(R.id.badge);
        }
    }
}

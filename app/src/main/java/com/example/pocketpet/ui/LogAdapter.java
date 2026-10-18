package com.example.pocketpet.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.pocketpet.data.GrowthLog;
import com.example.pocketpet.databinding.ItemRowBinding;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogAdapter extends RecyclerView.Adapter<RowHolder> {
    private List<GrowthLog> logs = new ArrayList<>();
    private final SimpleDateFormat fmt = new SimpleDateFormat("d MMM, HH:mm", Locale.getDefault());

    public void setLogs(List<GrowthLog> v) {
        logs = v;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new RowHolder(ItemRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RowHolder h, int position) {
        GrowthLog l = logs.get(position);
        h.b.rowTitle.setText(l.text);
        h.b.rowSub.setText(fmt.format(new Date(l.time)));
    }

    @Override
    public int getItemCount() { return logs.size(); }
}

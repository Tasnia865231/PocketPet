package com.example.pocketpet.ui;

import androidx.recyclerview.widget.RecyclerView;
import com.example.pocketpet.databinding.ItemRowBinding;

public class RowHolder extends RecyclerView.ViewHolder {
    public final ItemRowBinding b;

    public RowHolder(ItemRowBinding b) {
        super(b.getRoot());
        this.b = b;
    }
}

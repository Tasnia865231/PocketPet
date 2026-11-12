package com.example.pocketpet.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.pocketpet.data.Item;
import com.example.pocketpet.databinding.ItemRowBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ItemAdapter extends RecyclerView.Adapter<RowHolder> {
    private List<Item> items = new ArrayList<>();
    private final boolean shop;
    private final Consumer<Item> onClick;
    private int petLevel = 1; // used in shop mode to gate locked items

    public ItemAdapter(boolean shop, Consumer<Item> onClick) {
        this.shop = shop;
        this.onClick = onClick;
    }

    /** Call from ShopActivity after loading the pet so lock status is correct. */
    public void setPetLevel(int level) {
        this.petLevel = level;
        notifyDataSetChanged();
    }

    public void setItems(List<Item> v) {
        items = v;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new RowHolder(ItemRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RowHolder h, int position) {
        Item item = items.get(position);
        h.b.rowTitle.setText(item.name);

        if (shop) {
            boolean locked = item.minLevel > petLevel;
            if (locked) {
                h.b.rowSub.setText(item.type + " | Unlocks at level " + item.minLevel);
                h.b.getRoot().setAlpha(0.5f);
                h.b.getRoot().setOnClickListener(null);
                h.b.getRoot().setClickable(false);
            } else {
                h.b.rowSub.setText(item.type + " | " + item.price + " coins | owned " + item.owned);
                h.b.getRoot().setAlpha(1.0f);
                h.b.getRoot().setClickable(true);
                h.b.getRoot().setOnClickListener(v -> onClick.accept(item));
            }
        } else {
            h.b.rowSub.setText(item.type + " | x" + item.owned);
            h.b.getRoot().setAlpha(1.0f);
            h.b.getRoot().setClickable(true);
            h.b.getRoot().setOnClickListener(v -> onClick.accept(item));
        }
    }

    @Override
    public int getItemCount() { return items.size(); }
}

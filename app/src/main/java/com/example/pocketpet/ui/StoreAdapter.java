package com.example.pocketpet.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pocketpet.R;
import com.example.pocketpet.data.Item;
import com.example.pocketpet.data.Pet;
import com.example.pocketpet.databinding.ItemStoreCardBinding;
import com.example.pocketpet.view.ItemArt;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Picture-card grid used by both the Shop and the Bag. The caller picks the tab (Food / Dresses); Dresses
 * are split into "Hats" and "Eggshells" sections. In the Bag only owned items are listed.
 */
public class StoreAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int HEADER = 0, CARD = 1;

    /** One row of the grid: a section header or an item. */
    private static class Row {
        final String header; final Item item;
        Row(String header, Item item) { this.header = header; this.item = item; }
    }

    private final boolean shop;
    private final Consumer<Item> onClick;
    private final List<Row> rows = new ArrayList<>();
    private Pet pet = new Pet();

    public StoreAdapter(boolean shop, Consumer<Item> onClick) {
        this.shop = shop;
        this.onClick = onClick;
    }

    /** The built-in spotted shell: free, always owned, lets the player switch back from another shell. */
    public static Item defaultShell() {
        Item d = new Item(-1, ItemArt.SHELL_DEFAULT, "shell", 0, 0, 1);
        d.owned = 1;
        return d;
    }

    /** Rebuilds the list. dresses = false shows Food, true shows Hats + Eggshells. Returns the number of items shown. */
    public int setData(List<Item> all, Pet pet, boolean dresses) {
        this.pet = pet;
        rows.clear();
        int count = 0;
        if (!dresses) {
            for (Item it : sorted(all, "food")) { rows.add(new Row(null, it)); count++; }
        } else {
            List<Item> hats = sorted(all, "hat");
            if (!hats.isEmpty()) {
                rows.add(new Row("Hats", null));
                for (Item it : hats) { rows.add(new Row(null, it)); count++; }
            }
            List<Item> shells = sorted(all, "shell");
            shells.add(0, defaultShell());
            rows.add(new Row("Eggshells", null));
            for (Item it : shells) { rows.add(new Row(null, it)); count++; }
        }
        notifyDataSetChanged();
        return count;
    }

    /** Items of one type in unlock order; the Bag only keeps what is owned. */
    private List<Item> sorted(List<Item> all, String type) {
        List<Item> out = new ArrayList<>();
        for (Item it : all) {
            if (!type.equals(it.type)) continue;
            if (!shop && it.owned <= 0) continue;
            out.add(it);
        }
        out.sort((a, b) -> a.minLevel != b.minLevel ? Integer.compare(a.minLevel, b.minLevel) : Integer.compare(a.id, b.id));
        return out;
    }

    public boolean isHeader(int position) { return rows.get(position).header != null; }

    @Override public int getItemViewType(int position) { return isHeader(position) ? HEADER : CARD; }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater in = LayoutInflater.from(parent.getContext());
        if (viewType == HEADER) {
            View v = in.inflate(R.layout.item_store_header, parent, false);
            return new RecyclerView.ViewHolder(v) {};
        }
        return new CardHolder(ItemStoreCardBinding.inflate(in, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (row.header != null) {
            ((TextView) holder.itemView).setText(row.header);
            return;
        }
        ItemStoreCardBinding b = ((CardHolder) holder).b;
        Item it = row.item;
        boolean food = "food".equals(it.type);
        boolean locked = shop && it.minLevel > pet.level;
        b.icon.setItem(it.type, it.name, locked);
        b.name.setText(it.name);
        b.getRoot().setAlpha(1f);
        b.getRoot().setClickable(true);

        if (shop) {
            if (locked) {
                b.detail.setText("Unlocks at level " + it.minLevel);
                chip(b.chip, "Lv " + it.minLevel, R.drawable.bg_chip_lock, 0xFF8A7A66);
                b.getRoot().setAlpha(0.8f);
            } else if (!food && it.owned >= 1) {
                b.detail.setText(it.id == -1 ? "Mochi's first shell" : "In your bag");
                chip(b.chip, "Owned", R.drawable.bg_chip_owned, 0xFF4F7A45);
            } else {
                b.detail.setText(food ? "+" + it.effect + " hunger" + (it.owned > 0 ? "  |  have " + it.owned : "")
                        : ("hat".equals(it.type) ? "Buy once, keep forever" : "Buy once, keep forever"));
                boolean afford = pet.coins >= it.price;
                chip(b.chip, "\uD83E\uDE99 " + it.price, R.drawable.bg_price_chip, afford ? 0xFF7A5238 : 0xFFB5483E);
            }
        } else {
            if (food) {
                b.detail.setText("+" + it.effect + " hunger");
                chip(b.chip, "x" + it.owned, R.drawable.bg_price_chip, 0xFF7A5238);
            } else {
                boolean wearing = "hat".equals(it.type) ? it.name.equals(pet.hat)
                        : (ItemArt.isDefaultShell(pet.shell) ? it.id == -1 : it.name.equals(pet.shell));
                b.detail.setText(wearing ? "Tap to take off" : "Tap to wear");
                if (it.id == -1) b.detail.setText(wearing ? "Wearing now" : "Tap to wear");
                chip(b.chip, wearing ? "Wearing" : "Wear", wearing ? R.drawable.bg_chip_wear : R.drawable.bg_chip_owned,
                        wearing ? 0xFFB5486F : 0xFF4F7A45);
            }
        }
        b.getRoot().setOnClickListener(v -> onClick.accept(it));
    }

    private static void chip(TextView t, String text, int bg, int color) {
        t.setText(text);
        t.setBackgroundResource(bg);
        t.setTextColor(color);
    }

    @Override public int getItemCount() { return rows.size(); }

    static class CardHolder extends RecyclerView.ViewHolder {
        final ItemStoreCardBinding b;
        CardHolder(ItemStoreCardBinding b) { super(b.getRoot()); this.b = b; }
    }
}

package com.example.pocketpet.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.pocketpet.data.Item;
import com.example.pocketpet.databinding.ItemFoodCardBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Horizontal RecyclerView adapter for the food-menu overlay in MainActivity.
 * Only shows food items (type == "food") that the player owns (owned > 0).
 */
public class FoodMenuAdapter extends RecyclerView.Adapter<FoodMenuAdapter.FoodHolder> {

    private List<Item> items = new ArrayList<>();
    private final Consumer<Item> onClick;

    public FoodMenuAdapter(Consumer<Item> onClick) {
        this.onClick = onClick;
    }

    public void setItems(List<Item> all) {
        items = new ArrayList<>();
        for (Item it : all) {
            if ("food".equals(it.type) && it.owned > 0) items.add(it);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FoodHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new FoodHolder(ItemFoodCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull FoodHolder h, int position) {
        Item item = items.get(position);
        h.b.foodSprite.setFoodName(item.name);
        h.b.foodName.setText(item.name);
        h.b.foodQty.setText("x" + item.owned);
        h.b.getRoot().setOnClickListener(v -> onClick.accept(item));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class FoodHolder extends RecyclerView.ViewHolder {
        final ItemFoodCardBinding b;
        FoodHolder(ItemFoodCardBinding b) { super(b.getRoot()); this.b = b; }
    }
}

package com.example.pocketpet.ui;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.pocketpet.Bg;
import com.example.pocketpet.data.Item;
import com.example.pocketpet.data.Pet;
import com.example.pocketpet.data.PetRepository;
import com.example.pocketpet.databinding.ActivityListBinding;
import java.util.List;

public class ShopActivity extends AppCompatActivity {
    private ActivityListBinding b;
    private PetRepository repo;
    private final ItemAdapter adapter = new ItemAdapter(true, this::buy);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        repo = new PetRepository(this);
        b.title.setText("Shop");
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        refresh();
    }

    private void refresh() {
        Bg.IO.execute(() -> {
            Pet pet = repo.load();
            List<Item> items = repo.items();
            Bg.UI.post(() -> {
                b.subtitle.setText("Coins: " + pet.coins + "  (tap an item to buy)");
                adapter.setItems(items);
            });
        });
    }

    private void buy(Item item) {
        Bg.IO.execute(() -> {
            boolean ok = repo.buy(item);
            Bg.UI.post(() -> {
                Toast.makeText(this, ok ? "Bought " + item.name : "Not enough coins", Toast.LENGTH_SHORT).show();
                refresh();
            });
        });
    }
}

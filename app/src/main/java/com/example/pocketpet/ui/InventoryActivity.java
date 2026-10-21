package com.example.pocketpet.ui;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.pocketpet.Bg;
import com.example.pocketpet.data.Item;
import com.example.pocketpet.data.PetRepository;
import com.example.pocketpet.databinding.ActivityListBinding;
import java.util.List;

public class InventoryActivity extends AppCompatActivity {
    private ActivityListBinding b;
    private PetRepository repo;
    private final ItemAdapter adapter = new ItemAdapter(false, this::use);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        repo = new PetRepository(this);
        b.title.setText("Bag");
        b.subtitle.setText("Tap food to feed, tap a hat to wear it");
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        b.btnBack.setOnClickListener(v -> finish());
        refresh();
    }

    private void refresh() {
        Bg.IO.execute(() -> {
            List<Item> items = repo.ownedItems();
            Bg.UI.post(() -> adapter.setItems(items));
        });
    }

    private void use(Item item) {
        Bg.IO.execute(() -> {
            String msg = repo.use(item);
            Bg.UI.post(() -> {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                refresh();
            });
        });
    }
}

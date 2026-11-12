package com.example.pocketpet.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.pocketpet.Bg;
import com.example.pocketpet.data.Item;
import com.example.pocketpet.data.Pet;
import com.example.pocketpet.data.PetRepository;
import com.example.pocketpet.databinding.ActivityStoreBinding;

import java.util.List;

/** Bag: shows only what the player owns right now, split into Food and Dresses (hats + eggshells). */
public class InventoryActivity extends AppCompatActivity {
    private ActivityStoreBinding b;
    private PetRepository repo;
    private final StoreAdapter adapter = new StoreAdapter(false, this::use);
    private boolean dresses = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityStoreBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        repo = new PetRepository(this);
        b.title.setText("Bag");
        b.coinChip.setVisibility(View.GONE);
        GridLayoutManager lm = new GridLayoutManager(this, 2);
        lm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) { return adapter.isHeader(position) ? 2 : 1; }
        });
        b.list.setLayoutManager(lm);
        b.list.setAdapter(adapter);
        b.btnBack.setOnClickListener(v -> finish());
        b.tabFood.setOnClickListener(v -> selectTab(false));
        b.tabDress.setOnClickListener(v -> selectTab(true));
        StoreTabs.style(b.tabFood, b.tabDress, dresses);
        refresh();
    }

    // Live updates: eating or shopping somewhere else changes what is in the bag
    private final android.database.ContentObserver observer = new android.database.ContentObserver(new android.os.Handler(android.os.Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange) { refresh(); }
    };

    @Override protected void onStart() {
        super.onStart();
        getContentResolver().registerContentObserver(com.example.pocketpet.system.PetProvider.PET, true, observer);
        getContentResolver().registerContentObserver(com.example.pocketpet.system.PetProvider.ITEMS, true, observer);
    }

    @Override protected void onStop() {
        super.onStop();
        getContentResolver().unregisterContentObserver(observer);
    }

    private void selectTab(boolean dress) {
        dresses = dress;
        StoreTabs.style(b.tabFood, b.tabDress, dresses);
        refresh();
    }

    private void refresh() {
        Bg.IO.execute(() -> {
            Pet pet = repo.load();
            List<Item> items = repo.ownedItems();
            Bg.UI.post(() -> {
                int count = adapter.setData(items, pet, dresses);
                b.subtitle.setText(dresses ? "Tap a hat or eggshell to wear it" : "Tap food to feed Mochi");
                boolean empty = !dresses && count == 0;
                b.emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
                b.emptyText.setText("Your bag has no food yet.\nVisit the Shop to buy some yummy snacks!");
            });
        });
    }

    private void use(Item item) {
        Bg.IO.execute(() -> {
            String msg;
            if (item.id == -1) {                       // the built-in spotted shell
                repo.edit(p -> { p.shell = ""; return p; });
                msg = "Back to the spotted shell";
            } else {
                msg = repo.use(item);                  // food is eaten (and leaves the bag); hats / shells toggle
            }
            Bg.UI.post(() -> {
                KawaiiToast.show(this, msg);
                refresh();
            });
        });
    }
}

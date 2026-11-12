package com.example.pocketpet.ui;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.pocketpet.Bg;
import com.example.pocketpet.data.Item;
import com.example.pocketpet.data.Pet;
import com.example.pocketpet.data.PetRepository;
import com.example.pocketpet.databinding.ActivityStoreBinding;

import java.util.List;

/** Shop: Food and Dresses (hats + eggshells) as picture cards. Hats and eggshells can be bought only once. */
public class ShopActivity extends AppCompatActivity {
    private ActivityStoreBinding b;
    private PetRepository repo;
    private final StoreAdapter adapter = new StoreAdapter(true, this::onItemTapped);
    private boolean dresses = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityStoreBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        repo = new PetRepository(this);
        b.title.setText("Shop");
        b.subtitle.setText("Tap a card to buy. New treats unlock as Mochi levels up!");
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

    // A ContentObserver on the provider: coins or items changed somewhere else (notification button, widget)? Reload.
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
            List<Item> items = repo.items();
            Bg.UI.post(() -> {
                b.coinChip.setText("\uD83E\uDE99 " + pet.coins);
                adapter.setData(items, pet, dresses);
                b.emptyText.setVisibility(android.view.View.GONE);
            });
        });
    }

    private void onItemTapped(Item item) {
        if (item.id == -1 || (!"food".equals(item.type) && item.owned >= 1)) {
            KawaiiToast.show(this, "You already own " + item.name);
            return;
        }
        KawaiiDialog.show(this, 0, "Buy " + item.name + "?",
                "It costs " + item.price + " coins" + ("food".equals(item.type) ? "." : ", and you keep it forever."),
                "Buy", () -> buy(item), "Not now", null);
    }

    private void buy(Item item) {
        Bg.IO.execute(() -> {
            String r = repo.tryBuy(item);
            Bg.UI.post(() -> {
                String msg;
                switch (r) {
                    case PetRepository.BUY_OK: msg = "Bought " + item.name + "!"; break;
                    case PetRepository.BUY_COINS: msg = "Not enough coins yet"; break;
                    case PetRepository.BUY_LOCKED: msg = "Mochi needs to level up first"; break;
                    case PetRepository.BUY_OWNED: msg = "You already own " + item.name; break;
                    default: msg = "Something went wrong"; break;
                }
                KawaiiToast.show(this, msg);
                refresh();
            });
        });
    }
}

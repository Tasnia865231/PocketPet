package com.example.pocketpet.ui;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.pocketpet.Bg;
import com.example.pocketpet.data.GrowthLog;
import com.example.pocketpet.data.Pet;
import com.example.pocketpet.data.PetRepository;
import com.example.pocketpet.databinding.ActivityListBinding;
import java.util.List;

public class StatsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ActivityListBinding b = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        final PetRepository repo = new PetRepository(this);
        final LogAdapter adapter = new LogAdapter();
        b.title.setText("Growth History");
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        b.btnBack.setOnClickListener(v -> finish());
        Bg.IO.execute(() -> {
            Pet pet = repo.load();
            // Derive stage from level (0=lv1-2, 1=lv3-5, 2=lv6+)
            int stage = pet.level >= 6 ? 2 : pet.level >= 3 ? 1 : 0;
            int xpNeeded = 50 * pet.level;
            List<GrowthLog> logs = repo.logs();
            Bg.UI.post(() -> {
                b.subtitle.setText("Level " + pet.level
                        + "  |  XP " + pet.xp + "/" + xpNeeded
                        + "  |  Stage " + (stage + 1)
                        + "  |  Coins " + pet.coins);
                adapter.setLogs(logs);
            });
        });
    }
}

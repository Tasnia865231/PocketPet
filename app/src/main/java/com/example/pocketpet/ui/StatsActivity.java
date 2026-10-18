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
        b.title.setText("Growth history");
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        Bg.IO.execute(() -> {
            Pet pet = repo.load();
            List<GrowthLog> logs = repo.logs();
            Bg.UI.post(() -> {
                b.subtitle.setText("XP " + pet.xp + "  |  Stage " + (pet.stage + 1) + "  |  Coins " + pet.coins);
                adapter.setLogs(logs);
            });
        });
    }
}

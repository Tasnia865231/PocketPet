package com.example.pocketpet.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.pocketpet.R;
import com.example.pocketpet.databinding.ItemActivityBinding;
import com.example.pocketpet.firebase.ActivityEvent;
import com.example.pocketpet.firebase.FirebaseAuthManager;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Draws the community feed: a little icon, a sentence like "Bunny reached Level 5!", and how long ago. */
public class ActivityAdapter extends RecyclerView.Adapter<ActivityAdapter.VH> {
    static class VH extends RecyclerView.ViewHolder {
        final ItemActivityBinding b;
        VH(ItemActivityBinding b) { super(b.getRoot()); this.b = b; }
    }

    private List<ActivityEvent> events = new ArrayList<>();
    private Map<String, String> names = new HashMap<>();       // uid -> display name (from the status list)

    public void setEvents(List<ActivityEvent> v) { events = v; notifyDataSetChanged(); }
    public void setNames(Map<String, String> v)  { names = v; notifyDataSetChanged(); }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemActivityBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ActivityEvent e = events.get(position);
        String name = names.containsKey(e.uid) ? names.get(e.uid) : "A player";
        if (e.uid.equals(FirebaseAuthManager.uid())) name += " (you)";
        String text;
        int icon, card;
        switch (e.type) {
            case ActivityEvent.LEVEL:
                text = name + " reached Level " + e.value + "!";
                icon = R.drawable.ic_star_cute; card = R.drawable.bg_pcard_butter; break;
            case ActivityEvent.COINS:
                text = name + " earned " + NumberFormat.getIntegerInstance().format(e.value) + " coins in total!";
                icon = R.drawable.ic_coin_cute; card = R.drawable.bg_pcard_peach; break;
            default:      // mini-game: how many were played, and the coins the player has afterwards
                String played = e.value > 1 ? "completed " + e.value + " mini-games" : "completed a mini-game";
                text = e.coins >= 0
                        ? name + " " + played + " and now has " + NumberFormat.getIntegerInstance().format(e.coins) + " coins!"
                        : name + " " + played + "!";
                icon = R.drawable.ic_game_cute; card = R.drawable.bg_pcard_lilac;
        }
        h.b.getRoot().setBackgroundResource(card);       // yellow = level, peach = coins, lilac = mini-game
        h.b.activityText.setText(text);
        h.b.activityIcon.setImageResource(icon);
        h.b.activityTime.setText(PlayerStatusAdapter.ago(e.createdAt));
    }

    @Override public int getItemCount() { return events.size(); }
}

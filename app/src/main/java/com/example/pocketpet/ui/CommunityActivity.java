package com.example.pocketpet.ui;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.pocketpet.Bg;
import com.example.pocketpet.databinding.ActivityCommunityBinding;
import com.example.pocketpet.firebase.ActivityFeed;
import com.example.pocketpet.firebase.MatchManager;
import com.example.pocketpet.firebase.PlayerProfileRepository;
import com.example.pocketpet.firebase.PlayerStatus;
import com.example.pocketpet.firebase.PresenceManager;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Community screen, built like the Shop: two coloured tabs, one section at a time.
 *   Tab 1 "Mochi Friends":      who is online + each player's coins (Realtime Database + Firestore listeners)
 *   Tab 2 "What is happening":  the live activity feed (Realtime Database listener)
 * All listeners run while the screen is visible, so switching tabs is instant and everything updates by itself.
 */
public class CommunityActivity extends AppCompatActivity {
    private ActivityCommunityBinding b;
    private final PlayerStatusAdapter players = new PlayerStatusAdapter();
    private final ActivityAdapter feed = new ActivityAdapter();
    private List<PlayerStatus> latest = new ArrayList<>();
    private ListenerRegistration coinsListener;
    private boolean feedTab = false;

    // Re-draws every 30 s so "Last seen 5 min ago" keeps counting even when nothing changes in the database.
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            players.setPlayers(latest);
            feed.notifyDataSetChanged();
            Bg.UI.postDelayed(this, 30_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityCommunityBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        b.btnBack.setOnClickListener(v -> finish());
        b.listPlayers.setLayoutManager(new LinearLayoutManager(this));
        b.listPlayers.setAdapter(players);
        b.listFeed.setLayoutManager(new LinearLayoutManager(this));
        b.listFeed.setAdapter(feed);
        b.tabFriends.setOnClickListener(v -> selectTab(false));
        b.tabFeed.setOnClickListener(v -> selectTab(true));
        selectTab(false);
    }

    private void selectTab(boolean feedSelected) {
        feedTab = feedSelected;
        CommunityTabs.style(b.tabFriends, b.tabFeed, feedTab);
        b.sectionFriends.setVisibility(feedTab ? View.GONE : View.VISIBLE);
        b.sectionFeed.setVisibility(feedTab ? View.VISIBLE : View.GONE);
        b.subtitle.setText(feedTab ? "Live moments from Mochi players" : "See who is playing right now");
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Realtime Database: who is online (and names for the feed)
        PresenceManager.watchPlayers(list -> {
            latest = list;
            players.setPlayers(list);
            Map<String, String> names = new HashMap<>();
            int online = 0;
            for (PlayerStatus p : list) { names.put(p.uid, p.displayName); if (p.online) online++; }
            feed.setNames(names);
            b.onlineChip.setText(online + " online");
            b.textEmptyPlayers.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });
        // Realtime Database: the newest community events, live
        ActivityFeed.watchRecent(events -> {
            feed.setEvents(events);
            b.textEmptyFeed.setVisibility(events.isEmpty() ? View.VISIBLE : View.GONE);
        });
        // Firestore: coins of every player, keyed by UID, live
        coinsListener = PlayerProfileRepository.watchCoins(players::setCoins);
        MatchManager.setUiListener(players::notifyDataSetChanged);   // "Invite" turns into "Waiting..." by itself
        Bg.UI.postDelayed(tick, 30_000);
    }

    @Override
    protected void onStop() {
        // Listeners OFF when the screen is not visible, so we do not use data for a screen nobody is looking at.
        if (coinsListener != null) { coinsListener.remove(); coinsListener = null; }
        PresenceManager.stopWatchingPlayers();
        ActivityFeed.stopWatching();
        MatchManager.setUiListener(null);
        Bg.UI.removeCallbacks(tick);
        super.onStop();
    }
}

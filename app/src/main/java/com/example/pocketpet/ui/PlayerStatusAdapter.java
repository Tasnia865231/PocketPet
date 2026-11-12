package com.example.pocketpet.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.pocketpet.R;
import com.example.pocketpet.databinding.ItemPlayerBinding;
import com.example.pocketpet.firebase.FirebaseAuthManager;
import com.example.pocketpet.firebase.MatchManager;
import com.example.pocketpet.firebase.PlayerStatus;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Draws the "Players" list: a green dot + "Online" (or grey dot + "Last seen 5 min ago"), then the player's coins. */
public class PlayerStatusAdapter extends RecyclerView.Adapter<PlayerStatusAdapter.VH> {
    static class VH extends RecyclerView.ViewHolder {
        final ItemPlayerBinding b;
        VH(ItemPlayerBinding b) { super(b.getRoot()); this.b = b; }
    }

    private List<PlayerStatus> players = new ArrayList<>();
    private Map<String, Long> coinsByUid = new HashMap<>();   // coins by player UID (from Firestore)

    public void setPlayers(List<PlayerStatus> v) { players = v; notifyDataSetChanged(); }
    public void setCoins(Map<String, Long> v) { coinsByUid = v; notifyDataSetChanged(); }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemPlayerBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PlayerStatus p = players.get(position);
        boolean me = p.uid.equals(FirebaseAuthManager.uid());
        h.b.playerName.setText(p.displayName + (me ? " (you)" : ""));
        h.b.getRoot().setBackgroundResource(p.online ? R.drawable.bg_pcard_mint : R.drawable.bg_store_card);   // online = mint card
        h.b.playerDot.setBackgroundResource(p.online ? R.drawable.bg_dot_online : R.drawable.bg_dot_offline);
        h.b.playerState.setText(p.online ? "Online" : "Last seen " + ago(p.lastChanged));
        h.b.playerState.setTextColor(p.online ? 0xFF3F8F63 : 0xFF8E6F5E);
        // Invite button: only beside OTHER players who are online, and not while I am already in a match
        boolean canInvite = p.online && !me && !MatchManager.inMatch();
        boolean waiting = MatchManager.isPendingTo(p.uid);
        h.b.btnInvite.setVisibility(canInvite ? View.VISIBLE : View.GONE);
        h.b.btnInvite.setText(waiting ? "Waiting..." : "Invite");
        h.b.btnInvite.setEnabled(!waiting && !MatchManager.hasPendingInvite());
        final String uid = p.uid, name = p.displayName;
        h.b.btnInvite.setOnClickListener(v -> MatchManager.sendInvite(v.getContext(), uid, name));
        Long coins = coinsByUid.get(p.uid);       // missing = this player's profile has no coin value yet
        h.b.playerCoins.setText(coins == null
                ? "Total Coins: \u2014"
                : "Total Coins: " + NumberFormat.getIntegerInstance().format(coins));
    }

    @Override public int getItemCount() { return players.size(); }

    /** 90000 ms ago -> "1 min ago". (Uses the phone's clock to compare with Firebase's time stamp.) */
    static String ago(long thenMs) {
        if (thenMs <= 0) return "a while ago";
        long s = Math.max(0, (System.currentTimeMillis() - thenMs) / 1000);
        if (s < 60) return "just now";
        if (s < 3600) return (s / 60) + " min ago";
        if (s < 86400) return (s / 3600) + " h ago";
        return (s / 86400) + " days ago";
    }
}

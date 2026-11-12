package com.example.pocketpet.system;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import com.example.pocketpet.data.GrowthLog;
import com.example.pocketpet.data.Item;
import com.example.pocketpet.data.Pet;
import com.example.pocketpet.data.PetRepository;

/**
 * Mochi's data, shared through a Content Provider and protected by two permissions:
 *   READ_PET  (normal)     query
 *   WRITE_PET (signature)  insert / update / delete - only apps signed with our key
 *
 *   content://com.example.pocketpet.provider/pet          one row: name, hunger, energy, mood, coins, stage, level, cleanliness, isSleeping
 *   content://com.example.pocketpet.provider/items        every item: id, name, type, owned, price, minLevel
 *   content://com.example.pocketpet.provider/items/#      one item (update "owned")
 *   content://com.example.pocketpet.provider/log          growth log: id, time, text (insert a line)
 *
 * Every change calls notifyChange(), so screens that registered a ContentObserver (the main screen, Shop, Bag) and the
 * home-screen widget refresh by themselves. The app itself reads Mochi through here too (PetClient).
 */
public class PetProvider extends ContentProvider {
    public static final String AUTH  = "com.example.pocketpet.provider";
    public static final Uri PET      = Uri.parse("content://" + AUTH + "/pet");
    public static final Uri ITEMS    = Uri.parse("content://" + AUTH + "/items");
    public static final Uri LOG      = Uri.parse("content://" + AUTH + "/log");

    private static final int M_PET = 1, M_ITEMS = 2, M_ITEM = 3, M_LOG = 4;
    private static final android.content.UriMatcher MATCHER = new android.content.UriMatcher(android.content.UriMatcher.NO_MATCH);
    static {
        MATCHER.addURI(AUTH, "pet", M_PET);
        MATCHER.addURI(AUTH, "items", M_ITEMS);
        MATCHER.addURI(AUTH, "items/#", M_ITEM);
        MATCHER.addURI(AUTH, "log", M_LOG);
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        PetRepository repo = new PetRepository(getContext());
        MatrixCursor cur;
        switch (MATCHER.match(uri)) {
            case M_PET: {
                Pet p = repo.load();
                int stage = p.level >= 6 ? 2 : p.level >= 3 ? 1 : 0;
                cur = new MatrixCursor(new String[]{"name", "hunger", "energy", "mood", "coins", "stage", "level", "cleanliness", "isSleeping"});
                cur.addRow(new Object[]{p.name, p.hunger, p.energy, p.mood, p.coins, stage, p.level, p.cleanliness, p.isSleeping ? 1 : 0});
                break;
            }
            case M_ITEMS:
            case M_ITEM: {
                cur = new MatrixCursor(new String[]{"id", "name", "type", "owned", "price", "minLevel"});
                long only = MATCHER.match(uri) == M_ITEM ? ContentUris.parseId(uri) : -1;
                for (Item it : repo.items()) {
                    if (only >= 0 && it.id != only) continue;
                    cur.addRow(new Object[]{it.id, it.name, it.type, it.owned, it.price, it.minLevel});
                }
                break;
            }
            case M_LOG: {
                cur = new MatrixCursor(new String[]{"id", "time", "text"});
                for (GrowthLog l : repo.logs()) cur.addRow(new Object[]{l.id, l.time, l.text});
                break;
            }
            default:
                return null;
        }
        cur.setNotificationUri(getContext().getContentResolver(), uri);
        return cur;
    }

    @Override
    public Uri insert(Uri uri, ContentValues v) {
        if (MATCHER.match(uri) != M_LOG || v == null || v.getAsString("text") == null) return null;
        new PetRepository(getContext()).addLog(v.getAsString("text"));
        getContext().getContentResolver().notifyChange(LOG, null);
        return LOG;
    }

    @Override
    public int update(Uri uri, ContentValues v, String sel, String[] args) {
        if (v == null) return 0;
        PetRepository repo = new PetRepository(getContext());
        switch (MATCHER.match(uri)) {
            case M_PET:
                repo.edit(p -> {
                    if (v.containsKey("hunger"))      p.hunger      = clamp(v.getAsFloat("hunger"));
                    if (v.containsKey("energy"))      p.energy      = clamp(v.getAsFloat("energy"));
                    if (v.containsKey("mood"))        p.mood        = clamp(v.getAsFloat("mood"));
                    if (v.containsKey("cleanliness")) p.cleanliness = clamp(v.getAsFloat("cleanliness"));
                    if (v.containsKey("coins"))       p.coins       = Math.max(0, v.getAsInteger("coins"));
                    return p;
                });
                return 1;                                           // repo.save() already called notifyChange
            case M_ITEM:
                if (!v.containsKey("owned")) return 0;
                repo.setOwned((int) ContentUris.parseId(uri), Math.max(0, v.getAsInteger("owned")));
                return 1;
            default:
                return 0;
        }
    }

    @Override
    public int delete(Uri uri, String sel, String[] args) {
        if (MATCHER.match(uri) != M_LOG) return 0;
        int n = new PetRepository(getContext()).clearLogs();
        getContext().getContentResolver().notifyChange(LOG, null);
        return n;
    }

    @Override
    public String getType(Uri uri) {
        switch (MATCHER.match(uri)) {
            case M_PET:   return "vnd.android.cursor.item/vnd." + AUTH + ".pet";
            case M_ITEMS: return "vnd.android.cursor.dir/vnd." + AUTH + ".item";
            case M_ITEM:  return "vnd.android.cursor.item/vnd." + AUTH + ".item";
            case M_LOG:   return "vnd.android.cursor.dir/vnd." + AUTH + ".log";
            default:      return null;
        }
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(100f, v)); }
}

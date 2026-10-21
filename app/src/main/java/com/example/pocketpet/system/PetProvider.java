package com.example.pocketpet.system;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import com.example.pocketpet.data.Item;
import com.example.pocketpet.data.Pet;
import com.example.pocketpet.data.PetRepository;

/**
 * Read-only Content Provider so other apps/widgets can read the pet.
 *   content://com.example.pocketpet.provider/pet
 *       -> name, hunger, energy, mood, coins, stage, level, cleanliness, isSleeping
 *   content://com.example.pocketpet.provider/items
 *       -> name, type, owned
 */
public class PetProvider extends ContentProvider {
    public static final String AUTH = "com.example.pocketpet.provider";
    public static final Uri PET   = Uri.parse("content://" + AUTH + "/pet");
    public static final Uri ITEMS = Uri.parse("content://" + AUTH + "/items");

    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        MATCHER.addURI(AUTH, "pet",   1);
        MATCHER.addURI(AUTH, "items", 2);
    }

    @Override
    public boolean onCreate() { return true; }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        PetRepository repo = new PetRepository(getContext());
        switch (MATCHER.match(uri)) {
            case 1: {
                Pet p = repo.load();
                // Derive stage from level: 0=lv1-2, 1=lv3-5, 2=lv6+
                int stage = p.level >= 6 ? 2 : p.level >= 3 ? 1 : 0;
                MatrixCursor cur = new MatrixCursor(
                        new String[]{"name","hunger","energy","mood","coins","stage",
                                     "level","cleanliness","isSleeping"});
                cur.addRow(new Object[]{
                        p.name, p.hunger, p.energy, p.mood, p.coins, stage,
                        p.level, p.cleanliness, p.isSleeping ? 1 : 0});
                return cur;
            }
            case 2: {
                MatrixCursor cur = new MatrixCursor(new String[]{"name","type","owned"});
                for (Item it : repo.items())
                    cur.addRow(new Object[]{it.name, it.type, it.owned});
                return cur;
            }
            default:
                return null;
        }
    }

    @Override
    public String getType(Uri uri) {
        switch (MATCHER.match(uri)) {
            case 1: return "vnd.android.cursor.item/vnd." + AUTH + ".pet";
            case 2: return "vnd.android.cursor.dir/vnd."  + AUTH + ".item";
            default: return null;
        }
    }

    @Override public Uri    insert(Uri uri, ContentValues v)                              { return null; }
    @Override public int    delete(Uri uri, String sel, String[] args)                    { return 0; }
    @Override public int    update(Uri uri, ContentValues v, String sel, String[] args)   { return 0; }
}

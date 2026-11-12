package com.example.pocketpet.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Pet.class, Item.class, GrowthLog.class}, version = 4, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract PetDao petDao();
    public abstract ItemDao itemDao();
    public abstract LogDao logDao();

    /** v2 -> v3: eggshell skins. Adds one column and keeps every pet, coin and item the player has. */
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE pet ADD COLUMN shell TEXT");
        }
    };

    /** v3 -> v4: lifetime earned coins. Adds one column; every pet, coin and item is kept. Starts at the current balance. */
    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE pet ADD COLUMN totalEarned INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE pet SET totalEarned = coins");
        }
    };

    private static volatile AppDatabase inst;

    public static AppDatabase get(Context ctx) {
        if (inst == null) {
            synchronized (AppDatabase.class) {
                if (inst == null) {
                    inst = Room.databaseBuilder(ctx.getApplicationContext(), AppDatabase.class, "pocketpet.db")
                            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return inst;
    }
}

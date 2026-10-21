package com.example.pocketpet.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {Pet.class, Item.class, GrowthLog.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract PetDao petDao();
    public abstract ItemDao itemDao();
    public abstract LogDao logDao();

    private static volatile AppDatabase inst;

    public static AppDatabase get(Context ctx) {
        if (inst == null) {
            synchronized (AppDatabase.class) {
                if (inst == null) {
                    inst = Room.databaseBuilder(ctx.getApplicationContext(), AppDatabase.class, "pocketpet.db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return inst;
    }
}

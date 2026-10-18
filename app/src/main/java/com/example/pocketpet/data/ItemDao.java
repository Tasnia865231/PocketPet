package com.example.pocketpet.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface ItemDao {
    @Query("SELECT * FROM item")
    List<Item> all();

    @Query("SELECT * FROM item WHERE owned > 0")
    List<Item> owned();

    @Query("SELECT COUNT(*) FROM item")
    int count();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Item> items);

    @Update
    void update(Item i);
}

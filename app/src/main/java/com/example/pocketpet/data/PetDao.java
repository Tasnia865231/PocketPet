package com.example.pocketpet.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface PetDao {
    @Query("SELECT * FROM pet WHERE id = 1")
    Pet get();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(Pet p);
}

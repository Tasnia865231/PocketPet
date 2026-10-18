package com.example.pocketpet.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface LogDao {
    @Query("SELECT * FROM growth_log ORDER BY time DESC")
    List<GrowthLog> all();

    @Insert
    void add(GrowthLog l);
}

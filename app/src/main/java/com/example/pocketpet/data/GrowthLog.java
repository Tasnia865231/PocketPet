package com.example.pocketpet.data;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "growth_log")
public class GrowthLog {
    @PrimaryKey(autoGenerate = true) public long id;
    public long time = System.currentTimeMillis();
    public String text;

    public GrowthLog() {}

    @Ignore
    public GrowthLog(String text) { this.text = text; }
}

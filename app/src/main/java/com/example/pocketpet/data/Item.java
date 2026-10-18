package com.example.pocketpet.data;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "item")
public class Item {
    @PrimaryKey public int id;
    public String name;
    public String type;   // "food" or "hat"
    public int price;
    public int effect;    // hunger restored for food
    public int owned;

    public Item() {}

    @Ignore
    public Item(int id, String name, String type, int price, int effect) {
        this.id = id; this.name = name; this.type = type; this.price = price; this.effect = effect;
    }
}

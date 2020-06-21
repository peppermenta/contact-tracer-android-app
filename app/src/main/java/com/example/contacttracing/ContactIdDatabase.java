package com.example.contacttracing;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * Database for storing IDs of Users who have been in contact
 **/
@Database(entities = {ContactID.class}, version = 1)
public abstract class ContactIdDatabase extends RoomDatabase{
    private static final String DB_NAME = "contactid_db";
    private static ContactIdDatabase instance;
    public abstract ContactIDDao contactIDDao();

    public static synchronized ContactIdDatabase getInstance(Context context) {
        if(instance==null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),ContactIdDatabase.class,DB_NAME).build();
        }

        return instance;
    }
}

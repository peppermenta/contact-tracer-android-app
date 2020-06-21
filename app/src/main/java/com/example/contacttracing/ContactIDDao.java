package com.example.contacttracing;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
@Dao
abstract class ContactIDDao {
    //Milliseconds elapsed in 14 days
    private static final int threshold = 14*24*60*60*1000;

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void insertContactID(ContactID contactID);

    @Query("SELECT * FROM contactids")
    abstract ContactID[] getAllContactIDs();

    @Query("SELECT * FROM contactids WHERE (:currTime-createdAt)<(14*24*60*60*1000)")
    abstract ContactID[] getRecentContactIDs(long currTime);

}

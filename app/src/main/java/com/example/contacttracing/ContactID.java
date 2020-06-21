package com.example.contacttracing;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**Database Model for ContactIDs(IDs of Users who have been in contact)
 **/
@Entity(tableName = "contactIDs")
public class ContactID {
    @PrimaryKey
    @ColumnInfo(name = "uuid")
    @NonNull
    public String uuid;

    @ColumnInfo(name = "createdAt")
    public long createdAt;

    public ContactID(String uuid) {
        this.uuid = uuid;
        this.createdAt = System.currentTimeMillis();
    }
}

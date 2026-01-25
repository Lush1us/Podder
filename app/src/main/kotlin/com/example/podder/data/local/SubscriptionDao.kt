package com.example.podder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SubscriptionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(subs: List<SubscriptionEntity>): List<Long>

    @Query("SELECT url FROM subscriptions")
    suspend fun getAllUrls(): List<String>

    @Query("SELECT COUNT(*) FROM subscriptions")
    suspend fun getCount(): Int
}

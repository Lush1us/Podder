package com.example.podder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(subs: List<SubscriptionEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sub: SubscriptionEntity): Long

    @Query("SELECT url FROM subscriptions")
    suspend fun getAllUrls(): List<String>

    @Query("SELECT url FROM subscriptions")
    fun getSubscribedUrls(): Flow<List<String>>

    @Query("DELETE FROM subscriptions WHERE url = :url")
    suspend fun delete(url: String): Int

    @Query("SELECT COUNT(*) FROM subscriptions")
    suspend fun getCount(): Int
}

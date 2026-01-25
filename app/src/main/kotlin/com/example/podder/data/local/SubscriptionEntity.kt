package com.example.podder.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val url: String,
    val title: String?,
    val dateAdded: Long
)

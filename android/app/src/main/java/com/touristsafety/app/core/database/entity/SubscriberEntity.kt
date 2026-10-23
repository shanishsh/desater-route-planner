package com.touristsafety.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscribers")
data class SubscriberEntity(
    @PrimaryKey val phone: String,
    val status: String,
    val consentGiven: Boolean,
    val consentTimestampEpochMs: Long,
    val subscribedAreas: String,
    val subscribedItineraryId: String?
)


package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cf_sync_rules")
data class CloudflareSyncRule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ruleName: String,
    val coloFilter: String,
    val targetDomain: String,
    val syncCount: Int = 1,
    val zoneId: String,
    val email: String,
    val apiKey: String,
    val isAutoSync: Boolean = true,
    val lastSyncTime: Long = 0L,
    val lastSyncStatus: String = ""
)

package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scanned_ips")
data class ScannedIp(
    @PrimaryKey val ip: String,
    val colo: String,
    val latency: Long,
    val isFavorite: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

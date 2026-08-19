package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScannedIpDao {
    @Query("SELECT * FROM scanned_ips ORDER BY timestamp DESC, latency ASC")
    fun getAllIps(): Flow<List<ScannedIp>>

    @Query("SELECT * FROM scanned_ips WHERE isFavorite = 1 ORDER BY timestamp DESC, latency ASC")
    fun getFavoriteIps(): Flow<List<ScannedIp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIp(ip: ScannedIp)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIps(ips: List<ScannedIp>)

    @Query("UPDATE scanned_ips SET isFavorite = :isFavorite WHERE ip = :ip")
    suspend fun updateFavorite(ip: String, isFavorite: Boolean)

    @Query("DELETE FROM scanned_ips WHERE isFavorite = 0")
    suspend fun deleteNonFavorites()
    
    @Query("DELETE FROM scanned_ips")
    suspend fun clearAll()
    
    @Query("DELETE FROM scanned_ips WHERE ip = :ip")
    suspend fun deleteIp(ip: String)
    
    @Query("DELETE FROM scanned_ips WHERE ip NOT IN (SELECT ip FROM scanned_ips ORDER BY timestamp DESC LIMIT 100)")
    suspend fun trimTo100Latest()

    @Query("SELECT * FROM scanned_ips ORDER BY timestamp DESC, latency ASC LIMIT :limit")
    suspend fun getLatestIps(limit: Int): List<ScannedIp>

    @Query("SELECT * FROM scanned_ips WHERE colo IN (:colos) ORDER BY timestamp DESC, latency ASC LIMIT :limit")
    suspend fun getIpsByColos(colos: List<String>, limit: Int): List<ScannedIp>
}

package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CloudflareSyncRuleDao {
    @Query("SELECT * FROM cf_sync_rules")
    fun getAllRules(): Flow<List<CloudflareSyncRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: CloudflareSyncRule)

    @Update
    suspend fun updateRule(rule: CloudflareSyncRule)

    @Delete
    suspend fun deleteRule(rule: CloudflareSyncRule)

    @Query("SELECT * FROM cf_sync_rules WHERE isAutoSync = 1")
    suspend fun getAutoSyncRules(): List<CloudflareSyncRule>
}

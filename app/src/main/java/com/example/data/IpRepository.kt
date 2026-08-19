package com.example.data

import kotlinx.coroutines.flow.Flow

class IpRepository(
    private val scannedIpDao: ScannedIpDao,
    private val syncRuleDao: CloudflareSyncRuleDao
) {
    val allIps: Flow<List<ScannedIp>> = scannedIpDao.getAllIps()
    val favoriteIps: Flow<List<ScannedIp>> = scannedIpDao.getFavoriteIps()
    val allRules: Flow<List<CloudflareSyncRule>> = syncRuleDao.getAllRules()

    suspend fun insertIp(ip: ScannedIp) = scannedIpDao.insertIp(ip)
    
    suspend fun insertIps(ips: List<ScannedIp>) = scannedIpDao.insertIps(ips)

    suspend fun updateFavorite(ip: String, isFavorite: Boolean) = scannedIpDao.updateFavorite(ip, isFavorite)

    suspend fun deleteNonFavorites() = scannedIpDao.deleteNonFavorites()
    
    suspend fun clearAll() = scannedIpDao.clearAll()
    
    suspend fun deleteIp(ip: String) = scannedIpDao.deleteIp(ip)
    
    suspend fun trimTo100Latest() = scannedIpDao.trimTo100Latest()

    suspend fun insertRule(rule: CloudflareSyncRule) = syncRuleDao.insertRule(rule)
    suspend fun updateRule(rule: CloudflareSyncRule) = syncRuleDao.updateRule(rule)
    suspend fun deleteRule(rule: CloudflareSyncRule) = syncRuleDao.deleteRule(rule)
    suspend fun getAutoSyncRules() = syncRuleDao.getAutoSyncRules()

    suspend fun getIpsByColo(coloList: List<String>, limit: Int): List<ScannedIp> {
        return if (coloList.isEmpty() || coloList.contains("ALL")) {
            scannedIpDao.getLatestIps(limit)
        } else {
            scannedIpDao.getIpsByColos(coloList, limit)
        }
    }
}

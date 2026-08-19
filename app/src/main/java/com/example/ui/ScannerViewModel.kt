package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.CloudflareSyncRule
import com.example.data.IpRepository
import com.example.data.ScannedIp
import com.example.scanner.ScannerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "scanner_settings")

class ScannerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: IpRepository
    
    // DataStore keys
    private val THREADS_KEY = floatPreferencesKey("threads")
    private val LATENCY_KEY = floatPreferencesKey("max_latency")
    private val TARGET_IP_KEY = floatPreferencesKey("target_ip_count")
    private val TARGET_VALID_KEY = floatPreferencesKey("target_valid_count")
    private val TARGET_ALL_VALID_KEY = floatPreferencesKey("target_all_valid_count")
    private val API_URL_KEY = stringPreferencesKey("api_url")
    private val DATACENTER_FILTER_KEY = stringPreferencesKey("datacenter_filter")
    
    init {
        val database = AppDatabase.getDatabase(application)
        repository = IpRepository(database.scannedIpDao(), database.syncRuleDao())
        
        // Load settings from DataStore
        viewModelScope.launch {
            val prefs = application.dataStore.data.first()
            _uiState.update { state ->
                val savedFilter = prefs[DATACENTER_FILTER_KEY] ?: "ALL"
                state.copy(
                    concurrentThreads = prefs[THREADS_KEY] ?: 100f,
                    maxLatency = prefs[LATENCY_KEY] ?: 350f,
                    targetIpCount = prefs[TARGET_IP_KEY] ?: 2000f,
                    targetValidIpCount = prefs[TARGET_VALID_KEY] ?: 10f,
                    targetAllValidIpCount = prefs[TARGET_ALL_VALID_KEY] ?: 100f,
                    workerApiUrl = prefs[API_URL_KEY] ?: "proxyipsinp.xxxxxxx.nyc.mn",
                    dataCenterFilter = savedFilter,
                    activeFilters = if (savedFilter.isBlank()) setOf("ALL") else savedFilter.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                )
            }
        }
        
        // Load initial IPs from database into the UI state
        viewModelScope.launch {
            repository.allIps.collect { ips ->
                if (!_uiState.value.isScanning) {
                    _uiState.update { it.copy(validIps = ips) }
                }
            }
        }
    }

    val allSavedIps = repository.allIps
    val favoriteIps = repository.favoriteIps
    val allRules = repository.allRules

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private val listMutex = Mutex()

    fun updateApiUrl(url: String) {
        _uiState.update { it.copy(workerApiUrl = url) }
        viewModelScope.launch { getApplication<Application>().dataStore.edit { it[API_URL_KEY] = url } }
    }
    
    fun updateConcurrentThreads(threads: Float) {
        _uiState.update { it.copy(concurrentThreads = threads) }
        viewModelScope.launch { getApplication<Application>().dataStore.edit { it[THREADS_KEY] = threads } }
    }
    
    fun updateMaxLatency(latency: Float) {
        _uiState.update { it.copy(maxLatency = latency) }
        viewModelScope.launch { getApplication<Application>().dataStore.edit { it[LATENCY_KEY] = latency } }
    }
    
    fun updateTargetIpCount(count: Float) {
        _uiState.update { it.copy(targetIpCount = count) }
        viewModelScope.launch { getApplication<Application>().dataStore.edit { it[TARGET_IP_KEY] = count } }
    }
    
    fun updateTargetValidIpCount(count: Float) {
        _uiState.update { it.copy(targetValidIpCount = count) }
        viewModelScope.launch { getApplication<Application>().dataStore.edit { it[TARGET_VALID_KEY] = count } }
    }
    
    fun updateTargetAllValidIpCount(count: Float) {
        _uiState.update { it.copy(targetAllValidIpCount = count) }
        viewModelScope.launch { getApplication<Application>().dataStore.edit { it[TARGET_ALL_VALID_KEY] = count } }
    }
    
    fun updateDataCenterFilter(filter: String) {
        val uppercaseFilter = filter.uppercase()
        val parsedFilters = uppercaseFilter.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val finalFilters = if (parsedFilters.isEmpty()) setOf("ALL") else parsedFilters
        _uiState.update { it.copy(dataCenterFilter = uppercaseFilter, activeFilters = finalFilters) }
        viewModelScope.launch { getApplication<Application>().dataStore.edit { it[DATACENTER_FILTER_KEY] = uppercaseFilter } }
    }

    fun toggleScanMode() {
        _uiState.update { it.copy(useCloudApi = !it.useCloudApi) }
    }

    fun startScan() {
        if (_uiState.value.isScanning) return
        
        // Grab local IPs before clearing the state
        val localIps = _uiState.value.validIps.map { it.ip }.distinct()
        
        _uiState.update { it.copy(isScanning = true, scannedCount = 0, validIps = emptyList()) }
        
        scanJob = viewModelScope.launch(Dispatchers.Default) {
            val ipsToGeneratePerRound = _uiState.value.targetIpCount.toInt()
            val useApi = _uiState.value.useCloudApi
            var apiUrl = _uiState.value.workerApiUrl.trim()
            if (apiUrl.isNotBlank() && !apiUrl.startsWith("http://") && !apiUrl.startsWith("https://")) {
                apiUrl = "https://$apiUrl"
            }
            val maxLatency = _uiState.value.maxLatency.toLong()
            val dataCenters = _uiState.value.dataCenterFilter.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() && it != "ALL" }
            val chunkSize = _uiState.value.concurrentThreads.toInt()
            val activeChips = _uiState.value.activeFilters
            val targetValidCount = _uiState.value.targetValidIpCount.toInt()
            val targetAllCount = _uiState.value.targetAllValidIpCount.toInt()
            val hasAll = activeChips.contains("ALL")
            val specificChips = activeChips.filter { it != "ALL" }
            val hasSpecific = specificChips.isNotEmpty()

            var isFirstRound = true
            
            while (isActive) {
                val totalValid = _uiState.value.validIps.size
                val currentValidMatches = _uiState.value.validIps.count { specificChips.contains(it.colo.uppercase()) }
                
                var shouldStop = true
                if (hasAll && totalValid < targetAllCount) {
                    shouldStop = false
                }
                if (hasSpecific && currentValidMatches < targetValidCount) {
                    shouldStop = false
                }
                if (!hasAll && !hasSpecific) {
                    if (totalValid < targetAllCount) shouldStop = false
                }
                
                if (shouldStop) break
                
                val ipsToTest = if (isFirstRound && localIps.isNotEmpty()) {
                    isFirstRound = false
                    localIps
                } else {
                    isFirstRound = false
                    val seeds = (localIps + _uiState.value.validIps.map { it.ip }).distinct()
                    if (seeds.isNotEmpty()) {
                        ScannerEngine.generateIpsAroundSeeds(seeds, ipsToGeneratePerRound)
                    } else {
                        ScannerEngine.generateRandomIps(ipsToGeneratePerRound)
                    }
                }
                
                for (chunk in ipsToTest.chunked(chunkSize)) {
                    if (!isActive) break
                    
                    val totalValidInner = _uiState.value.validIps.size
                    val innerValidMatches = _uiState.value.validIps.count { specificChips.contains(it.colo.uppercase()) }
                    
                    var innerShouldStop = true
                    if (hasAll && totalValidInner < targetAllCount) {
                        innerShouldStop = false
                    }
                    if (hasSpecific && innerValidMatches < targetValidCount) {
                        innerShouldStop = false
                    }
                    if (!hasAll && !hasSpecific) {
                        if (totalValidInner < targetAllCount) innerShouldStop = false
                    }
                    
                    if (innerShouldStop) break
                    
                    val jobs = chunk.map { ip ->
                        launch {
                            val result = if (useApi && apiUrl.isNotBlank()) {
                                ScannerEngine.testIpViaApi(apiUrl, ip)
                            } else {
                                ScannerEngine.testIp(ip)
                            }
                            
                            listMutex.withLock {
                                _uiState.update { state ->
                                    state.copy(scannedCount = state.scannedCount + 1)
                                }
                                if (result != null && result.latency <= maxLatency) {
                                    val coloMatches = dataCenters.isEmpty() || dataCenters.contains(result.colo.uppercase())
                                    if (coloMatches) {
                                        val scannedIp = ScannedIp(
                                            ip = result.ip,
                                            colo = result.colo,
                                            latency = result.latency
                                        )
                                        repository.insertIp(scannedIp)
                                        
                                        _uiState.update { state ->
                                            val currentValid = state.validIps.toMutableList()
                                            val existingIndex = currentValid.indexOfFirst { it.ip == scannedIp.ip }
                                            if (existingIndex == -1) {
                                                val index = currentValid.indexOfFirst { it.latency > scannedIp.latency }
                                                if (index == -1) currentValid.add(scannedIp) else currentValid.add(index, scannedIp)
                                            } else if (scannedIp.latency < currentValid[existingIndex].latency) {
                                                currentValid.removeAt(existingIndex)
                                                val index = currentValid.indexOfFirst { it.latency > scannedIp.latency }
                                                if (index == -1) currentValid.add(scannedIp) else currentValid.add(index, scannedIp)
                                            }
                                            state.copy(validIps = currentValid)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    jobs.forEach { it.join() }
                }
            }
            
            _uiState.update { it.copy(isScanning = false) }
            performAutoSync()
        }
    }

    private fun performAutoSync() {
        viewModelScope.launch {
            repository.trimTo100Latest()
            val autoRules = repository.getAutoSyncRules()
            for (rule in autoRules) {
                syncRule(rule)
            }
        }
    }

    fun syncRule(rule: com.example.data.CloudflareSyncRule) {
        viewModelScope.launch {
            try {
                val colos = rule.coloFilter.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
                val ipsToSync = repository.getIpsByColo(colos, rule.syncCount)
                val success = com.example.scanner.CloudflareSyncEngine.syncRule(rule, ipsToSync)
                
                val updatedRule = rule.copy(
                    lastSyncTime = System.currentTimeMillis(),
                    lastSyncStatus = if (success) "同步成功" else "同步失败"
                )
                repository.updateRule(updatedRule)
            } catch (e: Exception) {
                val updatedRule = rule.copy(
                    lastSyncTime = System.currentTimeMillis(),
                    lastSyncStatus = "同步出错: ${e.message}"
                )
                repository.updateRule(updatedRule)
            }
        }
    }

    fun saveRule(rule: com.example.data.CloudflareSyncRule) {
        viewModelScope.launch {
            if (rule.id == 0) {
                repository.insertRule(rule)
            } else {
                repository.updateRule(rule)
            }
        }
    }

    fun deleteRule(rule: com.example.data.CloudflareSyncRule) {
        viewModelScope.launch {
            repository.deleteRule(rule)
        }
    }

    fun toggleRuleAutoSync(rule: com.example.data.CloudflareSyncRule) {
        viewModelScope.launch {
            repository.updateRule(rule.copy(isAutoSync = !rule.isAutoSync))
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        _uiState.update { it.copy(isScanning = false) }
        performAutoSync()
    }

    fun toggleFavorite(ip: ScannedIp) {
        viewModelScope.launch {
            repository.updateFavorite(ip.ip, !ip.isFavorite)
            _uiState.update { state ->
                val currentValid = state.validIps.map {
                    if (it.ip == ip.ip) it.copy(isFavorite = !it.isFavorite) else it
                }
                state.copy(validIps = currentValid)
            }
        }
    }

    fun setFilter(colo: String) {
        val current = _uiState.value.activeFilters.toMutableSet()
        if (current.contains(colo)) {
            current.remove(colo)
        } else {
            current.add(colo)
        }
        if (current.isEmpty()) current.add("ALL")
        
        val newFilterStr = current.joinToString(",")
        updateDataCenterFilter(newFilterStr)
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
            _uiState.update { it.copy(validIps = emptyList()) }
        }
    }

    private val _queryState = MutableStateFlow(QueryUiState())
    val queryState: StateFlow<QueryUiState> = _queryState.asStateFlow()

    fun queryDomainOrIp(input: String) {
        if (input.isBlank()) return
        _queryState.update { it.copy(isLoading = true, results = emptyList(), error = null) }

        viewModelScope.launch {
            try {
                // If it looks like an IP or has a port, test it directly
                val portRemote = if (input.contains(":")) input.substringAfterLast(":").toIntOrNull() ?: 443 else 443
                val cleanInput = input.substringBeforeLast(":")
                
                val useApi = _uiState.value.useCloudApi
                var apiUrl = _uiState.value.workerApiUrl.trim()
                if (apiUrl.isNotBlank() && !apiUrl.startsWith("http://") && !apiUrl.startsWith("https://")) {
                    apiUrl = "https://$apiUrl"
                }

                if (cleanInput.matches(Regex("^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")) || 
                    cleanInput.contains("[")) {
                    val result = if (useApi && apiUrl.isNotBlank()) ScannerEngine.testIpViaApi(apiUrl, cleanInput, portRemote) else ScannerEngine.testIp(cleanInput, portRemote)
                    val info = ScannerEngine.getIpInfo(cleanInput)
                    _queryState.update { 
                        it.copy(
                            isLoading = false,
                            results = listOf(QueryResult(cleanInput, result?.colo, result?.latency, info?.country, info?.asn))
                        )
                    }
                } else {
                    // Resolve domain first
                    val ips = ScannerEngine.resolveDomain(cleanInput)
                    if (ips.isEmpty()) {
                        _queryState.update { it.copy(isLoading = false, error = "未能解析出 IP 地址") }
                        return@launch
                    }
                    
                    val results = mutableListOf<QueryResult>()
                    ips.forEach { ip ->
                        val result = if (useApi && apiUrl.isNotBlank()) ScannerEngine.testIpViaApi(apiUrl, ip, portRemote) else ScannerEngine.testIp(ip, portRemote)
                        if (result != null) {
                            val info = ScannerEngine.getIpInfo(ip)
                            results.add(QueryResult(ip, result.colo, result.latency, info?.country, info?.asn))
                            // Update UI incrementally
                            _queryState.update { it.copy(results = results.toList()) }
                        }
                    }
                    _queryState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                _queryState.update { it.copy(isLoading = false, error = e.message ?: "查询失败") }
            }
        }
    }
}

data class QueryUiState(
    val isLoading: Boolean = false,
    val results: List<QueryResult> = emptyList(),
    val error: String? = null
)

data class QueryResult(
    val ip: String,
    val colo: String?,
    val latency: Long?,
    val country: String?,
    val asn: String?
)

data class ScannerUiState(
    val isScanning: Boolean = false,
    val scannedCount: Int = 0,
    val validIps: List<ScannedIp> = emptyList(),
    val activeFilters: Set<String> = setOf("ALL"),
    val useCloudApi: Boolean = true,
    val workerApiUrl: String = "proxyipsinp.xxxxxxx.nyc.mn",
    val concurrentThreads: Float = 100f,
    val maxLatency: Float = 350f,
    val targetIpCount: Float = 2000f,
    val targetValidIpCount: Float = 10f,
    val targetAllValidIpCount: Float = 100f,
    val dataCenterFilter: String = "ALL"
) {
    val displayedIps: List<ScannedIp>
        get() = if (activeFilters.contains("ALL")) validIps else validIps.filter { activeFilters.contains(it.colo) }
}

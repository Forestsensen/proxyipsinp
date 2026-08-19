package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.ScannedIp
import com.example.ui.theme.*

@Composable
fun QueryScreen(viewModel: ScannerViewModel) {
    val queryState by viewModel.queryState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "手动查询与解析",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            placeholder = { Text("输入域名或IP (如 1.1.1.1 或 example.com:443)", color = TextSecondary) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardBackground,
                unfocusedContainerColor = CardBackground,
                focusedBorderColor = PrimaryOrange,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { viewModel.queryDomainOrIp(inputText) }) {
                    if (queryState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = PrimaryOrange, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = "查询", tint = PrimaryOrange)
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        
        if (queryState.error != null) {
            Text(queryState.error!!, color = Color.Red, modifier = Modifier.padding(top = 8.dp))
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(queryState.results, key = { it.ip }) { result ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(result.ip, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            if (result.latency != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF332B1A))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("${result.latency} ms", color = LatencyYellow, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF3A1C1C))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("超时/无效", color = Color.Red, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (result.colo != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(PrimaryOrange.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(result.colo, fontSize = 12.sp, color = PrimaryOrange, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(result.country ?: "未知位置", fontSize = 14.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ASN: ${result.asn ?: "未知"}", fontSize = 14.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: ScannerViewModel = viewModel()) {
    val navController = rememberNavController()
    
    Scaffold(
        bottomBar = { AppBottomNavigation(navController) },
        containerColor = DarkBackground
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "scanner",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("scanner") {
                ScannerScreen(viewModel)
            }
            composable("storage") {
                StorageScreen(viewModel)
            }
            composable("sync") {
                SyncScreen(viewModel)
            }
        }
    }
}

@Composable
fun ScannerScreen(viewModel: ScannerViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Cf IP 扫描与DNS",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            // Top Engine Card
            Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(PrimaryOrange.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.WifiTethering, contentDescription = null, tint = PrimaryOrange)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cloudflare IP 引擎", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        val targetPerRound = uiState.targetIpCount.toInt().coerceAtLeast(1)
                        val roundNumber = (uiState.scannedCount / targetPerRound) + 1
                        val currentRoundScanned = uiState.scannedCount % targetPerRound
                        val batchInfo = if (uiState.isScanning || uiState.scannedCount > 0) "第 ${roundNumber} 批: 已扫描 ${currentRoundScanned} / ${targetPerRound} (${uiState.validIps.size} 个有效)" else "就绪"
                        Text(batchInfo, fontSize = 14.sp, color = TextSecondary)
                    }
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { if (uiState.isScanning) viewModel.stopScan() else viewModel.startScan() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (uiState.isScanning) Color.Red else PrimaryOrange),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(if (uiState.isScanning) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (uiState.isScanning) "停止扫描" else "开始高速扫描", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        }
        
        item {
            AnimatedVisibility(
                visible = uiState.isScanning || uiState.scannedCount > 0,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    // 测速进度条 Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val targetPerRound = uiState.targetIpCount.toInt().coerceAtLeast(1)
                            val currentRoundScanned = uiState.scannedCount % targetPerRound
                            val progress = (currentRoundScanned.toFloat() / targetPerRound).coerceIn(0f, 1f)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("进度: ${(progress * 100).toInt()}%", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text("${currentRoundScanned} / ${targetPerRound} IPs", color = PrimaryOrange, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = PrimaryOrange,
                                trackColor = DarkBackground
                            )
                        }
                    }
                }
            }
        }

        item {
            AnimatedVisibility(
                visible = showSettings,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("并发线程", color = TextPrimary, modifier = Modifier.weight(1f))
                        Text("${uiState.concurrentThreads.toInt()}", color = PrimaryOrange, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = uiState.concurrentThreads,
                        onValueChange = { viewModel.updateConcurrentThreads(it) },
                        valueRange = 1f..200f,
                        colors = SliderDefaults.colors(thumbColor = PrimaryOrange, activeTrackColor = PrimaryOrange)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("最大延迟限制", color = TextPrimary, modifier = Modifier.weight(1f))
                        Text("${uiState.maxLatency.toInt()} ms", color = PrimaryOrange, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = uiState.maxLatency,
                        onValueChange = { viewModel.updateMaxLatency(it) },
                        valueRange = 50f..1000f,
                        colors = SliderDefaults.colors(thumbColor = PrimaryOrange, activeTrackColor = PrimaryOrange)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("专属目标 (非 ALL 节点有效 IP)", color = TextPrimary, modifier = Modifier.weight(1f))
                        Text("${uiState.targetValidIpCount.toInt()} 个", color = PrimaryOrange, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = uiState.targetValidIpCount,
                        onValueChange = { viewModel.updateTargetValidIpCount(it) },
                        valueRange = 1f..100f,
                        colors = SliderDefaults.colors(thumbColor = PrimaryOrange, activeTrackColor = PrimaryOrange)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("全局目标 (ALL 专用)", color = TextPrimary, modifier = Modifier.weight(1f))
                        Text("${uiState.targetAllValidIpCount.toInt()} 个", color = PrimaryOrange, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = uiState.targetAllValidIpCount,
                        onValueChange = { viewModel.updateTargetAllValidIpCount(it) },
                        valueRange = 100f..1000f,
                        colors = SliderDefaults.colors(thumbColor = PrimaryOrange, activeTrackColor = PrimaryOrange)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("单轮随机测速数量", color = TextPrimary, modifier = Modifier.weight(1f))
                        Text("${uiState.targetIpCount.toInt()} IPs", color = PrimaryOrange, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = uiState.targetIpCount,
                        onValueChange = { viewModel.updateTargetIpCount(it) },
                        valueRange = 100f..3000f,
                        colors = SliderDefaults.colors(thumbColor = PrimaryOrange, activeTrackColor = PrimaryOrange)
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedTextField(
                        value = uiState.dataCenterFilter,
                        onValueChange = { viewModel.updateDataCenterFilter(it) },
                        label = { Text("数据中心过滤器 (如 HKG,SJC,LAX)", color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedTextField(
                        value = uiState.workerApiUrl,
                        onValueChange = { viewModel.updateApiUrl(it) },
                        label = { Text("Worker API 链接 (无须 https://)", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PrimaryOrange
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        }
        

        item {
            Spacer(modifier = Modifier.height(24.dp))

            // Filters
            val allUniqueColos = (uiState.activeFilters + uiState.validIps.map { it.colo }).filter { it != "ALL" }.distinct()
            val selectedColos = allUniqueColos.filter { uiState.activeFilters.contains(it) }.sorted()
            val unselectedColos = allUniqueColos.filter { !uiState.activeFilters.contains(it) }.sorted()
            
            val finalColos = mutableListOf("ALL")
            finalColos.addAll(selectedColos)
            if (!uiState.isScanning) {
                finalColos.addAll(unselectedColos)
            }
            
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(finalColos) { colo ->
                    val isSelected = uiState.activeFilters.contains(colo)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) PrimaryOrange else CardBackground)
                            .clickable { viewModel.setFilter(colo) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(colo, color = if (isSelected) Color.White else TextSecondary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "扫描到的有效 IP (${uiState.displayedIps.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        items(uiState.displayedIps, key = { it.ip }) { ip ->
            IpCard(ip = ip, onToggleFavorite = { viewModel.toggleFavorite(it) })
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun StorageScreen(viewModel: ScannerViewModel) {
    val favoriteIps by viewModel.favoriteIps.collectAsState(initial = emptyList())
    val allSavedIps by viewModel.allSavedIps.collectAsState(initial = emptyList())
    
    var showFavoritesOnly by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val displayIps = if (showFavoritesOnly) favoriteIps else allSavedIps
    
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Storage, contentDescription = null, tint = PrimaryOrange, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "已保存的 IP 仓库",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${allSavedIps.size} 个 IP 已存入本地数据库",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 44.dp)
                )
            }
            IconButton(onClick = {
                viewModel.clearAll()
            }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete All", tint = Color.Red.copy(alpha = 0.8f))
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { 
                    val ips = displayIps.joinToString("\n") { it.ip }
                    copyToClipboard(context, ips)
                },
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("复制 IP", fontWeight = FontWeight.SemiBold)
            }
            
            OutlinedButton(
                onClick = { showFavoritesOnly = !showFavoritesOnly },
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryOrange),
                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryOrange),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(if (showFavoritesOnly) Icons.Filled.Favorite else Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (showFavoritesOnly) "显示全部" else "仅看收藏", fontWeight = FontWeight.SemiBold)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        if (displayIps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (showFavoritesOnly) "暂无收藏的IP" else "暂无保存的IP", color = TextSecondary)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayIps, key = { it.ip }) { ip ->
                    IpCard(ip = ip, onToggleFavorite = { viewModel.toggleFavorite(it) })
                }
            }
        }
    }
}

@Composable
fun IpCard(ip: ScannedIp, onToggleFavorite: (ScannedIp) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(ip.ip, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ip.timestamp)),
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(PrimaryOrange.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(ip.colo, fontSize = 12.sp, color = PrimaryOrange, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(getColoLocation(ip.colo), fontSize = 14.sp, color = TextSecondary)
                }
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF332B1A))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("${ip.latency} ms", color = LatencyYellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.Rounded.Router, contentDescription = null, tint = IconBlue, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = { onToggleFavorite(ip) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (ip.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (ip.isFavorite) PrimaryOrange else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AppBottomNavigation(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = BottomNavBackground,
        contentColor = TextPrimary,
        tonalElevation = 0.dp
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Rounded.WifiTethering, contentDescription = "扫描网络") },
            label = { Text("扫描网络") },
            selected = currentRoute == "scanner",
            onClick = {
                if (currentRoute != "scanner") {
                    navController.navigate("scanner") {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PrimaryOrange,
                selectedTextColor = PrimaryOrange,
                indicatorColor = PrimaryOrange.copy(alpha = 0.2f),
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Rounded.Storage, contentDescription = "本地储存") },
            label = { Text("本地储存") },
            selected = currentRoute == "storage",
            onClick = {
                if (currentRoute != "storage") {
                    navController.navigate("storage") {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PrimaryOrange,
                selectedTextColor = PrimaryOrange,
                indicatorColor = PrimaryOrange.copy(alpha = 0.2f),
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Rounded.Sync, contentDescription = "同步数据") },
            label = { Text("同步数据") },
            selected = currentRoute == "sync",
            onClick = {
                if (currentRoute != "sync") {
                    navController.navigate("sync") {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PrimaryOrange,
                selectedTextColor = PrimaryOrange,
                indicatorColor = PrimaryOrange.copy(alpha = 0.2f),
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            )
        )
    }
}

fun copyToClipboard(context: Context, text: String) {
    if (text.isEmpty()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("IPs", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

fun getColoLocation(colo: String): String {
    // Simplified mapping based on common codes
    return when(colo) {
        "HKG" -> "Hong Kong · Asia Pacific"
        "LAX" -> "Los Angeles · North America"
        "SJC" -> "San Jose · North America"
        "FRA" -> "Frankfurt · Europe"
        "NRT" -> "Tokyo · Asia Pacific"
        "SIN" -> "Singapore · Asia Pacific"
        "LHR" -> "London · Europe"
        "CDG" -> "Paris · Europe"
        "SYD" -> "Sydney · Oceania"
        else -> "Datacenter: $colo"
    }
}

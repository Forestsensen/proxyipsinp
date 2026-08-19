package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CloudflareSyncRule
import com.example.ui.theme.CardBackground
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.PrimaryOrange
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(viewModel: ScannerViewModel) {
    val allRules by viewModel.allRules.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<CloudflareSyncRule?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Sync, contentDescription = null, tint = PrimaryOrange, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Cloudflare DNS 同步",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "设置目标域名，根据节点/机场过滤器自动或手动同步扫描到的 Cloudflare IP。",
                fontSize = 14.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (allRules.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无同步规则", color = TextSecondary)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(allRules, key = { it.id }) { rule ->
                        SyncRuleCard(
                            rule = rule,
                            onToggle = { viewModel.toggleRuleAutoSync(rule) },
                            onDelete = { viewModel.deleteRule(rule) },
                            onEdit = {
                                editingRule = rule
                                showAddDialog = true
                            },
                            onSync = { viewModel.syncRule(rule) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                editingRule = null
                showAddDialog = true
            },
            containerColor = PrimaryOrange,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).padding(bottom = 60.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add Rule", tint = Color.White)
        }

        if (showAddDialog) {
            AddSyncRuleDialog(
                initialRule = editingRule,
                onDismiss = { showAddDialog = false },
                onSave = {
                    viewModel.saveRule(it)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun SyncRuleCard(
    rule: CloudflareSyncRule,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onSync: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(rule.ruleName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(rule.targetDomain, fontSize = 16.sp, color = PrimaryOrange, fontWeight = FontWeight.Medium)
                }
                Switch(
                    checked = rule.isAutoSync,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PrimaryOrange)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.DarkGray.copy(alpha=0.5f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("机场/过滤器: ${rule.coloFilter}", color = PrimaryOrange, fontSize = 12.sp)
                }
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.DarkGray.copy(alpha=0.5f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("同步数量: ${rule.syncCount} 个 IP", color = PrimaryOrange, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            if (rule.lastSyncStatus.isNotEmpty()) {
                val isSuccess = rule.lastSyncStatus == "同步成功"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (isSuccess) Color.Green else Color.Red))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(rule.lastSyncStatus, color = if (isSuccess) Color.Green else Color.Red, fontSize = 14.sp)
                        if (rule.lastSyncTime > 0) {
                            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(rule.lastSyncTime))
                            Text("上次同步: $timeStr", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha=0.8f))
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = TextSecondary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onSync,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("立即同步")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSyncRuleDialog(
    initialRule: CloudflareSyncRule?,
    onDismiss: () -> Unit,
    onSave: (CloudflareSyncRule) -> Unit
) {
    var ruleName by remember { mutableStateOf(initialRule?.ruleName ?: "") }
    var coloFilter by remember { mutableStateOf(initialRule?.coloFilter ?: "") }
    var targetDomain by remember { mutableStateOf(initialRule?.targetDomain ?: "") }
    var syncCount by remember { mutableStateOf(initialRule?.syncCount?.toString() ?: "1") }
    var zoneId by remember { mutableStateOf(initialRule?.zoneId ?: "") }
    var email by remember { mutableStateOf(initialRule?.email ?: "") }
    var apiKey by remember { mutableStateOf(initialRule?.apiKey ?: "") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss, 
        containerColor = DarkBackground,
        sheetState = sheetState
    ) {
        LazyColumn(modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime)
        ) {
            item {
                Text("添加 Cloudflare 域名同步", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(16.dp))
            }
            item {
                OutlinedTextField(
                    value = ruleName, onValueChange = { ruleName = it },
                    label = { Text("机场/规则名称 (如 HK Node)", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                OutlinedTextField(
                    value = coloFilter, onValueChange = { coloFilter = it },
                    label = { Text("数据中心过滤 (如 HKG,SJC)", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                OutlinedTextField(
                    value = targetDomain, onValueChange = { targetDomain = it },
                    label = { Text("目标域名记录 (如 hk.example.com)", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                OutlinedTextField(
                    value = syncCount, onValueChange = { syncCount = it },
                    label = { Text("IP 同步数量 (默认: 1)", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                OutlinedTextField(
                    value = zoneId, onValueChange = { zoneId = it },
                    label = { Text("Cloudflare Zone ID", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("Cloudflare 邮箱 (使用 Token 时可选)", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                OutlinedTextField(
                    value = apiKey, onValueChange = { apiKey = it },
                    label = { Text("Cloudflare Global API Key 或 Token", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val rule = CloudflareSyncRule(
                                id = initialRule?.id ?: 0,
                                ruleName = ruleName,
                                coloFilter = coloFilter,
                                targetDomain = targetDomain,
                                syncCount = syncCount.toIntOrNull() ?: 1,
                                zoneId = zoneId,
                                email = email,
                                apiKey = apiKey,
                                isAutoSync = initialRule?.isAutoSync ?: true,
                                lastSyncStatus = initialRule?.lastSyncStatus ?: "",
                                lastSyncTime = initialRule?.lastSyncTime ?: 0L
                            )
                            onSave(rule)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
                    ) {
                        Text("保存规则")
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

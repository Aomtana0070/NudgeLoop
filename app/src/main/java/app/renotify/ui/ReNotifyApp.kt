package app.renotify.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.renotify.R
import app.renotify.data.AppEntry
import app.renotify.data.AppRule
import app.renotify.data.PendingReminder
import app.renotify.data.ReminderSettings
import app.renotify.ui.PermissionState

private enum class Destination(val label: String, val icon: ImageVector) {
    Home("หน้าแรก", Icons.Outlined.NotificationsActive),
    Apps("แอป", Icons.Outlined.Apps),
    Rules("กฎเตือน", Icons.AutoMirrored.Outlined.Rule),
    Guide("คู่มือ", Icons.AutoMirrored.Outlined.HelpOutline),
}

@Composable
private fun FloatingNotchBar(selected: Destination, onSelect: (Destination) -> Unit) {
    val selectedPosition by animateFloatAsState(
        targetValue = (Destination.entries.indexOf(selected) + 0.5f) / Destination.entries.size,
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "floating-notch-position",
    )
    val notchShape = remember(selectedPosition) {
        GenericShape { size, _ ->
            val corner = size.height * 0.34f
            val notchHalfWidth = size.height * 0.4f
            val notchDepth = size.height * 0.25f
            val center = (size.width * selectedPosition).coerceIn(notchHalfWidth + corner, size.width - notchHalfWidth - corner)
            moveTo(0f, corner)
            cubicTo(0f, corner * 0.45f, corner * 0.45f, 0f, corner, 0f)
            lineTo(center - notchHalfWidth, 0f)
            cubicTo(center - notchHalfWidth * 0.45f, 0f, center - notchHalfWidth * 0.45f, notchDepth, center, notchDepth)
            cubicTo(center + notchHalfWidth * 0.45f, notchDepth, center + notchHalfWidth * 0.45f, 0f, center + notchHalfWidth, 0f)
            lineTo(size.width - corner, 0f)
            cubicTo(size.width - corner * 0.45f, 0f, size.width, corner * 0.45f, size.width, corner)
            lineTo(size.width, size.height - corner)
            cubicTo(size.width, size.height - corner * 0.45f, size.width - corner * 0.45f, size.height, size.width - corner, size.height)
            lineTo(corner, size.height)
            cubicTo(corner * 0.45f, size.height, 0f, size.height - corner * 0.45f, 0f, size.height - corner)
            close()
        }
    }

    Box(Modifier.fillMaxWidth().navigationBarsPadding().height(80.dp)) {
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 18.dp).height(62.dp),
            shape = notchShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 4.dp,
        ) {}
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(62.dp).padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Destination.entries.forEach { entry ->
                val isSelected = selected == entry
                Column(
                    modifier = Modifier.weight(1f).fillMaxSize()
                        .selectable(selected = isSelected, role = Role.Tab) { onSelect(entry) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.fillMaxWidth().height(42.dp), contentAlignment = Alignment.Center) {
                        if (isSelected) {
                            Surface(
                                modifier = Modifier.size(44.dp).offset(y = (-13).dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                tonalElevation = 5.dp,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(entry.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(23.dp))
                                }
                            }
                        } else {
                            Icon(entry.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        entry.label,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReNotifyApp(
    viewModel: AppViewModel,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenAppNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenBackgroundSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var destination by remember { mutableStateOf(Destination.Home) }
    var selectedRule by remember { mutableStateOf<AppEntry?>(null) }
    var query by remember { mutableStateOf("") }

    BackHandler(enabled = destination != Destination.Home || selectedRule != null) {
        if (selectedRule != null) selectedRule = null else destination = Destination.Home
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { destination = Destination.Guide }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "การตั้งค่าและคู่มือ")
                    }
                },
            )
        },
        bottomBar = {
            FloatingNotchBar(
                selected = destination,
                onSelect = { destination = it; selectedRule = null },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Column(
                Modifier.widthIn(max = 760.dp)
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 20.dp),
            ) {
                when (destination) {
                    Destination.Home -> HomeScreen(
                        state.permissions,
                        state.config.rules.values.count { it.enabled },
                        state.config.pending,
                        onOpenNotificationAccess,
                        onRequestNotificationPermission,
                        onOpenAppNotificationSettings,
                        onOpenExactAlarmSettings,
                        onGoApps = { destination = Destination.Apps },
                        onStop = viewModel::stopReminder,
                    )
                    Destination.Apps -> AppsScreen(
                        apps = state.apps,
                        loading = state.loadingApps,
                        rules = state.config.rules,
                        query = query,
                        onQueryChange = { query = it },
                        onToggle = { app, enabled ->
                            viewModel.saveRule(viewModel.ruleFor(app.packageName).copy(enabled = enabled))
                        },
                        onEdit = { selectedRule = it },
                    )
                    Destination.Rules -> RulesScreen(
                        apps = state.apps,
                        rules = state.config.rules,
                        onEdit = { selectedRule = it },
                        onAdd = { destination = Destination.Apps },
                    )
                    Destination.Guide -> GuideScreen(
                        permissions = state.permissions,
                        settings = state.config.settings,
                        pending = state.config.pending.filterNot { it.demo },
                        diagnostics = state.config.diagnostics,
                        apps = state.apps,
                        enabledRules = state.config.rules.values.count { it.enabled },
                        onScanNotifications = viewModel::refreshNotificationListener,
                        onOpenNotificationAccess = onOpenNotificationAccess,
                        onRequestNotificationPermission = onRequestNotificationPermission,
                        onOpenAppNotificationSettings = onOpenAppNotificationSettings,
                        onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                        onOpenBackgroundSettings = onOpenBackgroundSettings,
                        onSaveSettings = viewModel::saveSettings,
                        onDemo = viewModel::demoReminder,
                    )
                }
            }
        }
    }

    selectedRule?.let { app ->
        RuleEditorDialog(
            app = app,
            initialRule = viewModel.ruleFor(app.packageName),
            senderSuggestions = viewModel.senderSuggestionsFor(app.packageName),
            pendingCount = viewModel.remindersFor(app.packageName).size,
            onDismiss = { selectedRule = null },
            onSave = { viewModel.saveRule(it); selectedRule = null },
        )
    }
}

@Composable
private fun HomeScreen(
    permissions: PermissionState,
    enabledRules: Int,
    pending: List<PendingReminder>,
    onOpenNotificationAccess: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenAppNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onGoApps: () -> Unit,
    onStop: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("ไม่พลาดข้อความ\nที่สำคัญกับคุณ", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("เตือนซ้ำเฉพาะแอปที่คุณเลือก จนกว่าคุณจะจัดการการแจ้งเตือนนั้น", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.NotificationsActive, contentDescription = null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (permissions.notificationAccess && permissions.notificationListenerConnected && permissions.postNotifications && permissions.appNotificationsEnabled && permissions.exactAlarms) "พร้อมเตือนซ้ำ" else "ตรวจสอบการเชื่อมต่อ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("$enabledRules แอปที่เปิดอยู่ · ${pending.count { !it.demo }} รายการกำลังรอ", color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }

        if (!permissions.notificationAccess || !permissions.notificationListenerConnected || !permissions.postNotifications || !permissions.appNotificationsEnabled || !permissions.exactAlarms) {
            SectionTitle("สถานะการตั้งค่า")
            if (!permissions.notificationAccess) PermissionRow(
                "เข้าถึงการแจ้งเตือน", "ตรวจพบข้อความและหยุดเตือนเมื่อคุณจัดการแล้ว", false, onOpenNotificationAccess,
            )
            if (permissions.notificationAccess && !permissions.notificationListenerConnected) PermissionRow(
                "บริการอ่านแจ้งเตือนยังไม่เชื่อมต่อ",
                "กำลังลองเชื่อมต่อใหม่อัตโนมัติ หากยังไม่กลับมาเชื่อม ให้แตะเพื่อตรวจสิทธิ์",
                false,
                onOpenNotificationAccess,
            )
            if (!permissions.postNotifications) PermissionRow(
                "ส่งการแจ้งเตือนซ้ำ", "ต้องอนุญาตให้ NudgeLoop แสดงการเตือน", false, onRequestNotificationPermission,
            )
            if (permissions.postNotifications && !permissions.appNotificationsEnabled) PermissionRow(
                "เปิดการแจ้งเตือนของ NudgeLoop", "ตรวจสอบว่าไม่ได้ปิดการแจ้งเตือนหรือช่องเตือนซ้ำใน Android", false, onOpenAppNotificationSettings,
            )
            if (!permissions.exactAlarms) PermissionRow(
                "เพิ่มความแม่นยำของเวลา", "ไม่เปิดก็ใช้ได้ แต่อาจถูกเลื่อนโดย Android", false, onOpenExactAlarmSettings,
            )
        } else {
            PermissionRow("สิทธิ์พร้อมแล้ว", "ระบบกำลังรับการแจ้งเตือนจากแอปที่คุณเลือก", true, {})
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("กำลังรอ")
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onGoApps) { Text("เลือกแอป") }
        }
        val active = pending.filter { !it.demo && it.nextAtMillis > 0L }
        if (active.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("ยังไม่มีรายการที่ต้องเตือนซ้ำ", fontWeight = FontWeight.SemiBold)
                        Text("เมื่อมีแจ้งเตือนใหม่จากแอปที่เปิดไว้ รายการจะปรากฏตรงนี้", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            active.sortedBy(PendingReminder::nextAtMillis).forEach { item ->
                ReminderRow(item, onStop)
            }
        }

        Text("ข้อมูลการแจ้งเตือนอยู่ในเครื่องนี้ และ NudgeLoop จะไม่ลบข้อความต้นทาง", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppsScreen(
    apps: List<AppEntry>,
    loading: Boolean,
    rules: Map<String, AppRule>,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggle: (AppEntry, Boolean) -> Unit,
    onEdit: (AppEntry) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(top = 4.dp)) {
        Text("เลือกแอปที่อยากให้เตือนซ้ำ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("การตั้งค่าจะแยกตามแอป คุณปิดหรือเปิดได้ตลอดเวลา", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("ค้นหาแอป") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        )
        Spacer(Modifier.height(8.dp))
        val filtered = apps.filter {
            it.label.contains(query, true) || it.packageName.contains(query, true)
        }.sortedWith(compareBy<AppEntry>({ it.packageName != "com.instagram.android" }, { it.label.lowercase() }))
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
        } else if (filtered.isEmpty()) {
            EmptyPanel("ไม่พบแอป", if (apps.isEmpty()) "ยังไม่มีแอปที่แสดงในหน้ารวมแอปของเครื่องนี้" else "ลองค้นหาด้วยชื่อแอปหรือชื่อแพ็กเกจ")
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(filtered, key = AppEntry::packageName) { app ->
                    val rule = rules[app.packageName] ?: AppRule(app.packageName)
                    AppRow(app, rule, onToggle = { onToggle(app, it) }, onEdit = { onEdit(app) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, rule: AppRule, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val bitmap = remember(app.packageName) { app.icon.toBitmap(48, 48).asImageBitmap() }
        Image(bitmap, contentDescription = null, modifier = Modifier.size(42.dp).clip(MaterialTheme.shapes.medium))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(if (rule.enabled) "ทุกข้อความ · ทุก ${rule.intervalMinutes} นาที" else "แตะเพื่อกำหนดผู้ส่งและช่วงเวลา", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Tune, contentDescription = "ตั้งค่าการเตือนของ ${app.label}") }
        Switch(
            checked = rule.enabled,
            onCheckedChange = onToggle,
            modifier = Modifier.semantics { contentDescription = "เตือนซ้ำสำหรับ ${app.label}" },
        )
    }
}

@Composable
private fun RulesScreen(
    apps: List<AppEntry>,
    rules: Map<String, AppRule>,
    onEdit: (AppEntry) -> Unit,
    onAdd: () -> Unit,
) {
    val enabled = rules.values.filter(AppRule::enabled).sortedBy { rule -> apps.firstOrNull { it.packageName == rule.packageName }?.label.orEmpty() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("กฎเตือนซ้ำ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("ปรับช่วงเวลา จำนวนครั้ง และชื่อผู้ส่งให้เหมาะกับแต่ละแอป", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (enabled.isEmpty()) {
            EmptyPanel("ยังไม่มีกฎที่เปิดอยู่", "เลือกแอป แล้วเปิดสวิตช์เพื่อเริ่มเตือนซ้ำ")
            Button(onClick = onAdd) { Text("เลือกแอป") }
        } else {
            enabled.forEach { rule ->
                val app = apps.firstOrNull { it.packageName == rule.packageName } ?: return@forEach
                Surface(
                    onClick = { onEdit(app) },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        val bitmap = remember(app.packageName) { app.icon.toBitmap(48, 48).asImageBitmap() }
                        Image(bitmap, contentDescription = null, modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.medium))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, fontWeight = FontWeight.SemiBold)
                            val senders = rule.senderFilters.takeIf { it.isNotEmpty() }?.joinToString() ?: "ทุกผู้ส่ง"
                            val repeats = rule.maxRepeats?.let { "$it ครั้ง" } ?: "ไม่จำกัดครั้ง"
                            Text("$senders · ทุก ${rule.intervalMinutes} นาที · $repeats", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "แก้ไข")
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideScreen(
    permissions: PermissionState,
    settings: ReminderSettings,
    pending: List<PendingReminder>,
    diagnostics: app.renotify.data.ReminderDiagnostics,
    apps: List<AppEntry>,
    enabledRules: Int,
    onScanNotifications: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenAppNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenBackgroundSettings: () -> Unit,
    onSaveSettings: (ReminderSettings) -> Unit,
    onDemo: () -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("คู่มือและการตั้งค่า", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("ตั้งค่าครั้งเดียว แล้ว NudgeLoop จะคอยเตือนให้คุณกลับไปดูข้อความที่ยังค้างอยู่", color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionTitle("ตรวจสอบการทำงาน")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DiagnosticsRow("สิทธิ์อ่านแจ้งเตือน", if (permissions.notificationAccess) "อนุญาต" else "ยังไม่อนุญาต", permissions.notificationAccess)
                DiagnosticsRow("บริการ listener", if (permissions.notificationListenerConnected) "เชื่อมต่ออยู่" else "ไม่ได้เชื่อมต่อ", permissions.notificationListenerConnected)
                DiagnosticsRow(
                    "บริการพยุงเบื้องหลัง",
                    if (permissions.listenerKeepAliveRunning) {
                        if (permissions.postNotifications && permissions.appNotificationsEnabled) {
                            "ทำงานอยู่ · แจ้งเตือนถาวรเปิดอยู่"
                        } else {
                            "ทำงานอยู่ · Android อาจซ่อนแจ้งเตือนบริการ"
                        }
                    } else {
                        "ยังไม่ทำงาน"
                    },
                    permissions.listenerKeepAliveRunning,
                )
                DiagnosticsRow(
                    "เชื่อมต่อ listener ล่าสุด",
                    diagnosticEvent("", diagnostics.lastListenerConnectedAtMillis, apps),
                )
                DiagnosticsRow(
                    "หลุดการเชื่อมต่อล่าสุด",
                    diagnosticEvent("", diagnostics.lastListenerDisconnectedAtMillis, apps),
                )
                DiagnosticsRow(
                    "ขอเชื่อม listener ใหม่ล่าสุด",
                    diagnostics.lastListenerRebindRequestedAtMillis.takeIf { it > 0L }?.let {
                        "${diagnostics.lastListenerRebindSource} · ${relativeTime(it)}"
                    } ?: "ยังไม่มีคำขอ",
                    diagnostics.lastListenerRebindError.isBlank(),
                )
                if (diagnostics.lastListenerRebindError.isNotBlank()) {
                    DiagnosticsRow("คำขอเชื่อมใหม่ผิดพลาด", diagnostics.lastListenerRebindError, false)
                }
                DiagnosticsRow("ส่งการแจ้งเตือน", if (permissions.postNotifications && permissions.appNotificationsEnabled) "พร้อม" else "ถูกปิดกั้น", permissions.postNotifications && permissions.appNotificationsEnabled)
                DiagnosticsRow("Alarm แม่นยำ", if (permissions.exactAlarms) "อนุญาต" else "ไม่ได้อนุญาต", permissions.exactAlarms)
                DiagnosticsRow("กฎที่เปิดใช้งาน", "$enabledRules แอป")
                DiagnosticsRow(
                    "แจ้งเตือนต้นทางล่าสุด",
                    diagnosticEvent(diagnostics.lastSourcePackage, diagnostics.lastSourceAtMillis, apps),
                )
                DiagnosticsRow(
                    "ผลตรวจ rule ล่าสุด",
                    diagnostics.lastRuleCheckResult.takeIf(String::isNotBlank)?.let { result ->
                        val detail = when (result) {
                            "matched" -> "ผ่านกฎ"
                            "sender_mismatch" -> "ไม่ตรงชื่อผู้ส่ง"
                            "disabled" -> "ปิดกฎแอปนี้"
                            "no_rule" -> "ยังไม่มีกฎของแอปนี้"
                            else -> "ไม่ทราบผล"
                        }
                        val appLabel = apps.firstOrNull { it.packageName == diagnostics.lastRuleCheckedPackage }?.label
                            ?: diagnostics.lastRuleCheckedPackage
                        "$appLabel · $detail · ${relativeTime(diagnostics.lastRuleCheckedAtMillis)}"
                    } ?: "ยังไม่มีข้อมูล",
                )
                DiagnosticsRow(
                    "ประมวลผลผ่านกฎล่าสุด",
                    diagnosticEvent(diagnostics.lastMatchedPackage, diagnostics.lastMatchedAtMillis, apps),
                )
                DiagnosticsRow(
                    "ตั้ง Alarm ล่าสุด",
                    diagnosticEvent(diagnostics.lastAlarmPackage, diagnostics.lastAlarmScheduledAtMillis, apps),
                )
                if (diagnostics.nextAlarmAtMillis > 0L) {
                    DiagnosticsRow("Alarm รอบถัดไป", relativeTime(diagnostics.nextAlarmAtMillis))
                }
                DiagnosticsRow(
                    "รับ Alarm ล่าสุด",
                    diagnosticEvent(diagnostics.lastAlarmReceivedPackage, diagnostics.lastAlarmReceivedAtMillis, apps),
                )
                DiagnosticsRow(
                    "แสดงเตือนซ้ำล่าสุด",
                    diagnosticEvent(diagnostics.lastReminderPostedPackage, diagnostics.lastReminderPostedAtMillis, apps),
                )
                if (diagnostics.lastDeliveryProblem.isNotBlank()) {
                    DiagnosticsRow(
                        "ปัญหาล่าสุด",
                        "${diagnostics.lastDeliveryProblem} · ${relativeTime(diagnostics.lastDeliveryProblemAtMillis)}",
                        false,
                    )
                }
                HorizontalDivider()
                Text(
                    "รายการรอ ${pending.size} รายการ",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (pending.isEmpty()) {
                    Text("ยังไม่มีรายการที่ผ่านกฎ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    pending.sortedBy(PendingReminder::nextAtMillis).take(5).forEach { reminder ->
                        val next = if (reminder.nextAtMillis > 0L) relativeTime(reminder.nextAtMillis) else "ไม่มีรอบถัดไป"
                        Text(
                            "${reminder.appLabel} · ${reminder.sender.ifBlank { "ไม่มีชื่อผู้ส่ง" }} — $next",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "ข้อมูลวินิจฉัยบันทึกเฉพาะเวลาและชื่อแอป ไม่เก็บเนื้อหาการแจ้งเตือน",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onScanNotifications) {
                    Text("สแกนแจ้งเตือนที่ยังค้าง")
                }
                OutlinedButton(onClick = onOpenNotificationAccess) {
                    Text("เปิดการตั้งค่า Notification access")
                }
            }
        }

        SectionTitle("เริ่มต้นใช้งาน")
        NumberedStep("1", "อนุญาตให้ NudgeLoop อ่านการแจ้งเตือน", "ใช้ชื่อแอปและชื่อผู้ส่งเพื่อเลือกกฎ และตรวจเมื่อการแจ้งเตือนต้นทางถูกปิด", permissions.notificationAccess && permissions.notificationListenerConnected, onOpenNotificationAccess)
        NumberedStep("2", "อนุญาตการแจ้งเตือนของ NudgeLoop", "จำเป็นสำหรับการส่งการเตือนซ้ำบน Android 13 ขึ้นไป", permissions.postNotifications, onRequestNotificationPermission)
        NumberedStep("3", "เปิดช่องทางแจ้งเตือนของ NudgeLoop", "ถ้าปิดแอปหรือช่องเตือนซ้ำไว้ Android จะไม่แสดงการเตือน", permissions.appNotificationsEnabled, onOpenAppNotificationSettings)
        NumberedStep("4", "อนุญาตนาฬิกาปลุกที่แม่นยำ", "ถ้าไม่อนุญาต Android 12 ขึ้นไปอาจเลื่อนเวลาได้ถึงประมาณ 1 ชั่วโมง", permissions.exactAlarms, onOpenExactAlarmSettings)
        InfoRow(Icons.Outlined.Bedtime, "พยุง listener เมื่อปิดแอป", "เมื่ออนุญาตอ่านแจ้งเตือนและการแจ้งเตือนของ NudgeLoop แอปจะเปิดบริการเบื้องหน้าพร้อมแจ้งเตือนถาวรเพื่อช่วยคง process และลองเชื่อม listener ใหม่")
        Text("Redmi/Xiaomi: เปิดข้อมูลแอป NudgeLoop > ประหยัดแบตเตอรี่ > ไม่มีข้อจำกัด และเปิดเริ่มการทำงานอัตโนมัติ/Background autostart หากมี", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onOpenBackgroundSettings) { Text("ตั้งค่าแบตเตอรี่และการทำงานเบื้องหลัง") }
        Text("แตะชื่อขั้นตอนเพื่อเปิดหน้าตั้งค่าที่เกี่ยวข้อง", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionTitle("ตั้งค่าขั้นสูง")
        SettingToggle("ซ่อนตัวอย่างข้อความ", "แสดงเฉพาะชื่อแอปและผู้ส่งในเตือนซ้ำ", !settings.showPreview, Icons.Outlined.VisibilityOff) {
            onSaveSettings(settings.copy(showPreview = !it))
        }
        SettingToggle("มีเสียงเตือน", "ปรับเสียงและความสำคัญได้เพิ่มใน Android Settings", settings.soundEnabled, Icons.AutoMirrored.Outlined.VolumeUp) {
            onSaveSettings(settings.copy(soundEnabled = it))
        }
        SettingToggle("ช่วงงดเตือน", "เลื่อนการเตือนไปหลังช่วงเวลานี้", settings.quietHoursEnabled, Icons.Outlined.Bedtime) {
            onSaveSettings(settings.copy(quietHoursEnabled = it))
        }
        if (settings.quietHoursEnabled) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeButton("เริ่ม", settings.quietStartMinute) { onSaveSettings(settings.copy(quietStartMinute = it)) }
                TimeButton("สิ้นสุด", settings.quietEndMinute) { onSaveSettings(settings.copy(quietEndMinute = it)) }
            }
        }

        SectionTitle("ทดสอบการแจ้งเตือน")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ส่งตัวอย่างเตือนทันที แล้วเตือนซ้ำอีก 1 นาทีถัดไป", fontWeight = FontWeight.SemiBold)
                Text("ใช้ตรวจการแสดงผลและการตั้งเวลาใน Emulator โดยไม่ต้องมีบัญชี Instagram หากเปิดช่วงงดเตือน ตัวอย่างจะรอจนหมดช่วงนั้น", color = MaterialTheme.colorScheme.onTertiaryContainer)
                Button(onClick = onDemo, enabled = permissions.postNotifications && permissions.appNotificationsEnabled) { Text("ทดลองเตือน") }
            }
        }

        SectionTitle("ความเป็นส่วนตัวและข้อจำกัด")
        InfoRow(Icons.Outlined.PrivacyTip, "ไม่เชื่อมบัญชี", "อ่านข้อความจากการแจ้งเตือนที่ Android ส่งให้ และเก็บกฎกับชื่อหัวข้อสำหรับคำแนะนำไว้ในเครื่อง")
        InfoRow(Icons.Outlined.NotificationsOff, "หยุดเตือนได้เอง", "ปิดการแจ้งเตือนต้นทางหรือกด ‘หยุดเตือน’ ในเตือนซ้ำเพื่อยกเลิกรอบถัดไป")
        InfoRow(Icons.Outlined.Bedtime, "เวลาอาจคลาดเคลื่อน", "โหมดประหยัดพลังงานและข้อจำกัดของผู้ผลิตมือถืออาจทำให้ Android เลื่อนการเตือน")
        TextButton(onClick = {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            context.startActivity(intent)
        }) { Text("จัดการเสียงและการแจ้งเตือนใน Android Settings") }
    }
}

@Composable
private fun DiagnosticsRow(title: String, value: String, healthy: Boolean? = null) {
    Row(verticalAlignment = Alignment.Top) {
        if (healthy != null) {
            Icon(
                if (healthy) Icons.Outlined.CheckCircle else Icons.Outlined.NotificationsOff,
                contentDescription = null,
                tint = if (healthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun diagnosticEvent(packageName: String, atMillis: Long, apps: List<AppEntry>): String {
    if (atMillis <= 0L) return "ยังไม่มีข้อมูล"
    if (packageName.isBlank()) return relativeTime(atMillis)
    val appLabel = apps.firstOrNull { it.packageName == packageName }?.label ?: packageName
    return "$appLabel · ${relativeTime(atMillis)}"
}

private fun relativeTime(atMillis: Long): String =
    android.text.format.DateUtils.getRelativeTimeSpanString(
        atMillis,
        System.currentTimeMillis(),
        android.text.format.DateUtils.MINUTE_IN_MILLIS,
    ).toString()

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
private fun RuleEditorDialog(
    app: AppEntry,
    initialRule: AppRule,
    senderSuggestions: List<String>,
    pendingCount: Int,
    onDismiss: () -> Unit,
    onSave: (AppRule) -> Unit,
) {
    var enabled by remember(app.packageName, initialRule) { mutableStateOf(initialRule.enabled) }
    var senders by remember(app.packageName, initialRule) { mutableStateOf(initialRule.senderFilters) }
    var senderQuery by remember(app.packageName, initialRule) { mutableStateOf("") }
    var suggestionsExpanded by remember(app.packageName) { mutableStateOf(false) }
    var interval by remember(app.packageName, initialRule) { mutableStateOf(initialRule.intervalMinutes.toString()) }
    var limit by remember(app.packageName, initialRule) { mutableStateOf(initialRule.maxRepeats?.toString() ?: "3") }
    var unlimited by remember(app.packageName, initialRule) { mutableStateOf(initialRule.maxRepeats == null) }
    var exact by remember(app.packageName, initialRule) { mutableStateOf(initialRule.exactSenderMatch) }
    var error by remember { mutableStateOf<String?>(null) }
    val matchingSuggestions = senderSuggestions
        .filter { senderQuery.isBlank() || it.contains(senderQuery.trim(), ignoreCase = true) }
        .filterNot { suggestion -> senders.any { it.equals(suggestion, ignoreCase = true) } }
        .take(if (senderQuery.isBlank()) 6 else 8)

    fun addSender(value: String = senderQuery) {
        val name = value.trim().take(80)
        if (name.isBlank() || senders.any { it.equals(name, ignoreCase = true) }) return
        senders = senders + name
        senderQuery = ""
        suggestionsExpanded = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ตั้งค่า ${app.label}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("เปิดเตือนซ้ำ", fontWeight = FontWeight.Medium)
                        Text("สำหรับการแจ้งเตือนใหม่จากแอปนี้", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        modifier = Modifier.semantics { contentDescription = "เปิดเตือนซ้ำสำหรับ ${app.label}" },
                    )
                }
                Text("เลือกผู้ส่งได้หลายคน · ไม่เลือก = ทุกคน", style = MaterialTheme.typography.labelLarge)
                if (senders.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        senders.forEach { name ->
                            InputChip(
                                selected = true,
                                onClick = { senders = senders.filterNot { it == name } },
                                label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = "เอา $name ออก") },
                            )
                        }
                    }
                    if (senderQuery.isBlank() && matchingSuggestions.isNotEmpty()) {
                        Text("ผู้ส่งที่พบล่าสุด", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            matchingSuggestions.forEach { suggestion ->
                                InputChip(
                                    selected = false,
                                    onClick = { addSender(suggestion) },
                                    label = { Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                )
                            }
                        }
                    }
                }
                Box {
                    OutlinedTextField(
                        value = senderQuery,
                        onValueChange = {
                            senderQuery = it.take(80)
                            suggestionsExpanded = true
                            error = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("ชื่อผู้ส่ง") },
                        supportingText = { Text("พิมพ์เพื่อค้นหาชื่อที่เคยปรากฏในแจ้งเตือนของแอปนี้") },
                        trailingIcon = {
                            IconButton(onClick = { addSender() }, enabled = senderQuery.isNotBlank()) {
                                Icon(Icons.Outlined.Add, contentDescription = "เพิ่มชื่อผู้ส่ง")
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addSender() }),
                        singleLine = true,
                    )
                    DropdownMenu(
                        expanded = suggestionsExpanded && senderQuery.isNotBlank() && matchingSuggestions.isNotEmpty(),
                        onDismissRequest = { suggestionsExpanded = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        matchingSuggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text(suggestion) },
                                onClick = { addSender(suggestion) },
                            )
                        }
                    }
                }
                Text("ค้นหาได้จากทุกส่วนของชื่อหัวข้อแจ้งเตือน คำแนะนำเก็บไว้ในเครื่องและแสดงล่าสุดสูงสุด 6 รายชื่อ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = exact, onCheckedChange = { exact = it })
                    Text(if (exact) "ตรงกับชื่อทั้งหมด" else "มีชื่อที่เลือกอยู่ในหัวข้อก็พอ")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = interval,
                        onValueChange = { interval = it.filter(Char::isDigit).map { digit -> digit.digitToInt().toString() }.joinToString(separator = "").take(4); error = null },
                        modifier = Modifier.weight(1f),
                        label = { Text("ทุกกี่นาที") },
                        supportingText = { Text("1–1440 นาที") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = limit,
                        onValueChange = { limit = it.filter(Char::isDigit).map { digit -> digit.digitToInt().toString() }.joinToString(separator = "").take(2); error = null },
                        modifier = Modifier.weight(1f),
                        label = { Text("เตือนกี่ครั้ง") },
                        supportingText = { Text("1–20 ครั้ง") },
                        enabled = !unlimited,
                        singleLine = true,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = unlimited, onCheckedChange = { unlimited = it; error = null })
                    Column {
                        Text("เตือนไม่จำกัดครั้ง", fontWeight = FontWeight.Medium)
                        Text("เตือนต่อไปจนกว่าจะปิดแจ้งเตือนต้นทางหรือกดหยุด", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (pendingCount > 0) Text("มี $pendingCount รายการรออยู่ · การแก้กฎจะนำไปใช้ทันที", color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val minutes = interval.toIntOrNull()
                val repeats = limit.toIntOrNull()
                if (minutes == null || minutes !in 1..1440) error = "ช่วงเวลาต้องอยู่ระหว่าง 1 ถึง 1440 นาที"
                else if (!unlimited && (repeats == null || repeats !in 1..20)) error = "จำนวนครั้งต้องอยู่ระหว่าง 1 ถึง 20 ครั้ง"
                else onSave(initialRule.copy(
                    enabled = enabled,
                    senderFilters = (senders + senderQuery.trim().takeIf(String::isNotBlank).orEmpty())
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                        .distinctBy(String::lowercase),
                    exactSenderMatch = exact,
                    intervalMinutes = minutes,
                    maxRepeats = if (unlimited) null else repeats,
                ))
            }) { Text("บันทึก") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ยกเลิก") } },
    )
}

@Composable
private fun ReminderRow(reminder: PendingReminder, onStop: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${reminder.appLabel}${reminder.sender.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}", fontWeight = FontWeight.SemiBold)
                val whenText = if (reminder.nextAtMillis <= System.currentTimeMillis()) "กำลังเตือน" else "เตือนอีก ${ceilMinutes(reminder.nextAtMillis - System.currentTimeMillis())} นาที"
                val count = reminder.maxRepeats?.let { "${reminder.sentRepeats}/$it ครั้ง" } ?: "ครั้งที่ ${reminder.sentRepeats} · ไม่จำกัด"
                Text("$whenText · $count", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onStop(reminder.key) }) {
                Icon(Icons.Outlined.StopCircle, contentDescription = "หยุดเตือน ${reminder.appLabel}")
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, detail: String, granted: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (granted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.NotificationsActive, contentDescription = null, tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!granted) Icon(Icons.Outlined.ChevronRight, contentDescription = "เปิดการตั้งค่า")
        }
    }
}

@Composable
private fun NumberedStep(number: String, title: String, detail: String, granted: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (granted) "✓" else number, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!granted) Icon(Icons.Outlined.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun SettingToggle(title: String, detail: String, checked: Boolean, icon: ImageVector, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}

@Composable
private fun TimeButton(label: String, minute: Int, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    val hour = minute / 60
    val min = minute % 60
    OutlinedButton(
        onClick = {
            TimePickerDialog(context, { _, newHour, newMinute -> onChange(newHour * 60 + newMinute) }, hour, min, true).show()
        },
    ) { Text("$label  ${"%02d:%02d".format(hour, min)}") }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyPanel(title: String, detail: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(label: String) {
    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

private fun ceilMinutes(millis: Long) = ((millis + 59_999L) / 60_000L).coerceAtLeast(1L)

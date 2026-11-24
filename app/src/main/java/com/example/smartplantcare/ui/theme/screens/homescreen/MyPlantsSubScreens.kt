package com.example.smartplantcare.ui.theme.screens.homescreen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import com.example.smartplantcare.util.NetworkMonitor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartplantcare.iot.CropZoneConfig
import com.example.smartplantcare.iot.DeviceConfig
import com.example.smartplantcare.iot.DeviceRole
import com.example.smartplantcare.iot.IotDomainLogic
import com.example.smartplantcare.iot.LiveDeviceSnapshot
import com.example.smartplantcare.iot.ShareInvite
import java.util.Locale

// ─── PAGE 4: Share My Plant & Access Control ─────────────────────────────────

@Composable
internal fun MyPlantsShareScreen(
    snapshot: LiveDeviceSnapshot,
    currentUserUid: String,
    currentUserRole: DeviceRole,
    latestInvite: ShareInvite?,
    isBusy: Boolean,
    onBack: () -> Unit,
    onGenerateInvite: (DeviceRole) -> Unit,
    onRevokeMember: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedRole by remember { mutableStateOf(DeviceRole.OPERATOR) }
    var copiedNotice by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeColors.PageBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HomeDimens.ScreenHorizontalPadding)
            .padding(top = HomeDimens.ScreenTopPadding, bottom = HomeDimens.BottomBarClearance)
    ) {
        SubPageHeader(
            title = "Share My Plant",
            subtitle = snapshot.meta.displayName,
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (currentUserRole.canManageSharing) {
            Surface(
                color = HomeColors.SurfaceWhite,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, HomeColors.CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Invite Family or Caretaker",
                        fontFamily = HomeInterFont,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Choose what the invited account can do, then generate a single-use 24-hour code.",
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        color = HomeColors.TextSecondary,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RoleChoiceCard(
                            title = "Operator",
                            desc = "Water plants & edit thresholds",
                            selected = selectedRole == DeviceRole.OPERATOR,
                            onClick = { selectedRole = DeviceRole.OPERATOR },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        RoleChoiceCard(
                            title = "Viewer",
                            desc = "Read-only sensors & alerts",
                            selected = selectedRole == DeviceRole.VIEWER,
                            onClick = { selectedRole = DeviceRole.VIEWER },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            copiedNotice = false
                            onGenerateInvite(selectedRole)
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HomeColors.ForestPrimary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Filled.Share, null, tint = Color.White, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Generate 24-Hour ${selectedRole.label} Code",
                            fontFamily = HomeInterFont,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    val displayInvite = (latestInvite ?: snapshot.activeInvites.firstOrNull())?.takeIf { invite ->
                        val now = System.currentTimeMillis()
                        val isExpired = (invite.expiresAt > 0L && now >= invite.expiresAt) ||
                                (invite.createdAt > 0L && (now - invite.createdAt) >= 24 * 60 * 60 * 1000L) ||
                                invite.status != "PENDING"
                        !isExpired
                    }
                    if (displayInvite != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            color = HomeColors.SoftMint,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, HomeColors.ForestPrimary.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${displayInvite.role.label} INVITE CODE (24H)",
                                        fontFamily = HomeInterFont,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HomeColors.ForestMedium
                                    )
                                    Text(
                                        text = displayInvite.inviteCode,
                                        fontFamily = HomeInterFont,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black,
                                        color = HomeColors.ForestPrimary
                                    )
                                    if (copiedNotice) {
                                        Text(
                                            text = "Copied to clipboard!",
                                            fontFamily = HomeInterFont,
                                            fontSize = 11.sp,
                                            color = HomeColors.SuccessGreen
                                        )
                                    }
                                }
                                Surface(
                                    color = if (copiedNotice) HomeColors.SuccessGreen else HomeColors.ForestPrimary,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("Invite Code", displayInvite.inviteCode))
                                            copiedNotice = true
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (copiedNotice) Icons.Filled.Check else Icons.Filled.ContentCopy,
                                            contentDescription = "Copy Code",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (copiedNotice) "Copied" else "Copy",
                                            fontFamily = HomeInterFont,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Members List
        Text(
            text = "Authorized Members (${snapshot.members.size})",
            fontFamily = HomeInterFont,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = HomeColors.TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            snapshot.members.forEach { member ->
                val isSelf = member.uid == currentUserUid
                Surface(
                    color = HomeColors.SurfaceWhite,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, HomeColors.CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(HomeColors.SoftMint),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Person, null, tint = HomeColors.ForestPrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = member.displayName.ifBlank { member.email.ifBlank { member.uid } } +
                                    if (isSelf) " (You)" else "",
                                fontFamily = HomeInterFont,
                                fontSize = 13.sp,
                                lineHeight = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = HomeColors.TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (member.email.isNotBlank()) {
                                Text(
                                    text = member.email,
                                    fontFamily = HomeInterFont,
                                    fontSize = 11.5.sp,
                                    color = HomeColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = if (member.role == DeviceRole.OWNER) HomeColors.SoftMint else HomeColors.WaterBg,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = member.role.label,
                                    fontFamily = HomeInterFont,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (member.role == DeviceRole.OWNER) HomeColors.ForestPrimary else HomeColors.WaterBlue,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                                )
                            }
                        }
                        if (member.role != DeviceRole.OWNER && (currentUserRole.canManageSharing || isSelf)) {
                            Spacer(modifier = Modifier.width(6.dp))
                            TextButton(
                                onClick = { onRevokeMember(member.uid) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isSelf) "Leave" else "Remove",
                                    fontFamily = HomeInterFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HomeColors.AlertRed,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleChoiceCard(
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) HomeColors.SoftMint else HomeColors.PageBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) HomeColors.ForestPrimary else HomeColors.CardBorder),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                fontFamily = HomeInterFont,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) HomeColors.ForestPrimary else HomeColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                fontFamily = HomeInterFont,
                fontSize = 11.sp,
                color = HomeColors.TextSecondary,
                lineHeight = 15.sp
            )
        }
    }
}

// ─── PAGE 5: Live Alerts & Irrigation Event History Log ──────────────────────

@Composable
internal fun MyPlantsEventLogScreen(
    snapshot: LiveDeviceSnapshot,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit = {}
) {
    var filter by remember { mutableStateOf("ALL") }
    val filtered = remember(snapshot.events, filter) {
        when (filter) {
            "CRITICAL" -> snapshot.events.filter { it.severity.equals("critical", true) }
            "WARNING" -> snapshot.events.filter { it.severity.equals("warning", true) }
            "IRRIGATION" -> snapshot.events.filter { it.type.contains("IRRIGATION", true) }
            else -> snapshot.events
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeColors.PageBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = HomeDimens.ScreenHorizontalPadding)
            .padding(top = HomeDimens.ScreenTopPadding)
    ) {
        SubPageHeader(
            title = "Device Alerts & Activity",
            subtitle = "${snapshot.meta.displayName} • ${snapshot.events.size} events",
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            color = HomeColors.SoftMint,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, HomeColors.ForestPrimary.copy(alpha = 0.25f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onOpenHistory)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = null,
                        tint = HomeColors.ForestPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "7-Day Sensor Graphs & Watering Records",
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.ForestPrimary,
                        maxLines = 2
                    )
                }
                Text(
                    text = "Open →",
                    fontFamily = HomeInterFont,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.ForestPrimary,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val filterItems = remember {
            listOf("ALL" to "All", "CRITICAL" to "Critical", "WARNING" to "Warnings", "IRRIGATION" to "Irrigation")
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filterItems, key = { it.first }) { (key, label) ->
                val active = filter == key
                Surface(
                    color = if (active) HomeColors.ForestPrimary else HomeColors.SurfaceWhite,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, if (active) HomeColors.ForestPrimary else HomeColors.CardBorder),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { filter = key }
                ) {
                    Text(
                        text = label,
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) Color.White else HomeColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No events recorded for this filter yet.",
                    fontFamily = HomeInterFont,
                    fontSize = 13.sp,
                    color = HomeColors.TextSecondary
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = HomeDimens.BottomBarClearance)
            ) {
                items(filtered, key = { it.eventId }) { ev ->
                    val (bg, fg) = when (ev.severity.lowercase()) {
                        "critical" -> HomeColors.AlertBg to HomeColors.AlertRed
                        "warning" -> HomeColors.WarningBg to HomeColors.WarningAmber
                        else -> HomeColors.SuccessBg to HomeColors.SuccessGreen
                    }
                    Surface(
                        color = HomeColors.SurfaceWhite,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, HomeColors.CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = ev.title,
                                    fontFamily = HomeInterFont,
                                    fontSize = 13.sp,
                                    lineHeight = 17.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HomeColors.TextPrimary,
                                    maxLines = 2,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                )
                                Surface(color = bg, shape = RoundedCornerShape(50)) {
                                    Text(
                                        text = ev.severity.uppercase(),
                                        fontFamily = HomeInterFont,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = fg,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            if (ev.message.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = ev.message,
                                    fontFamily = HomeInterFont,
                                    fontSize = 12.sp,
                                    color = HomeColors.TextSecondary,
                                    lineHeight = 17.sp
                                )
                            }
                            if (ev.createdAt > 0L) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = HomeDateFormatter.formatDateTime(ev.createdAt),
                                    fontFamily = HomeInterFont,
                                    fontSize = 10.sp,
                                    color = HomeColors.TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── PAGE 6: Device Settings, Rename, Calibration & Unclaim Reset ────────────

@Composable
internal fun MyPlantsSettingsScreen(
    snapshot: LiveDeviceSnapshot,
    role: DeviceRole,
    isBusy: Boolean,
    onBack: () -> Unit,
    onRenameBox: (String) -> Unit,
    onSaveConfig: (DeviceConfig) -> Unit,
    onUnclaimDevice: () -> Unit
) {
    var boxName by remember(snapshot.meta.displayName) { mutableStateOf(snapshot.meta.displayName) }
    var draft by remember(snapshot.config) { mutableStateOf(snapshot.config) }
    var showUnclaimConfirm by remember { mutableStateOf(false) }

    val isPhoneOnline by NetworkMonitor.isOnlineFlow.collectAsState()
    val phoneDisconnectedAtMs by NetworkMonitor.disconnectedAtMsFlow.collectAsState()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(snapshot.deviceId, snapshot.receivedAtClientMs, snapshot.status.lastSeenAt) {
        NetworkMonitor.refreshOnlineState()
        nowMs = System.currentTimeMillis()
        while (true) {
            delay(2_000L)
            NetworkMonitor.refreshOnlineState()
            nowMs = System.currentTimeMillis()
        }
    }
    val evalNowMs = if (!isPhoneOnline && phoneDisconnectedAtMs != null) {
        phoneDisconnectedAtMs!!
    } else {
        nowMs
    }
    val isDeviceOnline = IotDomainLogic.isDeviceOnlineFresh(snapshot, nowMs = evalNowMs)
    val isNoInternetWhileBoxOnline = isDeviceOnline && !isPhoneOnline
    val canEditSettings = role.canControl && isDeviceOnline && isPhoneOnline

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeColors.PageBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HomeDimens.ScreenHorizontalPadding)
            .padding(top = HomeDimens.ScreenTopPadding, bottom = HomeDimens.BottomBarClearance),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SubPageHeader(
            title = "Device Settings & Calibration",
            subtitle = snapshot.deviceId,
            onBack = onBack
        )

        // Status notice banner when ESP32 is offline OR phone lost internet while ESP32 is online
        if (isNoInternetWhileBoxOnline || !isDeviceOnline) {
            Surface(
                color = HomeColors.AlertBg,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, HomeColors.AlertRed.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (isNoInternetWhileBoxOnline) Icons.Filled.WifiOff else Icons.Filled.CloudOff,
                        contentDescription = if (isNoInternetWhileBoxOnline) "No Internet Connection" else "ESP32 Offline",
                        tint = HomeColors.AlertRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isNoInternetWhileBoxOnline) "No Internet Connection" else "Plant Box is Offline",
                            fontFamily = HomeInterFont,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.AlertRed
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isNoInternetWhileBoxOnline) {
                                "Check your internet connection. Your plant box is online, but settings cannot be changed while your phone has no internet."
                            } else {
                                "Settings, thresholds, and calibration adjustments are disabled until your plant box reconnects."
                            },
                            fontFamily = HomeInterFont,
                            fontSize = 11.sp,
                            color = HomeColors.TextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // 1. Rename Plant Box Card
        Surface(
            color = HomeColors.SurfaceWhite,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, HomeColors.CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Plant Box Name",
                    fontFamily = HomeInterFont,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = boxName,
                        onValueChange = { boxName = it.take(35) },
                        enabled = canEditSettings,
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HomeColors.ForestPrimary,
                            unfocusedBorderColor = HomeColors.CardBorder,
                            focusedLabelColor = HomeColors.ForestPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { onRenameBox(boxName) },
                        enabled = canEditSettings && boxName.isNotBlank() && boxName.trim() != snapshot.meta.displayName,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(52.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HomeColors.ForestPrimary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Filled.Edit, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // 2. Watering & Sound Controls
        Surface(
            color = HomeColors.SurfaceWhite,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, HomeColors.CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Watering & Sound Controls",
                    fontFamily = HomeInterFont,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary
                )
                SettingToggleRow(
                    title = "Allow Watering Output",
                    subtitle = "Keep ON to let your plant box deliver water during auto and manual cycles.",
                    checked = draft.physicalLoadsEnabled,
                    enabled = canEditSettings,
                    onCheckedChange = { draft = draft.copy(physicalLoadsEnabled = it) }
                )
                HorizontalDivider(color = HomeColors.DividerLine, thickness = 1.dp)
                SettingToggleRow(
                    title = "Mute Box Alert Sounds",
                    subtitle = "Silences the beeper on the box while keeping screen and phone alerts active.",
                    checked = draft.buzzerMuted,
                    enabled = canEditSettings,
                    onCheckedChange = { draft = draft.copy(buzzerMuted = it) }
                )
            }
        }

        // 3. Per-Crop Moisture Thresholds
        Surface(
            color = HomeColors.SurfaceWhite,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, HomeColors.CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Per-Crop Irrigation Thresholds",
                    fontFamily = HomeInterFont,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary
                )
                CropThresholdEditor("Tomato", draft.tomato, canEditSettings) {
                    draft = draft.copy(tomato = it)
                }
                CropThresholdEditor("Chili", draft.chili, canEditSettings) {
                    draft = draft.copy(chili = it)
                }
                CropThresholdEditor("Eggplant", draft.eggplant, canEditSettings) {
                    draft = draft.copy(eggplant = it)
                }
            }
        }

        // 4. 7-Inch Reservoir & Weather Thresholds
        Surface(
            color = HomeColors.SurfaceWhite,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, HomeColors.CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "7-Inch Reservoir & Rain Thresholds",
                    fontFamily = HomeInterFont,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary
                )
                Text(
                    text = String.format(
                        Locale.US,
                        "Full Gap: %.1f in  •  Empty Gap: %.1f in  •  Rain Gate: >=%d%%",
                        draft.reservoirFullGapIn,
                        draft.reservoirEmptyGapIn,
                        draft.rainThresholdPct
                    ),
                    fontFamily = HomeInterFont,
                    fontSize = 12.sp,
                    color = HomeColors.TextSecondary
                )
                Text("Rain Suspension Threshold: ${draft.rainThresholdPct}%", fontFamily = HomeInterFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = draft.rainThresholdPct.toFloat(),
                    onValueChange = { draft = draft.copy(rainThresholdPct = it.toInt()) },
                    valueRange = 30f..90f,
                    enabled = canEditSettings,
                    colors = SliderDefaults.colors(thumbColor = HomeColors.ForestPrimary, activeTrackColor = HomeColors.ForestPrimary)
                )
                Text(
                    text = String.format(Locale.US, "Empty Tank Air Gap: %.1f in", draft.reservoirEmptyGapIn),
                    fontFamily = HomeInterFont,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = draft.reservoirEmptyGapIn,
                    onValueChange = { draft = draft.copy(reservoirEmptyGapIn = it) },
                    valueRange = 5.0f..10.0f,
                    enabled = canEditSettings,
                    colors = SliderDefaults.colors(thumbColor = HomeColors.WaterBlue, activeTrackColor = HomeColors.WaterBlue)
                )
            }
        }

        if (role.canControl) {
            Button(
                onClick = { onSaveConfig(draft) },
                enabled = canEditSettings && !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canEditSettings) HomeColors.ForestPrimary else HomeColors.CardBorder,
                    contentColor = if (canEditSettings) Color.White else HomeColors.TextMuted,
                    disabledContainerColor = HomeColors.CardBorder.copy(alpha = 0.5f),
                    disabledContentColor = HomeColors.TextMuted
                )
            ) {
                Icon(
                    imageVector = when {
                        isNoInternetWhileBoxOnline -> Icons.Filled.WifiOff
                        !isDeviceOnline -> Icons.Filled.CloudOff
                        else -> Icons.Filled.Save
                    },
                    contentDescription = null,
                    tint = if (canEditSettings) Color.White else HomeColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        isBusy -> "Saving..."
                        isNoInternetWhileBoxOnline -> "No Internet Connection"
                        !isDeviceOnline -> "ESP32 Offline • Adjustments Disabled"
                        else -> "Save Settings to ESP32"
                    },
                    fontFamily = HomeInterFont,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canEditSettings) Color.White else HomeColors.TextMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        // 5. Device Information
        Surface(
            color = HomeColors.SurfaceWhite,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, HomeColors.CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Device Information",
                    fontFamily = HomeInterFont,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Controller ID",
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        color = HomeColors.TextSecondary
                    )
                    Text(
                        text = snapshot.deviceId,
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HomeColors.TextPrimary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }
                HorizontalDivider(color = HomeColors.DividerLine, thickness = 1.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Firmware Version",
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        color = HomeColors.TextSecondary
                    )
                    Text(
                        text = "v3.1",
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HomeColors.ForestPrimary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }
                HorizontalDivider(color = HomeColors.DividerLine, thickness = 1.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Local Network IP",
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        color = HomeColors.TextSecondary
                    )
                    Text(
                        text = snapshot.status.ipAddress.ifBlank { "--" },
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HomeColors.TextPrimary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 6. Danger Zone (Owner Only): Unclaim & Factory Reset Plant Box
        if (role.canUnclaim) {
            Surface(
                color = HomeColors.AlertBg,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, HomeColors.AlertRed.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = HomeColors.AlertRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Danger Zone (Owner Only)",
                            fontFamily = HomeInterFont,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.AlertRed
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Removing this plant box immediately stops active watering, removes all shared members, clears saved Wi-Fi settings, and resets the box so it can be paired again.",
                        fontFamily = HomeInterFont,
                        fontSize = 11.sp,
                        color = HomeColors.TextSecondary,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showUnclaimConfirm = true },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, HomeColors.AlertRed)
                    ) {
                        Icon(Icons.Filled.DeleteForever, null, tint = HomeColors.AlertRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Remove & Reset Plant Box",
                            fontFamily = HomeInterFont,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.AlertRed
                        )
                    }
                }
            }
        }
    }

    if (showUnclaimConfirm) {
        AlertDialog(
            onDismissRequest = { showUnclaimConfirm = false },
            containerColor = HomeColors.SurfaceWhite,
            title = {
                Text(
                    text = "Remove & Reset Plant Box?",
                    fontFamily = HomeInterFont,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary
                )
            },
            text = {
                Text(
                    text = "All shared members will lose access to ${snapshot.meta.displayName} immediately, saved Wi-Fi will be cleared, and the box will reopen setup mode for pairing.",
                    fontFamily = HomeInterFont,
                    fontSize = 13.sp,
                    color = HomeColors.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnclaimConfirm = false
                        onUnclaimDevice()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HomeColors.AlertRed,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Unclaim & Reset",
                        fontFamily = HomeInterFont,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnclaimConfirm = false }) {
                    Text("Cancel", fontFamily = HomeInterFont, color = HomeColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun CropThresholdEditor(
    title: String,
    cfg: CropZoneConfig,
    enabled: Boolean,
    onChange: (CropZoneConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HomeColors.PageBg)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = title,
                    fontFamily = HomeInterFont,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary,
                    maxLines = 2
                )
                Text(
                    text = "Dry ≤ ${cfg.startPct.toInt()}% → Stop ≥ ${cfg.stopPct.toInt()}%",
                    fontFamily = HomeInterFont,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeColors.ForestPrimary,
                    maxLines = 2
                )
            }
            Switch(
                checked = cfg.enabled,
                onCheckedChange = { onChange(cfg.copy(enabled = it)) },
                enabled = enabled,
                colors = SwitchDefaults.colors(checkedTrackColor = HomeColors.ForestPrimary)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Dry Start Threshold (triggers auto watering): ${cfg.startPct.toInt()}%",
            fontFamily = HomeInterFont,
            fontSize = 11.sp,
            color = HomeColors.TextSecondary
        )
        Slider(
            value = cfg.startPct,
            onValueChange = { start ->
                val adjustedStop = cfg.stopPct.coerceAtLeast(start + 10f).coerceAtMost(90f)
                onChange(cfg.copy(startPct = start, stopPct = adjustedStop))
            },
            valueRange = 15f..65f,
            enabled = enabled && cfg.enabled,
            colors = SliderDefaults.colors(thumbColor = HomeColors.ForestPrimary, activeTrackColor = HomeColors.ForestPrimary)
        )
        Text(
            text = "Wet Stop Threshold (stops auto watering): ${cfg.stopPct.toInt()}%",
            fontFamily = HomeInterFont,
            fontSize = 11.sp,
            color = HomeColors.TextSecondary
        )
        val minStop = (cfg.startPct + 10f).coerceAtMost(85f)
        Slider(
            value = cfg.stopPct.coerceIn(minStop, 90f),
            onValueChange = { stop ->
                onChange(cfg.copy(stopPct = stop.coerceIn(minStop, 90f)))
            },
            valueRange = minStop..90f,
            enabled = enabled && cfg.enabled,
            colors = SliderDefaults.colors(thumbColor = HomeColors.WaterBlue, activeTrackColor = HomeColors.WaterBlue)
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = HomeInterFont,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = HomeColors.TextPrimary
            )
            Text(
                text = subtitle,
                fontFamily = HomeInterFont,
                fontSize = 11.sp,
                color = HomeColors.TextSecondary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = HomeColors.ForestPrimary)
        )
    }
}

@Composable
private fun SubPageHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = HomeColors.SurfaceWhite,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, HomeColors.CardBorder),
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onBack)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = HomeColors.ForestPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = HomeInterFont,
                fontSize = 19.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold,
                color = HomeColors.TextPrimary,
                maxLines = 2
            )
            Text(
                text = subtitle,
                fontFamily = HomeInterFont,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = HomeColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

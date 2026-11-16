package com.example.smartplantcare.ui.theme.screens.homescreen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack

import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing

// ─── Color Palette ───────────────────────────────────────────────────────────
private val PrimaryDark   = Color(0xFF0D2B1F)
private val PrimaryGreen  = Color(0xFF1B4332)
private val SoftGreen     = Color(0xFFE8F5E9)
private val LightBg       = Color(0xFFF5F7F5)
private val TextDark      = Color(0xFF1F2937)
private val TextMedium    = Color(0xFF4B5563)
private val TextLight     = Color(0xFF9CA3AF)
private val Divider       = Color(0xFFE5E7EB)
private val ReadCardBg    = Color(0xFFFFFFFF)
private val UnreadCardBg  = Color(0xFFF0FDF4)
private val StatusGreen   = Color(0xFF059669)
private val StatusAmber   = Color(0xFFD97706)
private val StatusRed     = Color(0xFFDC2626)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryScreen(
    viewModel: NotificationHistoryViewModel,
    onBackClick: () -> Unit,
    onPlanClick: (String) -> Unit = {}
) {
    val notifications by viewModel.notifications.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Optimize: Only show badge when unreadCount changes
    val showBadge by remember(unreadCount) { derivedStateOf { unreadCount > 0 } }

    LaunchedEffect(Unit) {
        viewModel.loadNotifications()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Notifications",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = PrimaryDark
                        )
                        if (showBadge) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = Color(0xFFDC2626),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "$unreadCount new",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryDark
                        )
                    }
                },
                actions = {
                    if (notifications.isNotEmpty() && unreadCount > 0) {
                        TextButton(
                            onClick = { viewModel.markAllAsRead() },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = PrimaryGreen
                            )
                        ) {
                            Icon(
                                Icons.Default.DoneAll,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Mark all read",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    scrolledContainerColor = Color.White
                ),
                windowInsets = WindowInsets.safeDrawing
            )
        },
        containerColor = LightBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = PrimaryGreen,
                        strokeWidth = 3.dp
                    )
                }
                notifications.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = SoftGreen,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.NotificationsOff,
                                    contentDescription = null,
                                    tint = PrimaryGreen.copy(alpha = 0.6f),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            "No notifications yet",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDark
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Your plant care reminders and alerts\nwill appear here",
                            fontSize = 14.sp,
                            color = TextMedium,
                            lineHeight = 20.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                else -> {
                    // Section header
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                if (unreadCount > 0) "Recent" else "All notifications",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextMedium,
                                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                            )
                        }
                        items(
                            count = notifications.size,
                            key = { index -> notifications[index].id }
                        ) { index ->
                            NotificationItem(
                                notification = notifications[index],
                                onClick = {
                                    viewModel.markAsRead(notifications[index].id)
                                    val plantId = notifications[index].plantId
                                    if (plantId.isNotBlank()) {
                                        onPlanClick(plantId)
                                    }
                                },
                                onDelete = { viewModel.deleteNotification(notifications[index].id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationItem(
    notification: com.example.smartplantcare.data.NotificationHistory,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val dateStr = dateFormat.format(Date(notification.timestamp))
    val timeStr = timeFormat.format(Date(notification.timestamp))

    // Check if this is an overdue notification
    val isOverdue = notification.title.contains("Overdue", ignoreCase = true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isOverdue -> Color(0xFFFFF8F8)
                !notification.isRead -> Color(0xFFF7FCF8)
                else -> Color.White
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (notification.isRead) 0.dp else 1.dp
        ),
        border = BorderStroke(
            width = 1.dp,
            color = when {
                isOverdue -> StatusRed.copy(alpha = 0.15f)
                !notification.isRead -> PrimaryGreen.copy(alpha = 0.15f)
                else -> Divider.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // ─── Plant Image with Unread Badge Overlay ────────────────────
            Box(
                modifier = Modifier.size(56.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isOverdue -> Color(0xFFFEE2E2)
                                !notification.isRead -> SoftGreen
                                else -> Color(0xFFF3F4F6)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val imageModel = remember(notification.plantImageUrl) {
                        if (notification.plantImageUrl.isNotBlank()) {
                            val file = File(notification.plantImageUrl)
                            if (file.exists()) android.net.Uri.fromFile(file) else notification.plantImageUrl
                        } else null
                    }

                    if (imageModel != null) {
                        AsyncImage(
                            model = imageModel,
                            contentDescription = "Plant",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = if (isOverdue) "⚠️" else "🌿",
                            fontSize = 24.sp
                        )
                    }
                }

                // Clean notification badge overlay (dot) on top-right corner of the image
                if (isOverdue || !notification.isRead) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .offset(x = 4.dp, y = (-4).dp)
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(
                                if (isOverdue) StatusRed else PrimaryGreen
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // ─── Notification Content ─────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                // Plant name + status badges row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = notification.plantName.ifBlank { notification.title },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isOverdue -> StatusRed
                            else -> PrimaryDark
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Day Badge
                    Surface(
                        color = SoftGreen,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Day ${notification.day}",
                            fontSize = 10.sp,
                            color = PrimaryGreen,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(6.dp))

                    // Status Badge
                    Surface(
                        color = when {
                            isOverdue -> StatusRed.copy(alpha = 0.1f)
                            notification.isCompleted -> StatusGreen.copy(alpha = 0.1f)
                            else -> StatusAmber.copy(alpha = 0.1f)
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = when {
                                isOverdue -> "Overdue"
                                notification.isCompleted -> "Done"
                                else -> "Pending"
                            },
                            fontSize = 10.sp,
                            color = when {
                                isOverdue -> StatusRed
                                notification.isCompleted -> StatusGreen
                                else -> StatusAmber
                            },
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Message
                Text(
                    text = notification.message,
                    fontSize = 13.sp,
                    color = if (notification.isRead) TextMedium else TextDark,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                    fontWeight = if (!notification.isRead) FontWeight.Medium else FontWeight.Normal
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Timestamp
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = TextLight,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$dateStr • $timeStr",
                        fontSize = 11.sp,
                        color = TextLight,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // ─── Delete Button ────────────────────────────────────────────
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(36.dp)
                    .padding(0.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = TextLight,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

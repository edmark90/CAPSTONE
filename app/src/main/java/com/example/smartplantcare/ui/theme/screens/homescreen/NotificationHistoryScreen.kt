package com.example.smartplantcare.ui.theme.screens.homescreen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.smartplantcare.data.NotificationHistory
import java.io.File

@Composable
fun NotificationHistoryScreen(
    viewModel: NotificationHistoryViewModel,
    onBackClick: () -> Unit,
    onPlanClick: (String) -> Unit = {}
) {
    val notifications by viewModel.notifications.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL" or "UNREAD"

    val filteredNotifications = remember(notifications, selectedFilter) {
        if (selectedFilter == "UNREAD") {
            notifications.filter { !it.isRead }
        } else {
            notifications
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadNotifications()
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    text = "Clear All Notifications",
                    fontFamily = HomeInterFont,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary
                )
            },
            text = {
                Text(
                    text = "Remove all notification history? This will not cancel your active care reminders.",
                    fontFamily = HomeInterFont,
                    fontSize = 14.sp,
                    color = HomeColors.TextSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllNotifications()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HomeColors.AlertRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Clear All",
                        fontFamily = HomeInterFont,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showClearDialog = false },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HomeColors.CardBorder)
                ) {
                    Text(
                        text = "Cancel",
                        fontFamily = HomeInterFont,
                        color = HomeColors.TextSecondary
                    )
                }
            },
            containerColor = HomeColors.SurfaceWhite,
            shape = RoundedCornerShape(22.dp)
        )
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HomeColors.SurfaceWhite)
                    .statusBarsPadding()
            ) {
                // Clean, Spacious Editorial Top Bar (Zero overcrowding!)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = HomeColors.PageBg,
                            shape = RoundedCornerShape(13.dp),
                            border = BorderStroke(1.dp, HomeColors.CardBorder),
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .clickable(onClick = onBackClick)
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Notifications",
                                    fontFamily = HomeInterFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = HomeColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (unreadCount > 0) {
                                    Surface(
                                        color = HomeColors.AlertRed,
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            text = "$unreadCount new",
                                            fontFamily = HomeInterFont,
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Plant care alerts & ESP32 updates",
                                fontFamily = HomeInterFont,
                                fontSize = 11.sp,
                                color = HomeColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (notifications.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = HomeColors.AlertBg,
                            shape = RoundedCornerShape(13.dp),
                            border = BorderStroke(1.dp, HomeColors.AlertRed.copy(alpha = 0.22f)),
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .clickable { showClearDialog = true }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear all notifications",
                                    tint = HomeColors.AlertRed,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = HomeColors.DividerLine, thickness = 1.dp)
            }
        },
        containerColor = HomeColors.PageBg
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
                        color = HomeColors.ForestPrimary,
                        strokeWidth = 3.dp
                    )
                }

                notifications.isEmpty() -> {
                    EmptyNotificationsState(
                        isUnreadFilter = false
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 14.dp,
                            bottom = 28.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Dedicated Filter + Prominent "Mark all read" Action Bar
                        item(key = "filter_and_mark_read_bar") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NotificationFilterPill(
                                        label = "All (${notifications.size})",
                                        selected = selectedFilter == "ALL",
                                        onClick = { selectedFilter = "ALL" }
                                    )
                                    NotificationFilterPill(
                                        label = "Unread ($unreadCount)",
                                        selected = selectedFilter == "UNREAD",
                                        onClick = { selectedFilter = "UNREAD" }
                                    )
                                }

                                if (unreadCount > 0) {
                                    Surface(
                                        color = HomeColors.ForestPrimary,
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .clickable { viewModel.markAllAsRead() }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DoneAll,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = "Mark all read",
                                                fontFamily = HomeInterFont,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (filteredNotifications.isEmpty()) {
                            item(key = "empty_unread_state") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 48.dp)
                                ) {
                                    EmptyNotificationsState(isUnreadFilter = true)
                                }
                            }
                        } else {
                            items(
                                items = filteredNotifications,
                                key = { it.id }
                            ) { notification ->
                                NotificationItem(
                                    notification = notification,
                                    onClick = {
                                        viewModel.markAsRead(notification.id)
                                        val plantId = notification.plantId
                                        if (plantId.isNotBlank()) {
                                            onPlanClick(plantId)
                                        }
                                    },
                                    onDelete = { viewModel.deleteNotification(notification.id) }
                                )
                            }

                            item(key = "cap_footer") {
                                Text(
                                    text = "Keeping your latest ${notifications.size} of 50 notifications",
                                    fontFamily = HomeInterFont,
                                    fontSize = 11.sp,
                                    color = HomeColors.TextMuted,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
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
private fun NotificationFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) HomeColors.ForestPrimary else HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(50),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) HomeColors.ForestPrimary else HomeColors.CardBorder
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontFamily = HomeInterFont,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else HomeColors.TextSecondary,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun EmptyNotificationsState(isUnreadFilter: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = HomeColors.SoftMint,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isUnreadFilter) Icons.Default.CheckCircle else Icons.Default.NotificationsOff,
                    contentDescription = null,
                    tint = HomeColors.ForestMedium,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isUnreadFilter) "You're all caught up!" else "No notifications yet",
            fontFamily = HomeInterFont,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = HomeColors.TextPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isUnreadFilter) {
                "All your plant care notifications have been read."
            } else {
                "Your plant care reminders and ESP32 box alerts will appear here."
            },
            fontFamily = HomeInterFont,
            fontSize = 13.sp,
            color = HomeColors.TextSecondary,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun NotificationItem(
    notification: NotificationHistory,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val formattedDateTime = remember(notification.timestamp) {
        HomeDateFormatter.formatDateTime(notification.timestamp)
    }

    val isOverdue = notification.title.contains("Overdue", ignoreCase = true)
    val isGeneralDaily = notification.plantId.isBlank()

    val imageRequest = remember(notification.plantImageUrl) {
        if (notification.plantImageUrl.isNotBlank()) {
            val data: Any = if (notification.plantImageUrl.startsWith("/")) {
                File(notification.plantImageUrl)
            } else {
                notification.plantImageUrl
            }
            ImageRequest.Builder(context)
                .data(data)
                .size(160)
                .crossfade(false)
                .build()
        } else null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isOverdue -> Color(0xFFFFFAFA)
                !notification.isRead -> Color(0xFFF3FAF6)
                else -> HomeColors.SurfaceWhite
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            width = if (!notification.isRead || isOverdue) 1.2.dp else 1.dp,
            color = when {
                isOverdue -> HomeColors.AlertRed.copy(alpha = 0.28f)
                !notification.isRead -> HomeColors.ForestPrimary.copy(alpha = 0.28f)
                else -> HomeColors.CardBorder
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(modifier = Modifier.size(50.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            when {
                                isOverdue -> HomeColors.AlertBg
                                !notification.isRead -> HomeColors.SoftMint
                                else -> HomeColors.PlaceholderBg
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        imageRequest != null -> {
                            AsyncImage(
                                model = imageRequest,
                                contentDescription = "Plant",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        isOverdue -> {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = HomeColors.AlertRed,
                                modifier = Modifier.size(23.dp)
                            )
                        }
                        isGeneralDaily -> {
                            Icon(
                                imageVector = Icons.Default.WbSunny,
                                contentDescription = null,
                                tint = HomeColors.ForestMedium,
                                modifier = Modifier.size(23.dp)
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.Eco,
                                contentDescription = null,
                                tint = HomeColors.ForestPrimary,
                                modifier = Modifier.size(23.dp)
                            )
                        }
                    }
                }

                if (isOverdue || !notification.isRead) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .offset(x = 3.dp, y = (-3).dp)
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(if (isOverdue) HomeColors.AlertRed else HomeColors.ForestPrimary)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = notification.plantName.ifBlank { notification.title },
                        fontFamily = HomeInterFont,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOverdue) HomeColors.AlertRed else HomeColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    if (isGeneralDaily) {
                        Surface(
                            color = HomeColors.SoftMint,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "Daily",
                                fontFamily = HomeInterFont,
                                fontSize = 10.sp,
                                color = HomeColors.ForestPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = HomeColors.SoftMint,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "Day ${notification.day}",
                                fontFamily = HomeInterFont,
                                fontSize = 10.sp,
                                color = HomeColors.ForestPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(5.dp))

                        Surface(
                            color = when {
                                isOverdue -> HomeColors.AlertBg
                                notification.isCompleted -> HomeColors.SuccessBg
                                else -> HomeColors.WarningBg
                            },
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = when {
                                    isOverdue -> "Overdue"
                                    notification.isCompleted -> "Done"
                                    else -> "Pending"
                                },
                                fontFamily = HomeInterFont,
                                fontSize = 10.sp,
                                color = when {
                                    isOverdue -> HomeColors.AlertRed
                                    notification.isCompleted -> HomeColors.SuccessGreen
                                    else -> HomeColors.WarningAmber
                                },
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = notification.message,
                    fontFamily = HomeInterFont,
                    fontSize = 13.sp,
                    color = if (notification.isRead) HomeColors.TextSecondary else HomeColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                    fontWeight = if (!notification.isRead) FontWeight.Medium else FontWeight.Normal
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = HomeColors.TextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formattedDateTime,
                            fontFamily = HomeInterFont,
                            fontSize = 11.sp,
                            color = HomeColors.TextMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Surface(
                        color = HomeColors.PageBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDelete)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = HomeColors.TextMuted,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

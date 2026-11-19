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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
    var showClearDialog by remember { mutableStateOf(false) }

    val showBadge by remember(unreadCount) { derivedStateOf { unreadCount > 0 } }

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
                    text = "Remove all notification history? This will not cancel your active reminders.",
                    fontFamily = HomeInterFont,
                    fontSize = 14.sp,
                    color = HomeColors.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllNotifications()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HomeColors.AlertRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Clear All", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showClearDialog = false },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HomeColors.CardBorder)
                ) {
                    Text("Cancel", color = HomeColors.TextSecondary)
                }
            },
            containerColor = HomeColors.SurfaceWhite,
            shape = RoundedCornerShape(22.dp)
        )
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(HomeColors.SurfaceWhite)) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Notifications",
                                fontFamily = HomeInterFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                color = HomeColors.TextPrimary
                            )
                            if (showBadge) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = HomeColors.AlertRed,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "$unreadCount new",
                                        fontFamily = HomeInterFont,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = HomeColors.TextPrimary
                            )
                        }
                    },
                    actions = {
                        if (notifications.isNotEmpty() && unreadCount > 0) {
                            TextButton(
                                onClick = { viewModel.markAllAsRead() },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = HomeColors.ForestPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Mark read",
                                    fontFamily = HomeInterFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        if (notifications.isNotEmpty()) {
                            IconButton(onClick = { showClearDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear all notifications",
                                    tint = HomeColors.TextSecondary
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = HomeColors.SurfaceWhite,
                        scrolledContainerColor = HomeColors.SurfaceWhite
                    ),
                    windowInsets = WindowInsets.statusBars
                )
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
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
                                    imageVector = Icons.Default.NotificationsOff,
                                    contentDescription = null,
                                    tint = HomeColors.ForestMedium,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "No notifications yet",
                            fontFamily = HomeInterFont,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Your plant care reminders and alerts will appear here.",
                            fontFamily = HomeInterFont,
                            fontSize = 13.sp,
                            color = HomeColors.TextSecondary,
                            lineHeight = 19.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 28.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                text = if (unreadCount > 0) "Recent" else "All notifications",
                                fontFamily = HomeInterFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HomeColors.TextSecondary,
                                modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
                            )
                        }
                        items(
                            items = notifications,
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
                    }
                }
            }
        }
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isOverdue -> Color(0xFFFFFAFA)
                !notification.isRead -> Color(0xFFF5FBF7)
                else -> HomeColors.SurfaceWhite
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            width = 1.dp,
            color = when {
                isOverdue -> HomeColors.AlertRed.copy(alpha = 0.22f)
                !notification.isRead -> HomeColors.ForestPrimary.copy(alpha = 0.22f)
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
            Box(modifier = Modifier.size(52.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
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
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        isGeneralDaily -> {
                            Icon(
                                imageVector = Icons.Default.WbSunny,
                                contentDescription = null,
                                tint = HomeColors.ForestMedium,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.Eco,
                                contentDescription = null,
                                tint = HomeColors.ForestPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                if (isOverdue || !notification.isRead) {
                    Box(
                        modifier = Modifier
                            .size(11.dp)
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
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Daily",
                                fontFamily = HomeInterFont,
                                fontSize = 10.sp,
                                color = HomeColors.ForestPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = HomeColors.SoftMint,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Day ${notification.day}",
                                fontFamily = HomeInterFont,
                                fontSize = 10.sp,
                                color = HomeColors.ForestPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(5.dp))

                        Surface(
                            color = when {
                                isOverdue -> HomeColors.AlertBg
                                notification.isCompleted -> HomeColors.SuccessBg
                                else -> HomeColors.WarningBg
                            },
                            shape = RoundedCornerShape(6.dp)
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
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

                Spacer(modifier = Modifier.height(7.dp))

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
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = HomeColors.TextMuted,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

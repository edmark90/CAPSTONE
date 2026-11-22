package com.example.smartplantcare.ui.theme.screens.homescreen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.smartplantcare.util.NetworkMonitor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.smartplantcare.R
import com.example.smartplantcare.data.TreatmentPlan
import com.example.smartplantcare.iot.IotDomainLogic
import com.example.smartplantcare.iot.MyPlantsUiState
import kotlinx.coroutines.delay
import java.io.File
import java.util.Calendar
import java.util.Locale

@Composable
fun HomeScreen(
    userName: String,
    userEmail: String,
    unreadCount: Int = 0,
    plans: List<TreatmentPlan> = emptyList(),
    myPlantsUiState: MyPlantsUiState? = null,
    onOpenMyPlants: () -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onScanClick: () -> Unit = {},
    onIdentifyClick: () -> Unit = {},
    onDoctorClick: () -> Unit = {},
    onPlanClick: (String) -> Unit = {}
) {
    val greetingLabel = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val greetingName = userName.ifBlank { "Plant Lover" }

    val activePlans = remember(plans) {
        plans.filter { it.isActive && it.computedCurrentDayIndex < 7 }.take(3)
    }
    val healthyCount = remember(plans) { plans.count { it.isHealthy } }
    val inCareCount = remember(plans) { plans.count { !it.isHealthy && it.isActive } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeColors.PageBg)
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = HomeDimens.ScreenHorizontalPadding,
            end = HomeDimens.ScreenHorizontalPadding,
            top = HomeDimens.ScreenTopPadding,
            bottom = HomeDimens.BottomBarClearance
        ),
        verticalArrangement = Arrangement.spacedBy(HomeDimens.SectionSpacing)
    ) {
        // ─── 1. Top Header ─────────────────────────────────────────────────
        item {
            HomeHeader(
                greetingLabel = greetingLabel,
                userName = greetingName,
                subtitle = userEmail.ifBlank { "Your smart garden is ready today" },
                unreadCount = unreadCount,
                onNotificationClick = onNotificationClick
            )
        }

        // ─── 2. Flagship SmartPlantCare Box Hero Card ──────────────────────
        item {
            SmartPlantCareBoxHomeCard(
                uiState = myPlantsUiState,
                onOpenMyPlants = onOpenMyPlants
            )
        }

        // ─── 3. AI Leaf Health Scanner Feature Card ────────────────────────
        item {
            HeroScanCard(onScanClick = onScanClick)
        }

        // ─── 4. Quick Tools Row ────────────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading(title = "Quick Tools")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickToolCard(
                        icon = Icons.Filled.LocalFlorist,
                        title = "Identify Species",
                        subtitle = "Online plant lookup",
                        badgeText = null,
                        iconBg = HomeColors.SoftMint,
                        iconTint = HomeColors.ForestPrimary,
                        onClick = onIdentifyClick,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    QuickToolCard(
                        icon = Icons.Filled.MedicalServices,
                        title = "AI Doctor",
                        subtitle = if (plans.isEmpty()) "Treatment plans"
                        else "${plans.size} saved plant${if (plans.size == 1) "" else "s"}",
                        badgeText = if (inCareCount > 0) "$inCareCount active" else null,
                        iconBg = HomeColors.WaterBg,
                        iconTint = HomeColors.WaterBlue,
                        onClick = onDoctorClick,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }

        // ─── 4. Garden Overview Strip (when plans exist) ───────────────────
        if (plans.isNotEmpty()) {
            item {
                GardenSummaryStrip(
                    totalPlans = plans.size,
                    healthyCount = healthyCount,
                    inCareCount = inCareCount,
                    onClick = onDoctorClick
                )
            }
        }

        // ─── 5. Today's Care Routine ───────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeading(title = "Today's Care Tasks")
                if (plans.isNotEmpty()) {
                    Text(
                        text = "View all",
                        fontFamily = HomeInterFont,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HomeColors.ForestMedium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDoctorClick)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
        }

        if (activePlans.isEmpty()) {
            item {
                EmptyCarePromptCard(onScanClick = onScanClick)
            }
        } else {
            items(activePlans, key = { it.id }) { plan ->
                TodayTaskCard(
                    plan = plan,
                    onClick = { onPlanClick(plan.id) }
                )
            }
        }

        // ─── 6. Daily Plant Care Tips ──────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeading(title = "Daily Care Tips")
                CareTipsCard()
            }
        }
    }
}

@Composable
private fun HomeHeader(
    greetingLabel: String,
    userName: String,
    subtitle: String,
    unreadCount: Int,
    onNotificationClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = HomeColors.SurfaceWhite,
                border = BorderStroke(1.dp, HomeColors.CardBorder),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(6.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.appslogo2),
                        contentDescription = "SmartPlantCare",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$greetingLabel,",
                    fontFamily = HomeInterFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = HomeColors.ForestMedium
                )
                Text(
                    text = userName,
                    fontFamily = HomeInterFont,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontFamily = HomeInterFont,
                    fontSize = 12.sp,
                    color = HomeColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            Surface(
                shape = CircleShape,
                color = HomeColors.SurfaceWhite,
                border = BorderStroke(1.dp, HomeColors.CardBorder),
                modifier = Modifier.size(46.dp)
            ) {
                IconButton(onClick = onNotificationClick) {
                    Icon(
                        imageVector = if (unreadCount > 0) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                        contentDescription = "Notifications",
                        tint = HomeColors.ForestPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                        .clip(CircleShape)
                        .background(HomeColors.AlertRed)
                        .border(1.5.dp, HomeColors.PageBg, CircleShape)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroScanCard(onScanClick: () -> Unit) {
    val softGradient = remember {
        Brush.linearGradient(
            colors = listOf(HomeColors.SoftMint, HomeColors.SurfaceWhite)
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onScanClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HomeColors.SurfaceWhite),
        border = BorderStroke(1.dp, HomeColors.ForestPrimary.copy(alpha = 0.22f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(softGradient)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(HomeColors.ForestPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AI Leaf Health Scanner",
                                fontFamily = HomeInterFont,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = HomeColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Detect leaf issues offline & get a 7-day care plan",
                                fontFamily = HomeInterFont,
                                fontSize = 12.sp,
                                color = HomeColors.TextSecondary,
                                lineHeight = 16.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Tomato", "Chili", "Eggplant").forEach { crop ->
                            Surface(
                                color = HomeColors.SurfaceWhite,
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, HomeColors.CardBorder)
                            ) {
                                Text(
                                    text = crop,
                                    fontFamily = HomeInterFont,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = HomeColors.ForestPrimary,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onScanClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HomeColors.ForestPrimary,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text(
                            text = "Scan Leaf",
                            fontFamily = HomeInterFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickToolCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badgeText: String?,
    iconBg: Color,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = HomeColors.SurfaceWhite),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(21.dp)
                    )
                }
                if (badgeText != null) {
                    Surface(
                        color = HomeColors.AlertBg,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = badgeText,
                            fontFamily = HomeInterFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.AlertRed,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = HomeColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    fontFamily = HomeInterFont,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontFamily = HomeInterFont,
                    fontSize = 12.sp,
                    color = HomeColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun GardenSummaryStrip(
    totalPlans: Int,
    healthyCount: Int,
    inCareCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = HomeColors.SurfaceWhite),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SummaryStatItem(
                value = totalPlans.toString(),
                label = "Total Plans",
                color = HomeColors.ForestPrimary,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(HomeColors.DividerLine)
            )
            SummaryStatItem(
                value = healthyCount.toString(),
                label = "Healthy",
                color = HomeColors.SuccessGreen,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(HomeColors.DividerLine)
            )
            SummaryStatItem(
                value = inCareCount.toString(),
                label = "In Care",
                color = HomeColors.AlertRed,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryStatItem(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontFamily = HomeInterFont,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontFamily = HomeInterFont,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = HomeColors.TextSecondary
        )
    }
}

@Composable
private fun TodayTaskCard(
    plan: TreatmentPlan,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val dayIndex = plan.computedCurrentDayIndex.coerceIn(0, 6)
    val rawTask = plan.sevenDayPlan.getOrNull(dayIndex) ?: "Check plant leaves and care routine."
    val taskSummary = remember(rawTask) {
        rawTask.substringAfter(":").trim().ifBlank { rawTask }
    }
    val progress = (plan.completedCount / 7f).coerceIn(0f, 1f)

    val imageRequest = remember(plan.imageUrl) {
        if (plan.imageUrl.isNotBlank()) {
            val data: Any = if (plan.imageUrl.startsWith("/")) File(plan.imageUrl) else plan.imageUrl
            ImageRequest.Builder(context)
                .data(data)
                .size(192)
                .crossfade(false)
                .build()
        } else null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = HomeColors.SurfaceWhite),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(HomeColors.SoftMint),
                contentAlignment = Alignment.Center
            ) {
                if (imageRequest != null) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = plan.plantName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Eco,
                        contentDescription = null,
                        tint = HomeColors.ForestPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = plan.plantName.ifBlank { "Plant" },
                        fontFamily = HomeInterFont,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        color = if (plan.isHealthy) HomeColors.SuccessBg else HomeColors.SoftMint,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "Day ${dayIndex + 1}/7",
                            fontFamily = HomeInterFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (plan.isHealthy) HomeColors.SuccessGreen else HomeColors.ForestPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = taskSummary,
                    fontFamily = HomeInterFont,
                    fontSize = 12.sp,
                    color = HomeColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (plan.isHealthy) HomeColors.SuccessGreen else HomeColors.ForestPrimary,
                    trackColor = HomeColors.SoftMint
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Open Plan",
                tint = HomeColors.TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun EmptyCarePromptCard(onScanClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onScanClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = HomeColors.SurfaceWhite),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(HomeColors.SoftMint),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = HomeColors.ForestMedium,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "No active treatment tasks",
                    fontFamily = HomeInterFont,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Scan a plant leaf to create a personalized 7-day care schedule.",
                    fontFamily = HomeInterFont,
                    fontSize = 12.sp,
                    color = HomeColors.TextSecondary,
                    lineHeight = 17.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = HomeColors.TextMuted
            )
        }
    }
}

@Composable
private fun CareTipsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = HomeColors.SurfaceWhite),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CareTipRow(
                icon = Icons.Filled.WaterDrop,
                iconBg = Color(0xFFE0F2FE),
                iconTint = Color(0xFF0284C7),
                title = "Water at the base",
                description = "Keep soil moist in the morning and avoid wetting leaves late at night."
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(HomeColors.DividerLine)
            )
            CareTipRow(
                icon = Icons.Filled.WbSunny,
                iconBg = Color(0xFFFEF3C7),
                iconTint = Color(0xFFD97706),
                title = "Good airflow & light",
                description = "Space Tomato, Chili, and Eggplant leaves for airflow to reduce fungal spots."
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(HomeColors.DividerLine)
            )
            CareTipRow(
                icon = Icons.Filled.HealthAndSafety,
                iconBg = HomeColors.SoftMint,
                iconTint = HomeColors.ForestPrimary,
                title = "Inspect undersides weekly",
                description = "Early spots and pests often appear underneath lower leaves first."
            )
        }
    }
}

@Composable
private fun CareTipRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    description: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = HomeInterFont,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = HomeColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontFamily = HomeInterFont,
                fontSize = 12.sp,
                color = HomeColors.TextSecondary,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun SectionHeading(title: String) {
    Text(
        text = title,
        fontFamily = HomeInterFont,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = HomeColors.TextPrimary
    )
}

// ─── 3. SmartPlantCare Box Quick Status Card ──────────────────────────────────
@Composable
private fun SmartPlantCareBoxHomeCard(
    uiState: MyPlantsUiState?,
    onOpenMyPlants: () -> Unit
) {
    val snapshot = uiState?.liveSnapshot
    val isConnected = snapshot != null
    val boxName = snapshot?.meta?.displayName?.ifBlank { "SmartPlantCare Box" } ?: "SmartPlantCare Box"

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(
        snapshot?.deviceId,
        snapshot?.receivedAtClientMs,
        snapshot?.status?.lastSeenAt,
        snapshot?.telemetry?.updatedAt
    ) {
        NetworkMonitor.refreshOnlineState()
        nowMs = System.currentTimeMillis()
        while (snapshot != null) {
            delay(2_000L)
            NetworkMonitor.refreshOnlineState()
            nowMs = System.currentTimeMillis()
        }
    }
    val isPhoneOnline by NetworkMonitor.isOnlineFlow.collectAsState()
    val phoneDisconnectedAtMs by NetworkMonitor.disconnectedAtMsFlow.collectAsState()
    val evalNowMs = if (!isPhoneOnline && phoneDisconnectedAtMs != null) {
        phoneDisconnectedAtMs!!
    } else {
        nowMs
    }
    val isOnline = snapshot != null && IotDomainLogic.isDeviceOnlineFresh(
        status = snapshot.status,
        nowMs = evalNowMs,
        receivedAtClientMs = snapshot.receivedAtClientMs,
        telemetryUpdatedAt = snapshot.telemetry.updatedAt
    )
    val isAuto = snapshot?.config?.autoIrrigationEnabled ?: true
    val modeLabel = if (!isOnline) "--" else if (isAuto) "AUTO" else "MANUAL"

    val heroGradient = remember {
        Brush.linearGradient(
            colors = listOf(HomeColors.ForestDark, HomeColors.ForestPrimary, HomeColors.ForestMedium)
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = HomeColors.SurfaceWhite),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Top Deep Botanical Gradient Banner ────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(heroGradient)
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawCircle(
                        color = HomeColors.MintGlow.copy(alpha = 0.10f),
                        radius = size.width * 0.42f,
                        center = Offset(size.width * 0.92f, size.height * 0.12f)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        radius = size.width * 0.28f,
                        center = Offset(size.width * 0.08f, size.height * 0.96f)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Top Status + Mode Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = when {
                                !isConnected -> Color.White.copy(alpha = 0.16f)
                                isOnline && !isPhoneOnline -> HomeColors.AlertRed.copy(alpha = 0.30f)
                                isOnline -> HomeColors.MintGlow.copy(alpha = 0.20f)
                                else -> HomeColors.AlertRed.copy(alpha = 0.30f)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                !isConnected -> HomeColors.MintGlow
                                                isOnline && !isPhoneOnline -> Color(0xFFFCA5A5)
                                                isOnline -> HomeColors.MintGlow
                                                else -> Color(0xFFFCA5A5)
                                            }
                                        )
                                )
                                Text(
                                    text = when {
                                        !isConnected -> "Smart Garden Ready"
                                        isOnline && !isPhoneOnline -> "No Internet"
                                        isOnline -> "Online • Live"
                                        else -> "Offline"
                                    },
                                    fontFamily = HomeInterFont,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        !isConnected -> Color.White
                                        isOnline && !isPhoneOnline -> Color(0xFFFCA5A5)
                                        isOnline -> HomeColors.MintGlow
                                        else -> Color(0xFFFCA5A5)
                                    }
                                )
                            }
                        }

                        if (isConnected) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.White.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = if (!isOnline) "--" else if (isAuto) "AUTO CARE" else "MANUAL",
                                    fontFamily = HomeInterFont,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Box Name & Location Subtitle
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = boxName,
                            fontFamily = HomeInterFont,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val locSubtitle = when {
                            !isConnected -> "Automated 3-zone watering for Tomato, Chili & Eggplant"
                            isOnline && !isPhoneOnline -> "No internet connection • Check your internet"
                            !isOnline -> "Box is offline • Sensor readings paused (--)"
                            snapshot.meta.locationName.isNotBlank() -> snapshot.meta.locationName
                            snapshot.weather.locationName.isNotBlank() -> snapshot.weather.locationName
                            else -> "Live automated garden monitoring"
                        }
                        Text(
                            text = locSubtitle,
                            fontFamily = HomeInterFont,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.82f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Frosted Climate Strip inside Hero Banner (when connected)
                    if (isConnected) {
                        val tempText = if (isOnline && snapshot.telemetry.dhtValid && snapshot.telemetry.temperatureC != null) {
                            String.format(Locale.US, "%.1f°C", snapshot.telemetry.temperatureC)
                        } else "--"
                        val humText = if (isOnline && snapshot.telemetry.dhtValid && snapshot.telemetry.humidityPct != null) {
                            String.format(Locale.US, "%.0f%%", snapshot.telemetry.humidityPct)
                        } else "--"
                        val rainSensorStateText = when {
                            !isOnline -> null
                            !snapshot.telemetry.rainValid -> "Err"
                            snapshot.telemetry.rainRawWet || snapshot.telemetry.rainWetConfirmed -> "Wet"
                            else -> "Dry"
                        }
                        val rainText = when {
                            !isOnline -> "--"
                            snapshot.weather.valid && snapshot.weather.rainProbabilityPct >= 0 ->
                                "${snapshot.weather.rainProbabilityPct}% · ${rainSensorStateText ?: "Dry"}"
                            else -> rainSensorStateText ?: "--"
                        }

                        Surface(
                            shape = RoundedCornerShape(15.dp),
                            color = Color.White.copy(alpha = 0.13f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HomeAmbientChip(
                                    icon = Icons.Filled.Thermostat,
                                    label = "Temp",
                                    value = tempText,
                                    iconTint = HomeColors.MintGlow,
                                    lightOnDark = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(26.dp)
                                        .background(Color.White.copy(alpha = 0.20f))
                                )
                                HomeAmbientChip(
                                    icon = Icons.Filled.WaterDrop,
                                    label = "Humidity",
                                    value = humText,
                                    iconTint = Color(0xFFBAE6FD),
                                    lightOnDark = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(26.dp)
                                        .background(Color.White.copy(alpha = 0.20f))
                                )
                                HomeAmbientChip(
                                    icon = Icons.Filled.Cloud,
                                    label = "Rain",
                                    value = rainText,
                                    iconTint = Color(0xFFBAE6FD),
                                    lightOnDark = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // ── Lower Clean White Body: 4 Zone Tiles + Action CTA ─────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (isConnected) {
                    val reservoirValid = isOnline && snapshot.telemetry.reservoirValid && snapshot.telemetry.reservoirPct != null
                    val reservoirPct = if (reservoirValid) (snapshot.telemetry.reservoirPct ?: 0f).toInt().coerceIn(0, 100) else null

                    val tomatoValid = isOnline && snapshot.telemetry.tomato.valid && snapshot.telemetry.tomato.pct != null
                    val tomatoPct = if (tomatoValid) (snapshot.telemetry.tomato.pct ?: 0f).toInt().coerceIn(0, 100) else null

                    val chiliValid = isOnline && snapshot.telemetry.chili.valid && snapshot.telemetry.chili.pct != null
                    val chiliPct = if (chiliValid) (snapshot.telemetry.chili.pct ?: 0f).toInt().coerceIn(0, 100) else null

                    val eggplantValid = isOnline && snapshot.telemetry.eggplant.valid && snapshot.telemetry.eggplant.pct != null
                    val eggplantPct = if (eggplantValid) (snapshot.telemetry.eggplant.pct ?: 0f).toInt().coerceIn(0, 100) else null

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ZoneMoisturePill(
                            label = "Tank",
                            value = if (reservoirPct != null) "$reservoirPct%" else "--",
                            progress = if (reservoirPct != null) reservoirPct / 100f else 0f,
                            barColor = HomeColors.WaterBlue,
                            isAlert = reservoirPct != null && reservoirPct < 20,
                            modifier = Modifier.weight(1f)
                        )
                        ZoneMoisturePill(
                            label = "Tomato",
                            value = if (tomatoPct != null) "$tomatoPct%" else "--",
                            progress = if (tomatoPct != null) tomatoPct / 100f else 0f,
                            barColor = HomeColors.EmeraldAccent,
                            isAlert = isOnline && (snapshot.telemetry.tomato.needsWater || (tomatoPct != null && tomatoPct < snapshot.config.tomato.startPct)),
                            modifier = Modifier.weight(1f)
                        )
                        ZoneMoisturePill(
                            label = "Chili",
                            value = if (chiliPct != null) "$chiliPct%" else "--",
                            progress = if (chiliPct != null) chiliPct / 100f else 0f,
                            barColor = HomeColors.EmeraldAccent,
                            isAlert = isOnline && (snapshot.telemetry.chili.needsWater || (chiliPct != null && chiliPct < snapshot.config.chili.startPct)),
                            modifier = Modifier.weight(1f)
                        )
                        ZoneMoisturePill(
                            label = "Eggplant",
                            value = if (eggplantPct != null) "$eggplantPct%" else "--",
                            progress = if (eggplantPct != null) eggplantPct / 100f else 0f,
                            barColor = HomeColors.EmeraldAccent,
                            isAlert = isOnline && (snapshot.telemetry.eggplant.needsWater || (eggplantPct != null && eggplantPct < snapshot.config.eggplant.startPct)),
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Text(
                        text = "Pair your SmartPlantCare box to monitor live soil moisture, water tank level, weather-aware rain guard, and automated watering.",
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        color = HomeColors.TextSecondary,
                        lineHeight = 18.sp
                    )
                }

                // Action Button: Green with crisp White text
                Button(
                    onClick = onOpenMyPlants,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HomeColors.ForestPrimary,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = if (isConnected) "Open My Plants Dashboard" else "Connect SmartPlantCare Box",
                        fontFamily = HomeInterFont,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeAmbientChip(
    icon: ImageVector,
    label: String,
    value: String,
    iconTint: Color,
    lightOnDark: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                fontFamily = HomeInterFont,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (lightOnDark) Color.White.copy(alpha = 0.78f) else HomeColors.TextSecondary,
                maxLines = 1
            )
        }
        Text(
            text = value,
            fontFamily = HomeInterFont,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                lightOnDark -> Color.White.copy(alpha = if (value == "--") 0.65f else 0.98f)
                value == "--" -> HomeColors.TextMuted
                else -> HomeColors.TextPrimary
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ZoneMoisturePill(
    label: String,
    value: String,
    progress: Float,
    barColor: Color,
    isAlert: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isAlert) HomeColors.AlertBg else HomeColors.PageBg,
        border = BorderStroke(
            width = 1.dp,
            color = if (isAlert) HomeColors.AlertRed.copy(alpha = 0.3f) else HomeColors.CardBorder
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontFamily = HomeInterFont,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    value == "--" -> HomeColors.TextMuted
                    isAlert -> HomeColors.AlertRed
                    else -> HomeColors.TextPrimary
                }
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontFamily = HomeInterFont,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = HomeColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(50)),
                color = if (isAlert) HomeColors.AlertRed else barColor,
                trackColor = HomeColors.CardBorder
            )
        }
    }
}

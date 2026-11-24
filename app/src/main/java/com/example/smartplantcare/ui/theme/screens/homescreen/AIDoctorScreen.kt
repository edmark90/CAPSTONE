package com.example.smartplantcare.ui.theme.screens.homescreen

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.smartplantcare.data.TreatmentPlan
import java.io.File
import java.util.concurrent.TimeUnit

private enum class PlanFilter(val label: String) {
    ALL("All Plants"),
    IN_CARE("In Care"),
    HEALTHY("Healthy"),
    COMPLETED("Completed")
}

@Composable
fun AIDoctorScreen(
    viewModel: TreatmentPlanViewModel,
    onPlanClick: (String) -> Unit,
    onIdentifyClick: () -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    val plans by viewModel.plans.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(PlanFilter.ALL) }
    var planToDelete by remember { mutableStateOf<String?>(null) }

    val filteredPlans = remember(searchQuery, selectedFilter, plans) {
        plans.filter { plan ->
            val matchesQuery = searchQuery.isBlank() ||
                plan.plantName.contains(searchQuery, ignoreCase = true) ||
                plan.diseaseName.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                PlanFilter.ALL -> true
                PlanFilter.IN_CARE -> !plan.isHealthy && plan.completedCount < 7
                PlanFilter.HEALTHY -> plan.isHealthy
                PlanFilter.COMPLETED -> plan.completedCount >= 7
            }
            matchesQuery && matchesFilter
        }
    }

    val healthyCount = remember(plans) { plans.count { it.isHealthy } }
    val inCareCount = remember(plans) { plans.count { !it.isHealthy && it.completedCount < 7 } }
    val completedCount = remember(plans) { plans.count { it.completedCount >= 7 } }

    LaunchedEffect(Unit) {
        if (plans.isEmpty()) {
            viewModel.loadPlans()
        }
    }
    val context = LocalContext.current

    if (planToDelete != null) {
        AlertDialog(
            onDismissRequest = { planToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = HomeColors.AlertRed,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Delete Treatment Plan",
                    fontFamily = HomeInterFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = HomeColors.TextPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this plant's treatment plan? This action cannot be undone.",
                    fontFamily = HomeInterFont,
                    fontSize = 14.sp,
                    color = HomeColors.TextSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        planToDelete?.let { id ->
                            viewModel.deletePlan(id)
                            Toast.makeText(
                                context,
                                "Treatment plan deleted",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        planToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HomeColors.AlertRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete", fontFamily = HomeInterFont, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { planToDelete = null },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HomeColors.CardBorder)
                ) {
                    Text("Cancel", fontFamily = HomeInterFont, fontWeight = FontWeight.Medium, color = HomeColors.TextSecondary)
                }
            },
            shape = RoundedCornerShape(22.dp),
            containerColor = HomeColors.SurfaceWhite
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeColors.PageBg)
            .imePadding(),
        contentPadding = PaddingValues(bottom = HomeDimens.BottomBarClearance)
    ) {
        // ─── Header & Interactive Filter Summary Cards ─────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HomeColors.SurfaceWhite)
                    .statusBarsPadding()
                    .padding(horizontal = HomeDimens.ScreenHorizontalPadding)
                    .padding(top = HomeDimens.ScreenTopPadding, bottom = 18.dp)
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
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(HomeColors.SoftMint),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MedicalServices,
                                contentDescription = null,
                                tint = HomeColors.ForestPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AI Doctor",
                                fontFamily = HomeInterFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 23.sp,
                                color = HomeColors.TextPrimary,
                                letterSpacing = (-0.3).sp
                            )
                            Text(
                                text = "Diagnosed leaves & 7-day care plans",
                                fontFamily = HomeInterFont,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = HomeColors.TextSecondary,
                                maxLines = 2
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = onIdentifyClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HomeColors.ForestPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(13.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Identify",
                            fontFamily = HomeInterFont,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatCard(
                        icon = Icons.Default.Eco,
                        value = "${plans.size}",
                        label = "Total",
                        isSelected = selectedFilter == PlanFilter.ALL,
                        valueColor = HomeColors.ForestPrimary,
                        iconBg = HomeColors.SoftMint,
                        iconTint = HomeColors.ForestPrimary,
                        onClick = { selectedFilter = PlanFilter.ALL },
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = Icons.Default.LocalHospital,
                        value = "$inCareCount",
                        label = "In Care",
                        isSelected = selectedFilter == PlanFilter.IN_CARE,
                        valueColor = HomeColors.AlertRed,
                        iconBg = HomeColors.AlertBg,
                        iconTint = HomeColors.AlertRed,
                        onClick = { selectedFilter = PlanFilter.IN_CARE },
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = Icons.Default.HealthAndSafety,
                        value = "$healthyCount",
                        label = "Healthy",
                        isSelected = selectedFilter == PlanFilter.HEALTHY,
                        valueColor = HomeColors.SuccessGreen,
                        iconBg = HomeColors.SuccessBg,
                        iconTint = HomeColors.SuccessGreen,
                        onClick = { selectedFilter = PlanFilter.HEALTHY },
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = Icons.Default.CheckCircle,
                        value = "$completedCount",
                        label = "Done",
                        isSelected = selectedFilter == PlanFilter.COMPLETED,
                        valueColor = HomeColors.WaterBlue,
                        iconBg = HomeColors.WaterBg,
                        iconTint = HomeColors.WaterBlue,
                        onClick = { selectedFilter = PlanFilter.COMPLETED },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            HorizontalDivider(color = HomeColors.DividerLine, thickness = 1.dp)
        }

        // ─── Search + Filter Chips ──────────────────────────────────────────
        item {
            Column(
                modifier = Modifier.padding(horizontal = HomeDimens.ScreenHorizontalPadding, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Saved Care Plans",
                        fontFamily = HomeInterFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = HomeColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (plans.isNotEmpty()) {
                        Text(
                            text = "${filteredPlans.size} of ${plans.size} shown",
                            fontFamily = HomeInterFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = HomeColors.TextSecondary,
                            maxLines = 1
                        )
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "Search plant or diagnosis...",
                            fontFamily = HomeInterFont,
                            color = HomeColors.TextMuted,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = HomeColors.ForestMedium,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = HomeColors.TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = HomeColors.SurfaceWhite,
                        unfocusedContainerColor = HomeColors.SurfaceWhite,
                        focusedBorderColor = HomeColors.ForestPrimary,
                        unfocusedBorderColor = HomeColors.CardBorder
                    ),
                    singleLine = true
                )

                if (plans.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PlanFilter.entries.forEach { filter ->
                            val selected = selectedFilter == filter
                            Surface(
                                onClick = { selectedFilter = filter },
                                shape = RoundedCornerShape(50),
                                color = if (selected) HomeColors.ForestPrimary else HomeColors.SurfaceWhite,
                                border = if (selected) null else BorderStroke(1.dp, HomeColors.CardBorder)
                            ) {
                                Text(
                                    text = filter.label,
                                    fontFamily = HomeInterFont,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) Color.White else HomeColors.TextSecondary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ─── Content ───────────────────────────────────────────────────────
        when {
            isLoading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = HomeColors.ForestPrimary, strokeWidth = 3.dp)
                    }
                }
            }

            plans.isEmpty() -> {
                item { EmptyPlansState(onIdentifyClick = onIdentifyClick) }
            }

            filteredPlans.isEmpty() -> {
                item {
                    Surface(
                        color = HomeColors.SurfaceWhite,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, HomeColors.CardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = HomeDimens.ScreenHorizontalPadding, vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 36.dp, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = HomeColors.TextMuted,
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No matching care plans",
                                fontFamily = HomeInterFont,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = HomeColors.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Try clearing your search or switching the filter above.",
                                fontFamily = HomeInterFont,
                                fontSize = 12.sp,
                                color = HomeColors.TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            if (selectedFilter != PlanFilter.ALL || searchQuery.isNotBlank()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                OutlinedButton(
                                    onClick = {
                                        selectedFilter = PlanFilter.ALL
                                        searchQuery = ""
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, HomeColors.CardBorder)
                                ) {
                                    Text(
                                        text = "Reset Filters",
                                        fontFamily = HomeInterFont,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = HomeColors.ForestPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                items(
                    items = filteredPlans,
                    key = { it.id }
                ) { plan ->
                    TreatmentPlanCard(
                        plan = plan,
                        onClick = { onPlanClick(plan.id) },
                        onDeleteClick = { planToDelete = plan.id }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    isSelected: Boolean,
    valueColor: Color,
    iconBg: Color,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) iconBg.copy(alpha = 0.65f) else HomeColors.PageBg
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) valueColor.copy(alpha = 0.45f) else HomeColors.CardBorder
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) HomeColors.SurfaceWhite else iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = value,
                fontFamily = HomeInterFont,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = label,
                fontFamily = HomeInterFont,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) HomeColors.TextPrimary else HomeColors.TextSecondary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyPlansState(onIdentifyClick: () -> Unit) {
    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HomeDimens.ScreenHorizontalPadding, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(HomeColors.SoftMint),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Spa,
                    contentDescription = null,
                    tint = HomeColors.ForestPrimary,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Saved Care Plans Yet",
                fontFamily = HomeInterFont,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = HomeColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Use the center camera button to scan a Tomato, Chili, or Eggplant leaf for disease detection and a 7-day care schedule, or identify a new plant species.",
                fontFamily = HomeInterFont,
                fontSize = 13.sp,
                color = HomeColors.TextSecondary,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            OutlinedButton(
                onClick = onIdentifyClick,
                shape = RoundedCornerShape(13.dp),
                border = BorderStroke(1.dp, HomeColors.ForestPrimary),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddAPhoto,
                    contentDescription = null,
                    tint = HomeColors.ForestPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Identify Plant Species",
                    fontFamily = HomeInterFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.ForestPrimary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun TreatmentPlanCard(
    plan: TreatmentPlan,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val context = LocalContext.current
    val totalDays = 7
    val progress = (plan.completedCount.toFloat() / totalDays).coerceIn(0f, 1f)
    val isHealthy = plan.isHealthy
    val isCompleted = plan.completedCount >= totalDays
    val currentDayNum = (plan.completedCount + 1).coerceAtMost(totalDays)

    val daysAgoText = remember(plan.startDateMillis) {
        val diff = System.currentTimeMillis() - plan.startDateMillis
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        when {
            days <= 0L -> "Saved today"
            days == 1L -> "Saved yesterday"
            else -> "Saved $days days ago"
        }
    }

    val imageRequest = remember(plan.imageUrl) {
        if (plan.imageUrl.isNotBlank()) {
            val data: Any = if (plan.imageUrl.startsWith("/")) File(plan.imageUrl) else plan.imageUrl
            ImageRequest.Builder(context)
                .data(data)
                .size(240)
                .crossfade(false)
                .build()
        } else null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HomeDimens.ScreenHorizontalPadding, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = HomeColors.SurfaceWhite),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(HomeColors.PlaceholderBg),
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
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = HomeColors.TextMuted,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.plantName.ifBlank { "Unknown Plant" },
                        fontFamily = HomeInterFont,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(5.dp))

                    Surface(
                        color = when {
                            isHealthy -> HomeColors.SuccessBg
                            isCompleted -> HomeColors.WaterBg
                            else -> HomeColors.AlertBg
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isHealthy -> HomeColors.SuccessGreen
                                            isCompleted -> HomeColors.WaterBlue
                                            else -> HomeColors.AlertRed
                                        }
                                    )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isHealthy) "Healthy Leaf" else plan.diseaseName,
                                fontFamily = HomeInterFont,
                                color = when {
                                    isHealthy -> HomeColors.SuccessGreen
                                    isCompleted -> HomeColors.WaterBlue
                                    else -> HomeColors.AlertRed
                                },
                                fontSize = 11.sp,
                                lineHeight = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.height(5.dp))

                    Text(
                        text = daysAgoText,
                        fontFamily = HomeInterFont,
                        fontSize = 11.sp,
                        color = HomeColors.TextMuted
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Subtle neutral delete button so it doesn't compete with the diagnosis badge
                Surface(
                    color = HomeColors.PageBg,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HomeColors.CardBorder),
                    modifier = Modifier.size(40.dp)
                ) {
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete Plan",
                            tint = HomeColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = HomeColors.DividerLine, thickness = 1.dp)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isCompleted) "7-Day Care Completed"
                    else "Day $currentDayNum of $totalDays",
                    fontFamily = HomeInterFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCompleted || isHealthy) HomeColors.SuccessGreen else HomeColors.ForestPrimary,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${plan.completedCount}/$totalDays steps done",
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HomeColors.TextSecondary,
                        maxLines = 1
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = HomeColors.ForestMedium,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isCompleted || isHealthy) HomeColors.SuccessGreen else HomeColors.ForestPrimary,
                trackColor = HomeColors.SoftMint
            )
        }
    }
}

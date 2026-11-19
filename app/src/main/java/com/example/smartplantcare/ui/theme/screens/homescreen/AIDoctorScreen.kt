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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Plagiarism
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
    ALL("All"),
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
    val inCareCount = remember(plans) { plans.size - healthyCount }

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
        // ─── Header ────────────────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HomeColors.SurfaceWhite)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp, bottom = 18.dp)
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
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
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
                        Column {
                            Text(
                                text = "AI Doctor",
                                fontFamily = HomeInterFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = HomeColors.TextPrimary
                            )
                            Text(
                                text = "Saved plants & 7-day care plans",
                                fontFamily = HomeInterFont,
                                fontSize = 12.sp,
                                color = HomeColors.TextSecondary
                            )
                        }
                    }

                    Button(
                        onClick = onIdentifyClick,
                        colors = ButtonDefaults.buttonColors(containerColor = HomeColors.ForestPrimary),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
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
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        icon = Icons.Default.Plagiarism,
                        value = "${plans.size}",
                        label = "Plants",
                        valueColor = HomeColors.ForestPrimary,
                        iconBg = HomeColors.SoftMint,
                        iconTint = HomeColors.ForestPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = Icons.Default.HealthAndSafety,
                        value = "$healthyCount",
                        label = "Healthy",
                        valueColor = HomeColors.SuccessGreen,
                        iconBg = HomeColors.SuccessBg,
                        iconTint = HomeColors.SuccessGreen,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = Icons.Default.LocalHospital,
                        value = "$inCareCount",
                        label = "In Care",
                        valueColor = HomeColors.AlertRed,
                        iconBg = HomeColors.AlertBg,
                        iconTint = HomeColors.AlertRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            HorizontalDivider(color = HomeColors.DividerLine, thickness = 1.dp)
        }

        // ─── Search + Filter Chips ──────────────────────────────────────────
        item {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Saved Plants",
                        fontFamily = HomeInterFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = HomeColors.TextPrimary
                    )
                    if (plans.isNotEmpty()) {
                        Text(
                            text = "${filteredPlans.size} result${if (filteredPlans.size != 1) "s" else ""}",
                            fontFamily = HomeInterFont,
                            fontSize = 12.sp,
                            color = HomeColors.TextMuted
                        )
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "Search plant or disease...",
                            fontFamily = HomeInterFont,
                            color = HomeColors.TextMuted,
                            fontSize = 14.sp
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
                        focusedBorderColor = HomeColors.ForestMedium,
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
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) Color.White else HomeColors.TextSecondary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
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
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = HomeColors.ForestPrimary, strokeWidth = 3.5.dp)
                    }
                }
            }

            plans.isEmpty() -> {
                item { EmptyPlansState() }
            }

            filteredPlans.isEmpty() -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = HomeColors.TextMuted,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No matching plants found",
                                fontFamily = HomeInterFont,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = HomeColors.TextSecondary
                            )
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
    valueColor: Color,
    iconBg: Color,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HomeColors.PageBg),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                fontFamily = HomeInterFont,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Text(
                text = label,
                fontFamily = HomeInterFont,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = HomeColors.TextSecondary
            )
        }
    }
}

@Composable
private fun EmptyPlansState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(HomeColors.SoftMint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Spa,
                contentDescription = null,
                tint = HomeColors.ForestMedium,
                modifier = Modifier.size(38.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No plants saved yet",
            fontFamily = HomeInterFont,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = HomeColors.TextPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Scan a leaf with the camera to diagnose issues and generate a 7-day care plan.",
            fontFamily = HomeInterFont,
            fontSize = 13.sp,
            color = HomeColors.TextSecondary,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center
        )
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

    val daysAgoText = remember(plan.startDateMillis) {
        val diff = System.currentTimeMillis() - plan.startDateMillis
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        when {
            days <= 0L -> "Saved today"
            days == 1L -> "Saved 1 day ago"
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
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
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
                        .size(74.dp)
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
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.plantName.ifBlank { "Unknown Plant" },
                        fontFamily = HomeInterFont,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(5.dp))

                    Surface(
                        color = if (isHealthy) HomeColors.SuccessBg else HomeColors.AlertBg,
                        shape = RoundedCornerShape(99.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isHealthy) HomeColors.SuccessGreen else HomeColors.AlertRed)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isHealthy) "Healthy" else plan.diseaseName,
                                fontFamily = HomeInterFont,
                                color = if (isHealthy) HomeColors.SuccessGreen else HomeColors.AlertRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.height(5.dp))

                    Text(
                        text = daysAgoText,
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        color = HomeColors.TextMuted
                    )
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(HomeColors.AlertBg)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete Plan",
                        tint = HomeColors.AlertRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TREATMENT PROGRESS",
                        fontFamily = HomeInterFont,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextMuted,
                        letterSpacing = 0.7.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (isCompleted) "Plan Completed"
                        else "Day ${(plan.completedCount + 1).coerceAtMost(totalDays)} of $totalDays · ${plan.completedCount} done",
                        fontFamily = HomeInterFont,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.ForestPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isCompleted) HomeColors.SuccessBg else HomeColors.SoftMint),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Default.HealthAndSafety,
                            contentDescription = null,
                            tint = HomeColors.SuccessGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = HomeColors.ForestMedium,
                            modifier = Modifier.size(18.dp)
                        )
                    }
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

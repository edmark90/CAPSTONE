package com.example.smartplantcare.ui.theme.screens.homescreen

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Plagiarism
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.smartplantcare.R
import com.example.smartplantcare.data.TreatmentPlan
import com.example.smartplantcare.ui.theme.ErrorRed
import com.example.smartplantcare.ui.theme.MediumGreen
import com.example.smartplantcare.ui.theme.PrimaryGreen
import com.example.smartplantcare.ui.theme.SoftGreen
import com.example.smartplantcare.ui.theme.TextDark
import com.example.smartplantcare.ui.theme.TextLight
import com.example.smartplantcare.ui.theme.TextMedium
import java.util.concurrent.TimeUnit

// ─── Design Tokens ─────────────────────────────────────────────────────────────
private val HeaderTeal    = Color(0xFF1B4332)
private val PageBg        = Color(0xFFF8FAF9)
private val CardWhite     = Color(0xFFFFFFFF)
private val CardBorder    = Color(0xFFE8EFEA)
private val SuccessGreen  = Color(0xFF10B981)
private val AlertRed      = Color(0xFFEF4444)
private val AlertBg       = Color(0xFFFEF2F2)
private val SuccessBg     = Color(0xFFECFDF5)
private val PlaceholderBg = Color(0xFFEFF2F1)
private val MutedIcon     = Color(0xFF9CA3AF)
private val HealthyLabel  = Color(0xFF059669)
private val CareLabel     = Color(0xFFDC2626)
private val TotalLabel    = Color(0xFF1B4332)

private val InterFont = FontFamily(
    Font(R.font.inter_black, FontWeight.Black),
    Font(R.font.interbold, FontWeight.Bold),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_24pt_medium, FontWeight.Medium),
    Font(R.font.inter_24pt_regular, FontWeight.Normal)
)

@OptIn(ExperimentalMaterial3Api::class)
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
    var planToDelete by remember { mutableStateOf<String?>(null) }

    val filteredPlans = remember(searchQuery, plans) {
        if (searchQuery.isBlank()) {
            plans
        } else {
            plans.filter {
                it.plantName.contains(searchQuery, ignoreCase = true) ||
                        it.diseaseName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val healthyCount = remember(plans) { plans.count { it.isHealthy } }
    val inCareCount  = remember(plans) { plans.size - healthyCount }

    LaunchedEffect(Unit) {
        if (plans.isEmpty()) {
            viewModel.loadPlans()
        }
    }
    val context = LocalContext.current

    // Delete Confirmation Dialog
    if (planToDelete != null) {
        AlertDialog(
            onDismissRequest = { planToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = ErrorRed,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Delete Treatment Plan",
                    fontFamily = InterFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextDark
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this plant's treatment plan? This action cannot be undone.",
                    fontSize = 14.sp,
                    color = TextMedium,
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
                                "Treatment plan successfully deleted",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        planToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete", fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { planToDelete = null },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Medium, color = TextMedium)
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = CardWhite
        )
    }

    Scaffold(
        containerColor = PageBg,
        modifier = Modifier.imePadding()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // ─── Header ────────────────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardWhite)
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp)
                        .padding(top = 28.dp, bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(SoftGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MedicalServices,
                                    contentDescription = null,
                                    tint = PrimaryGreen,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "AI Doctor",
                                fontFamily = InterFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                color = TextDark
                            )
                        }

                        Button(
                            onClick = onIdentifyClick,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Identify",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Manage your saved plants and track recovery progress.",
                        fontSize = 14.sp,
                        color = TextMedium,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // ─── Stats Row ─────────────────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatCard(
                            icon = Icons.Default.Plagiarism,
                            value = "${plans.size}",
                            label = "Plants",
                            valueColor = TotalLabel,
                            iconBg = SoftGreen,
                            iconTint = PrimaryGreen,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            icon = Icons.Default.HealthAndSafety,
                            value = "$healthyCount",
                            label = "Healthy",
                            valueColor = HealthyLabel,
                            iconBg = SuccessBg,
                            iconTint = SuccessGreen,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            icon = Icons.Default.LocalHospital,
                            value = "$inCareCount",
                            label = "In Care",
                            valueColor = CareLabel,
                            iconBg = AlertBg,
                            iconTint = AlertRed,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ─── Search + Title ───────────────────────────────────────────────
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Saved Plants",
                            fontFamily = InterFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextDark
                        )
                        if (plans.isNotEmpty()) {
                            Text(
                                text = "${filteredPlans.size} result${if (filteredPlans.size != 1) "s" else ""}",
                                fontSize = 12.sp,
                                color = TextLight
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Search saved plant...",
                                color = TextLight,
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MediumGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardWhite,
                            unfocusedContainerColor = CardWhite,
                            focusedBorderColor = MediumGreen,
                            unfocusedBorderColor = CardBorder,
                            focusedPlaceholderColor = TextLight,
                            unfocusedPlaceholderColor = TextLight
                        ),
                        singleLine = true
                    )
                }
            }

            // ─── Content ──────────────────────────────────────────────────────
            when {
                isLoading -> {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(400.dp),
                            Alignment.Center
                        ) {
                            CircularProgressIndicator(color = HeaderTeal, strokeWidth = 4.dp)
                        }
                    }
                }

                plans.isEmpty() -> {
                    item { EmptyPlansState(onIdentifyClick = onIdentifyClick) }
                }

                filteredPlans.isEmpty() -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MutedIcon,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No plants match your search",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextMedium
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
}

// ─── Stat Card ────────────────────────────────────────────────────────────────
@Composable
private fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    valueColor: Color,
    iconBg: Color,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontFamily = InterFont,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMedium
            )
        }
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────
@Composable
private fun EmptyPlansState(onIdentifyClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(SoftGreen),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Spa,
                contentDescription = null,
                tint = MediumGreen,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "No plants found",
            fontFamily = InterFont,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = TextDark
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Identify a plant to generate a personalized treatment plan and start tracking its care journey.",
            fontSize = 13.sp,
            color = TextMedium,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )
     
    }
}

// ─── Treatment Plan Card ──────────────────────────────────────────────────────
@Composable
private fun TreatmentPlanCard(
    plan: TreatmentPlan,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val totalDays = 7
    val progress = (plan.completedCount.toFloat() / totalDays).coerceIn(0f, 1f)
    val isHealthy = plan.isHealthy

    val daysAgoText = remember(plan.startDateMillis) {
        val diff = System.currentTimeMillis() - plan.startDateMillis
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        when {
            days <= 0L  -> "Saved today"
            days == 1L -> "Saved 1 day ago"
            else       -> "Saved $days days ago"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // ─── Top Row: Image + Info + Delete ─────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Plant Image
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(PlaceholderBg),
                    contentAlignment = Alignment.Center
                ) {
                    if (plan.imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = plan.imageUrl,
                            contentDescription = plan.plantName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            tint = MutedIcon,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                // Text Details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.plantName.ifBlank { "Unknown Plant" },
                        fontFamily = InterFont,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(6.dp))

                    // Status Badge
                    Surface(
                        color = if (isHealthy) SuccessBg else AlertBg,
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
                                    .background(if (isHealthy) SuccessGreen else AlertRed)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isHealthy) "Healthy" else plan.diseaseName,
                                color = if (isHealthy) HealthyLabel else CareLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = daysAgoText,
                        fontSize = 12.sp,
                        color = TextLight
                    )
                }

                // Delete Button
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AlertBg.copy(alpha = 0.6f))
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete Plan",
                        tint = AlertRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ─── Progress Section ───────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TREATMENT PROGRESS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextLight,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (plan.completedCount >= totalDays) "Plan Completed" else "Day ${plan.completedCount} of $totalDays",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = HeaderTeal
                    )
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (plan.completedCount >= totalDays) SuccessBg else SoftGreen),
                    contentAlignment = Alignment.Center
                ) {
                    if (plan.completedCount >= totalDays) {
                        Icon(
                            imageVector = Icons.Default.HealthAndSafety,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MediumGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = when {
                    plan.completedCount >= totalDays -> SuccessGreen
                    isHealthy                        -> SuccessGreen
                    else                             -> HeaderTeal
                },
                trackColor = Color(0xFFE2E8F0)
            )
        }
    }
}

package com.example.smartplantcare.ui.theme.screens.homescreen

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material.icons.filled.WifiLock
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.smartplantcare.iot.EspScanBlocker
import com.example.smartplantcare.iot.MyPlantsUiState
import com.example.smartplantcare.iot.NearbyEspDevice
import com.example.smartplantcare.iot.ScannedWifiNetwork

private val RingOuter = Color(0xFFEAF3EE)
private val RingMid = Color(0xFFD5E9DE)
private val RingInner = Color(0xFFBBDCCB)

// ─── PAGE 1: Nearby ESP32 Scanner & Connect Page ─────────────────────────────

@Composable
internal fun MyPlantsScanStepScreen(
    uiState: MyPlantsUiState,
    isAuthenticated: Boolean,
    isGuest: Boolean,
    hasExistingDevices: Boolean,
    onStartScanRequest: () -> Unit,
    onStopScan: () -> Unit,
    onConnectDevice: (NearbyEspDevice) -> Unit,
    onVerifyManualConnection: () -> Unit,
    onJoinInviteSubmit: (String, () -> Unit) -> Unit,
    onBackToDashboard: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    var showAuthDialog by remember { mutableStateOf(false) }
    var showJoinInviteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeColors.PageBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HomeDimens.ScreenHorizontalPadding)
            .padding(top = HomeDimens.ScreenTopPadding, bottom = HomeDimens.BottomBarClearance),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (hasExistingDevices) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = HomeColors.SurfaceWhite,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HomeColors.CardBorder),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onBackToDashboard)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Dashboard",
                            tint = HomeColors.ForestPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Add Another Plant Box",
                        fontFamily = HomeInterFont,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary
                    )
                    Text(
                        text = "Pair a new SmartPlantCare controller",
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        color = HomeColors.TextSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Sleek 2-Step Setup Progress Bar
        SetupStepProgressHeader(currentStep = 1)

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Connect SmartPlantCare\nSensor Device",
            fontFamily = HomeInterFont,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            color = HomeColors.TextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 29.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when {
                uiState.isConnectingToEsp -> "Connecting directly to your ESP32 setup hotspot..."
                uiState.isScanningEsp -> "Scanning nearby for unclaimed SPC-SETUP ESP32 boxes..."
                else -> "Power ON your ESP32 plant box nearby, then start scanning to pair it with your account."
            },
            fontFamily = HomeInterFont,
            fontSize = 13.sp,
            color = HomeColors.TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Elevated Radar Stage Card
        Surface(
            color = HomeColors.SurfaceWhite,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, HomeColors.CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RadarScanArea(
                    isScanning = uiState.isScanningEsp || uiState.isConnectingToEsp,
                    modifier = Modifier
                        .widthIn(max = 216.dp)
                        .fillMaxWidth(0.68f)
                        .aspectRatio(1f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Live Status Pill inside Radar Stage
                val mins = uiState.scanElapsedSeconds / 60
                val secs = uiState.scanElapsedSeconds % 60
                val elapsedFormatted = String.format(java.util.Locale.US, "%d:%02d / 2:00", mins, secs)
                val pillBg = when {
                    uiState.isConnectingToEsp -> HomeColors.WaterBg
                    uiState.isScanningEsp -> HomeColors.SoftMint
                    uiState.nearbyEspDevices.isNotEmpty() -> HomeColors.SuccessBg
                    else -> HomeColors.PageBg
                }
                val pillFg = when {
                    uiState.isConnectingToEsp -> HomeColors.WaterBlue
                    uiState.isScanningEsp -> HomeColors.ForestPrimary
                    uiState.nearbyEspDevices.isNotEmpty() -> HomeColors.SuccessGreen
                    else -> HomeColors.TextSecondary
                }
                Surface(
                    color = pillBg,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, pillFg.copy(alpha = 0.22f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.isScanningEsp || uiState.isConnectingToEsp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = pillFg
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(
                                imageVector = if (uiState.nearbyEspDevices.isNotEmpty()) Icons.Filled.CheckCircle else Icons.Filled.Sensors,
                                contentDescription = null,
                                tint = pillFg,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = when {
                                uiState.isConnectingToEsp -> "Connecting to ESP32 hotspot..."
                                uiState.isScanningEsp -> "Scanning nearby...  $elapsedFormatted"
                                uiState.nearbyEspDevices.isNotEmpty() -> "${uiState.nearbyEspDevices.size} ESP32 box ready to connect"
                                else -> "Ready to scan nearby 2.4 GHz boxes"
                            },
                            fontFamily = HomeInterFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = pillFg
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Detected Nearby ESP32 List (`SPC-SETUP-*` only)
        AnimatedVisibility(visible = uiState.nearbyEspDevices.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Detected Unclaimed ESP32 Devices (${uiState.nearbyEspDevices.size})",
                    fontFamily = HomeInterFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary
                )
                uiState.nearbyEspDevices.forEach { device ->
                    NearbyEspCard(
                        device = device,
                        isConnecting = uiState.isConnectingToEsp,
                        onConnect = { onConnectDevice(device) }
                    )
                }
            }
        }

        // 2-Minute Timeout Card ("No active ESP32 nearby")
        AnimatedVisibility(visible = uiState.scanTimedOutNoDevice && !uiState.isScanningEsp) {
            Surface(
                color = HomeColors.SurfaceWhite,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, HomeColors.WarningAmber.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(HomeColors.WarningBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WifiOff,
                            contentDescription = null,
                            tint = HomeColors.WarningAmber,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No active ESP32 nearby",
                        fontFamily = HomeInterFont,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "No unclaimed SmartPlantCare ESP32 was detected within 2 minutes. Make sure your ESP32 is powered ON and close to your phone, then try again.",
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        color = HomeColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, HomeColors.CardBorder)
                        ) {
                            Text(
                                text = "Wi-Fi Settings",
                                fontFamily = HomeInterFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HomeColors.TextPrimary
                            )
                        }
                        Button(
                            onClick = onVerifyManualConnection,
                            enabled = !uiState.isConnectingToEsp,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HomeColors.SoftMint,
                                contentColor = HomeColors.ForestPrimary
                            )
                        ) {
                            Text(
                                text = "I'm Connected",
                                fontFamily = HomeInterFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // General Connection Status / Hint Banner (when not showing the 2-min timeout card)
        AnimatedVisibility(visible = !uiState.scanStatusMessage.isNullOrBlank() && !uiState.scanTimedOutNoDevice) {
            Surface(
                color = HomeColors.SurfaceWhite,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, HomeColors.CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Filled.WifiFind,
                            contentDescription = null,
                            tint = HomeColors.ForestMedium,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.scanStatusMessage.orEmpty(),
                            fontFamily = HomeInterFont,
                            fontSize = 12.sp,
                            color = HomeColors.TextSecondary,
                            lineHeight = 17.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (uiState.scanBlocker == EspScanBlocker.LOCATION_SERVICE_DISABLED ||
                        uiState.scanBlocker == EspScanBlocker.LOCATION_PERMISSION_MISSING
                    ) {
                        Button(
                            onClick = {
                                runCatching {
                                    val intent = if (uiState.scanBlocker == EspScanBlocker.LOCATION_SERVICE_DISABLED) {
                                        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                    } else {
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null)
                                        )
                                    }
                                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HomeColors.ForestPrimary,
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = if (uiState.scanBlocker == EspScanBlocker.LOCATION_SERVICE_DISABLED) {
                                    "Turn On Location (GPS)"
                                } else {
                                    "Open App Permissions"
                                },
                                fontFamily = HomeInterFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, HomeColors.CardBorder)
                        ) {
                            Text(
                                text = "Wi-Fi Settings",
                                fontFamily = HomeInterFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HomeColors.TextPrimary
                            )
                        }
                        Button(
                            onClick = onVerifyManualConnection,
                            enabled = !uiState.isConnectingToEsp,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HomeColors.SoftMint,
                                contentColor = HomeColors.ForestPrimary
                            )
                        ) {
                            Text(
                                text = "I'm Connected",
                                fontFamily = HomeInterFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Primary Scan / Stop / Try Again Button
        if (uiState.isScanningEsp) {
            OutlinedButton(
                onClick = onStopScan,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.5.dp, HomeColors.ForestPrimary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HomeColors.ForestPrimary)
            ) {
                Text(
                    text = "Stop Scanning",
                    fontFamily = HomeInterFont,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Button(
                onClick = {
                    if (isAuthenticated && !isGuest) {
                        onStartScanRequest()
                    } else {
                        showAuthDialog = true
                    }
                },
                enabled = !uiState.isConnectingToEsp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HomeColors.ForestPrimary,
                    contentColor = Color.White
                )
            ) {
                if (uiState.isConnectingToEsp) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Connecting to ESP32...",
                        fontFamily = HomeInterFont,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                } else {
                    Text(
                        text = when {
                            uiState.scanTimedOutNoDevice -> "Try Again"
                            uiState.nearbyEspDevices.isNotEmpty() -> "Scan Again"
                            else -> "Start Scanning"
                        },
                        fontFamily = HomeInterFont,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Join Shared Device Card (for family/operators/viewers)
        Surface(
            color = HomeColors.SurfaceWhite,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, HomeColors.CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable {
                    if (isAuthenticated && !isGuest) {
                        showJoinInviteDialog = true
                    } else {
                        showAuthDialog = true
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(HomeColors.SoftMint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.GroupAdd,
                        contentDescription = null,
                        tint = HomeColors.ForestPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Join Shared Plant Box",
                        fontFamily = HomeInterFont,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary
                    )
                    Text(
                        text = "Have a 24-hour invite code from an owner?",
                        fontFamily = HomeInterFont,
                        fontSize = 11.sp,
                        color = HomeColors.TextSecondary
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = HomeColors.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showAuthDialog) {
        AuthRequiredDialog(
            onCreateAccount = {
                showAuthDialog = false
                onNavigateToLogin()
            },
            onCancel = { showAuthDialog = false }
        )
    }

    if (showJoinInviteDialog) {
        JoinSharedDeviceDialog(
            isSubmitting = uiState.isSubmittingAction,
            onDismiss = { showJoinInviteDialog = false },
            onSubmit = { code ->
                onJoinInviteSubmit(code) { showJoinInviteDialog = false }
            }
        )
    }
}

@Composable
private fun NearbyEspCard(
    device: NearbyEspDevice,
    isConnecting: Boolean,
    onConnect: () -> Unit
) {
    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, HomeColors.ForestPrimary.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(HomeColors.SoftMint),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Memory,
                    contentDescription = null,
                    tint = HomeColors.ForestPrimary,
                    modifier = Modifier.size(23.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SmartPlantCare Box",
                    fontFamily = HomeInterFont,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SignalStrengthBars(bars = device.signalBars)
                    Text(
                        text = "${device.deviceId} • ${device.rssi} dBm",
                        fontFamily = HomeInterFont,
                        fontSize = 11.sp,
                        color = HomeColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = HomeColors.SuccessBg,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "UNCLAIMED • READY",
                        fontFamily = HomeInterFont,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.SuccessGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = onConnect,
                enabled = !isConnecting,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.height(42.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HomeColors.ForestPrimary,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Connect",
                    fontFamily = HomeInterFont,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun SetupStepProgressHeader(currentStep: Int) {
    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SetupStepPill(
                stepNumber = 1,
                label = "Find ESP32",
                isActive = currentStep == 1,
                isCompleted = currentStep > 1
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (currentStep > 1) HomeColors.ForestPrimary else HomeColors.CardBorder)
            )
            SetupStepPill(
                stepNumber = 2,
                label = "Home Wi-Fi",
                isActive = currentStep == 2,
                isCompleted = false
            )
        }
    }
}

@Composable
private fun SetupStepPill(
    stepNumber: Int,
    label: String,
    isActive: Boolean,
    isCompleted: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isActive || isCompleted -> HomeColors.ForestPrimary
                        else -> HomeColors.PlaceholderBg
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Text(
                    text = stepNumber.toString(),
                    fontFamily = HomeInterFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) Color.White else HomeColors.TextMuted
                )
            }
        }
        Text(
            text = label,
            fontFamily = HomeInterFont,
            fontSize = 12.sp,
            fontWeight = if (isActive || isCompleted) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive || isCompleted) HomeColors.ForestPrimary else HomeColors.TextMuted
        )
    }
}

@Composable
private fun SignalStrengthBars(bars: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(12.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 1..4) {
            val active = i <= bars.coerceIn(1, 4)
            val barColor = when {
                !active -> HomeColors.CardBorder
                bars >= 3 -> HomeColors.SuccessGreen
                bars == 2 -> HomeColors.WarningAmber
                else -> HomeColors.AlertRed
            }
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((3 + i * 2).dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(barColor)
            )
        }
    }
}

// ─── PAGE 2: 2.4 GHz Wi-Fi Picker, Password, Custom Box Name & Claim Code ────

@Composable
internal fun MyPlantsWifiSetupScreen(
    uiState: MyPlantsUiState,
    onBack: () -> Unit,
    onRefreshWifi: () -> Unit,
    onSelectWifi: (String) -> Unit,
    onToggleManualWifi: (Boolean) -> Unit,
    onManualWifiChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onBoxNameChange: (String) -> Unit,
    onClaimCodeChange: (String) -> Unit,
    onSubmitProvisioning: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val espInfo = uiState.connectedEspInfo

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
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = HomeColors.SurfaceWhite,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, HomeColors.CardBorder),
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !uiState.isProvisioning, onClick = onBack)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = HomeColors.ForestPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Configure ESP32 Connection",
                    fontFamily = HomeInterFont,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary
                )
                Text(
                    text = "Connect your box to home 2.4 GHz Wi-Fi",
                    fontFamily = HomeInterFont,
                    fontSize = 12.sp,
                    color = HomeColors.TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        SetupStepProgressHeader(currentStep = 2)

        Spacer(modifier = Modifier.height(14.dp))

        // Connected ESP32 Verification Banner
        Surface(
            color = HomeColors.SuccessBg,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, HomeColors.SuccessGreen.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = HomeColors.SuccessGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Connected to ${espInfo?.deviceId ?: "spc-unit-0001"}",
                        fontFamily = HomeInterFont,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.ForestPrimary
                    )
                    Text(
                        text = "SmartPlantCare Box connected • Ready for 2.4 GHz Wi-Fi",
                        fontFamily = HomeInterFont,
                        fontSize = 11.sp,
                        color = HomeColors.TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Custom Plant Box Name Card (User-requested editable box name!)
        Surface(
            color = HomeColors.SurfaceWhite,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, HomeColors.CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        tint = HomeColors.ForestPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Name Your Plant Box",
                        fontFamily = HomeInterFont,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Change the default SPC name to any custom name (shown on your app & 20x4 LCD).",
                    fontFamily = HomeInterFont,
                    fontSize = 12.sp,
                    color = HomeColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = uiState.customBoxName,
                    onValueChange = onBoxNameChange,
                    label = { Text("Plant Box Name", fontFamily = HomeInterFont) },
                    placeholder = { Text("e.g. Backyard Vegetable Bed", fontFamily = HomeInterFont) },
                    singleLine = true,
                    enabled = !uiState.isProvisioning,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HomeColors.ForestPrimary,
                        unfocusedBorderColor = HomeColors.CardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Clickable 2.4 GHz Wi-Fi Network Picker Card
        Surface(
            color = HomeColors.SurfaceWhite,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, HomeColors.CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Select 2.4 GHz Home Wi-Fi",
                            fontFamily = HomeInterFont,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.TextPrimary
                        )
                        Text(
                            text = "Tap your home Wi-Fi name below, then enter its password.",
                            fontFamily = HomeInterFont,
                            fontSize = 11.sp,
                            color = HomeColors.TextSecondary
                        )
                    }
                    IconButton(
                        onClick = onRefreshWifi,
                        enabled = !uiState.isScanningWifi && !uiState.isProvisioning
                    ) {
                        if (uiState.isScanningWifi) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = HomeColors.ForestPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Rescan 2.4 GHz Wi-Fi",
                                tint = HomeColors.ForestPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    color = HomeColors.WarningBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = HomeColors.WarningAmber,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ESP32 supports 2.4 GHz Wi-Fi only (5 GHz networks are hidden).",
                            fontFamily = HomeInterFont,
                            fontSize = 11.sp,
                            color = HomeColors.WarningAmber,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (uiState.isScanningWifi && uiState.scannedWifiNetworks.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = HomeColors.ForestPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Scanning nearby 2.4 GHz Wi-Fi networks...",
                            fontFamily = HomeInterFont,
                            fontSize = 12.sp,
                            color = HomeColors.TextSecondary
                        )
                    }
                } else if (!uiState.manualWifiMode && uiState.scannedWifiNetworks.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.scannedWifiNetworks.take(8).forEach { net ->
                            WifiNetworkRow(
                                network = net,
                                isSelected = uiState.selectedWifiSsid == net.ssid,
                                enabled = !uiState.isProvisioning,
                                onClick = { onSelectWifi(net.ssid) }
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = uiState.selectedWifiSsid,
                        onValueChange = onManualWifiChange,
                        label = { Text("2.4 GHz Wi-Fi Name (SSID)", fontFamily = HomeInterFont) },
                        placeholder = { Text("Type exact Wi-Fi SSID", fontFamily = HomeInterFont) },
                        singleLine = true,
                        enabled = !uiState.isProvisioning,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HomeColors.ForestPrimary,
                            unfocusedBorderColor = HomeColors.CardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                TextButton(
                    onClick = { onToggleManualWifi(!uiState.manualWifiMode) },
                    enabled = !uiState.isProvisioning
                ) {
                    Text(
                        text = if (uiState.manualWifiMode && uiState.scannedWifiNetworks.isNotEmpty()) {
                            "Choose from scanned 2.4 GHz Wi-Fi list"
                        } else {
                            "Wi-Fi not listed? Enter Wi-Fi name manually"
                        },
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HomeColors.ForestMedium
                    )
                }

                // Selected Wi-Fi Password + Physical Claim Code Inputs
                val selectedNet = uiState.scannedWifiNetworks.firstOrNull { it.ssid == uiState.selectedWifiSsid }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = uiState.wifiPassword,
                    onValueChange = onPasswordChange,
                    label = {
                        Text(
                            text = when {
                                selectedNet != null && !selectedNet.secure ->
                                    "Password for ${uiState.selectedWifiSsid} (Optional - Open)"
                                uiState.selectedWifiSsid.isNotBlank() ->
                                    "Password for ${uiState.selectedWifiSsid}"
                                else -> "Wi-Fi Password"
                            },
                            fontFamily = HomeInterFont
                        )
                    },
                    supportingText = {
                        Text(
                            text = "Case-sensitive • Tap the eye icon to verify exact spelling",
                            fontFamily = HomeInterFont,
                            fontSize = 11.sp,
                            color = HomeColors.TextSecondary
                        )
                    },
                    singleLine = true,
                    enabled = !uiState.isProvisioning,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "Toggle password",
                                tint = HomeColors.TextSecondary
                            )
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HomeColors.ForestPrimary,
                        unfocusedBorderColor = HomeColors.CardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = uiState.claimCode,
                    onValueChange = onClaimCodeChange,
                    label = { Text("Physical Claim Code (On ESP32 Label)", fontFamily = HomeInterFont) },
                    placeholder = { Text("SPC-CLAIM-0001", fontFamily = HomeInterFont) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = null,
                            tint = HomeColors.ForestMedium,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    enabled = !uiState.isProvisioning,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HomeColors.ForestPrimary,
                        unfocusedBorderColor = HomeColors.CardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Inline Provisioning Error Banner
        AnimatedVisibility(visible = !uiState.provisioningError.isNullOrBlank()) {
            Surface(
                color = HomeColors.AlertBg,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, HomeColors.AlertRed.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = HomeColors.AlertRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = uiState.provisioningError.orEmpty(),
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        color = HomeColors.AlertRed,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        // Provisioning Progress Status
        AnimatedVisibility(visible = uiState.isProvisioning && !uiState.provisioningProgressMessage.isNullOrBlank()) {
            Surface(
                color = HomeColors.SoftMint,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = HomeColors.ForestPrimary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = uiState.provisioningProgressMessage.orEmpty(),
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HomeColors.ForestPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = onSubmitProvisioning,
            enabled = !uiState.isProvisioning && uiState.selectedWifiSsid.isNotBlank() && uiState.claimCode.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = HomeColors.ForestPrimary,
                contentColor = Color.White
            )
        ) {
            Text(
                text = if (uiState.isProvisioning) "Setting Up ESP32..." else "Save Wi-Fi & Finish Setup",
                fontFamily = HomeInterFont,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun WifiNetworkRow(
    network: ScannedWifiNetwork,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) HomeColors.SoftMint else HomeColors.PageBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) HomeColors.ForestPrimary else HomeColors.CardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (network.secure) Icons.Filled.WifiLock else Icons.Filled.Wifi,
                contentDescription = null,
                tint = if (isSelected) HomeColors.ForestPrimary else HomeColors.ForestMedium,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = network.ssid,
                    fontFamily = HomeInterFont,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = HomeColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SignalStrengthBars(bars = network.signalBars)
                    Text(
                        text = "2.4 GHz • ${network.rssi} dBm",
                        fontFamily = HomeInterFont,
                        fontSize = 11.sp,
                        color = HomeColors.TextSecondary
                    )
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = HomeColors.ForestPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ─── Dialogs & Shared Radar Visuals ──────────────────────────────────────────

@Composable
private fun JoinSharedDeviceDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = HomeColors.SurfaceWhite
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = "Join Shared Plant Box",
                    fontFamily = HomeInterFont,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Enter the 24-hour invite code shared by the device owner (e.g. A8F392C1).",
                    fontFamily = HomeInterFont,
                    fontSize = 12.sp,
                    color = HomeColors.TextSecondary,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Share Invite Code", fontFamily = HomeInterFont) },
                    singleLine = true,
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HomeColors.ForestPrimary,
                        unfocusedBorderColor = HomeColors.CardBorder,
                        focusedLabelColor = HomeColors.ForestPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, HomeColors.CardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Cancel", fontFamily = HomeInterFont, fontWeight = FontWeight.SemiBold, color = HomeColors.TextSecondary)
                    }
                    Button(
                        onClick = { onSubmit(code) },
                        enabled = !isSubmitting && code.trim().length >= 6,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HomeColors.ForestPrimary,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = if (isSubmitting) "Joining..." else "Join Box",
                            fontFamily = HomeInterFont,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RadarScanArea(
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = if (isScanning) rememberInfiniteTransition(label = "radar_scan") else null
    val pulse: Float = if (infiniteTransition != null) {
        val anim by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulse"
        )
        anim
    } else {
        0f
    }

    val sweepAngle: Float = if (infiniteTransition != null) {
        val rot by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "sweep"
        )
        rot
    } else {
        0f
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxRadius = min(size.width, size.height) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            drawCircle(color = RingOuter, radius = maxRadius, center = center)
            drawCircle(color = RingMid, radius = maxRadius * 0.72f, center = center)
            drawCircle(color = RingInner, radius = maxRadius * 0.48f, center = center)

            val lineColor = Color.White.copy(alpha = 0.65f)
            drawLine(lineColor, Offset(center.x - maxRadius, center.y), Offset(center.x + maxRadius, center.y), 1.dp.toPx())
            drawLine(lineColor, Offset(center.x, center.y - maxRadius), Offset(center.x, center.y + maxRadius), 1.dp.toPx())

            if (isScanning) {
                // 360° rotating radar sweep beam + glowing leading ray
                val arcTopLeft = Offset(center.x - maxRadius, center.y - maxRadius)
                val arcSize = Size(maxRadius * 2f, maxRadius * 2f)
                drawArc(
                    color = HomeColors.EmeraldAccent.copy(alpha = 0.16f),
                    startAngle = sweepAngle - 58f,
                    sweepAngle = 58f,
                    useCenter = true,
                    topLeft = arcTopLeft,
                    size = arcSize
                )
                drawArc(
                    color = HomeColors.ForestPrimary.copy(alpha = 0.22f),
                    startAngle = sweepAngle - 22f,
                    sweepAngle = 22f,
                    useCenter = true,
                    topLeft = arcTopLeft,
                    size = arcSize
                )
                val rad = sweepAngle * (PI / 180.0)
                val endX = center.x + (maxRadius * cos(rad)).toFloat()
                val endY = center.y + (maxRadius * sin(rad)).toFloat()
                drawLine(
                    color = HomeColors.ForestPrimary.copy(alpha = 0.75f),
                    start = center,
                    end = Offset(endX, endY),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Expanding concentric signal pulse waves
                for (i in 0..2) {
                    val phase = (pulse + i / 3f) % 1f
                    val radius = maxRadius * 0.26f + phase * maxRadius * 0.74f
                    val alpha = (1f - phase).coerceIn(0f, 1f) * 0.55f
                    drawCircle(
                        color = HomeColors.ForestPrimary.copy(alpha = alpha),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            val dotColor = HomeColors.ForestPrimary.copy(alpha = if (isScanning) 0.9f else 0.4f)
            val dotRadius = 4.dp.toPx()
            drawCircle(dotColor, radius = dotRadius, center = Offset(center.x - maxRadius * 0.62f, center.y - maxRadius * 0.42f))
            drawCircle(dotColor, radius = dotRadius, center = Offset(center.x + maxRadius * 0.72f, center.y - maxRadius * 0.05f))
            drawCircle(dotColor, radius = dotRadius, center = Offset(center.x - maxRadius * 0.5f, center.y + maxRadius * 0.58f))
            drawCircle(dotColor, radius = dotRadius, center = Offset(center.x - maxRadius * 0.08f, center.y + maxRadius * 0.72f))
        }

        Box(
            modifier = Modifier
                .fillMaxSize(0.34f)
                .clip(CircleShape)
                .background(HomeColors.ForestPrimary),
            contentAlignment = Alignment.Center
        ) {
            LeafIcon(modifier = Modifier.fillMaxSize(0.52f))
        }
    }
}

@Composable
private fun LeafIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val center = Offset(w * 0.5f, h * 0.48f)
            val u = (min(w, h) * 0.5f) / 230f
            val leafColor = Color.White.copy(alpha = 0.95f)
            val veinColor = HomeColors.ForestMedium

            val leaf = Path().apply {
                moveTo(0f, -230f * u)
                cubicTo(110f * u, -190f * u, 165f * u, -90f * u, 150f * u, 10f * u)
                cubicTo(138f * u, 95f * u, 70f * u, 150f * u, 0f, 160f * u)
                cubicTo(-70f * u, 150f * u, -138f * u, 95f * u, -150f * u, 10f * u)
                cubicTo(-165f * u, -90f * u, -110f * u, -190f * u, 0f, -230f * u)
                close()
            }
            val mainVein = Path().apply {
                moveTo(0f, -210f * u)
                cubicTo(8f * u, -120f * u, 8f * u, -20f * u, 0f, 140f * u)
            }
            val veinLeftTop = Path().apply {
                moveTo(0f, -100f * u)
                cubicTo(-35f * u, -80f * u, -55f * u, -60f * u, -70f * u, -30f * u)
            }
            val veinRightTop = Path().apply {
                moveTo(0f, -100f * u)
                cubicTo(35f * u, -80f * u, 55f * u, -60f * u, 70f * u, -30f * u)
            }
            val stem = Path().apply {
                moveTo(0f, 155f * u)
                cubicTo(20f * u, 200f * u, -15f * u, 225f * u, 8f * u, 270f * u)
            }

            val veinStrokeThin = Stroke(width = 3f * u, cap = StrokeCap.Round)
            val veinStrokeMain = Stroke(width = 5f * u, cap = StrokeCap.Round)
            val stemStroke = Stroke(width = 7f * u, cap = StrokeCap.Round)

            onDrawBehind {
                withTransform({ translate(center.x, center.y) }) {
                    drawPath(leaf, color = leafColor)
                    drawPath(mainVein, color = veinColor, style = veinStrokeMain)
                    drawPath(veinLeftTop, color = veinColor, style = veinStrokeThin)
                    drawPath(veinRightTop, color = veinColor, style = veinStrokeThin)
                    drawPath(stem, color = leafColor, style = stemStroke)
                }
            }
        }
    )
}

@Composable
internal fun AuthRequiredDialog(
    onCreateAccount: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = HomeColors.SurfaceWhite
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(HomeColors.SoftMint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = HomeColors.ForestPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Sign In Required",
                    fontFamily = HomeInterFont,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Pairing or joining an ESP32 SmartPlantCare box requires a registered account so your ownership and sharing permissions are protected.",
                    fontFamily = HomeInterFont,
                    fontSize = 13.sp,
                    color = HomeColors.TextSecondary,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(22.dp))
                Button(
                    onClick = onCreateAccount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HomeColors.ForestPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Sign In / Create Account",
                        fontFamily = HomeInterFont,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Cancel",
                        fontFamily = HomeInterFont,
                        fontSize = 14.sp,
                        color = HomeColors.TextSecondary
                    )
                }
            }
        }
    }
}

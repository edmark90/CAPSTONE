package com.example.smartplantcare.ui.theme.screens.homescreen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.smartplantcare.R
import com.example.smartplantcare.util.NetworkMonitor

// ─── Unified Design Tokens (from HomeUiTokens) ───────────────────────────────
private val PageBg        = HomeColors.PageBg
private val SurfaceWhite  = HomeColors.SurfaceWhite
private val CardBorder    = HomeColors.CardBorder
private val MutedIconBg   = HomeColors.PlaceholderBg
private val DividerLine   = HomeColors.DividerLine
private val InterFont     = HomeInterFont

private val PrimaryGreen  = HomeColors.ForestPrimary
private val SoftGreen     = HomeColors.SoftMint
private val TextDark      = HomeColors.TextPrimary
private val TextMedium    = HomeColors.TextSecondary
private val TextLight     = HomeColors.TextMuted
private val ErrorRed      = HomeColors.AlertRed

// ─── SettingsScreen ──────────────────────────────────────────────────────────
@Composable
fun SettingsScreen(
    userName: String,
    userEmail: String,
    onSignOut: () -> Unit,
    onNavigateToLogin: (() -> Unit)? = null,
    onNavigateToSignUp: (() -> Unit)? = null,
    settingsViewModel: SettingsViewModel? = null,
    myPlantsUiState: com.example.smartplantcare.iot.MyPlantsUiState? = null,
    onOpenMyPlants: () -> Unit = {}
) {
    val context = LocalContext.current
    val vm = settingsViewModel ?: viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                    return SettingsViewModel(context) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    )

    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            vm.clearSnackbar()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* proceed */ }

    var showSignOutDialog by remember { mutableStateOf(false) }

    // ─── Dialogs ─────────────────────────────────────────────────────────────

    if (showSignOutDialog) {
        PlantDialog(
            onDismissRequest = { showSignOutDialog = false },
            icon = Icons.AutoMirrored.Filled.ExitToApp,
            iconTint = ErrorRed,
            iconBackground = HomeColors.AlertBg,
            title = stringResource(R.string.sign_out_title),
            body = stringResource(R.string.sign_out_message),
            confirmText = stringResource(R.string.confirm),
            confirmColor = ErrorRed,
            onConfirm = {
                showSignOutDialog = false
                vm.signOut()
                onSignOut()
            },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { showSignOutDialog = false }
        )
    }

    if (state.showLanguageDialog) {
        Dialog(onDismissRequest = { vm.dismissLanguageDialog() }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = SurfaceWhite,
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SoftGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Language,
                            contentDescription = null,
                            tint = PrimaryGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.select_language),
                        fontFamily = InterFont,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Choose your preferred display language",
                        fontFamily = InterFont,
                        fontSize = 13.sp,
                        color = TextMedium,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    LanguageOption(
                        label = stringResource(R.string.english),
                        isSelected = state.language == "en",
                        onClick = { vm.setLanguage("en") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LanguageOption(
                        label = stringResource(R.string.filipino),
                        isSelected = state.language == "fil",
                        onClick = { vm.setLanguage("fil") }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = { vm.dismissLanguageDialog() },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            fontFamily = InterFont,
                            fontWeight = FontWeight.SemiBold,
                            color = TextMedium
                        )
                    }
                }
            }
        }
    }

    if (state.showEditNameDialog) {
        Dialog(onDismissRequest = { vm.dismissEditNameDialog() }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = SurfaceWhite,
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SoftGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            tint = PrimaryGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.edit_name_title),
                        fontFamily = InterFont,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = state.editNameText,
                        onValueChange = vm::updateEditNameText,
                        label = { Text(stringResource(R.string.edit_name_label), fontFamily = InterFont) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryGreen,
                            unfocusedBorderColor = CardBorder,
                            focusedLabelColor = PrimaryGreen,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = PageBg
                        )
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { vm.dismissEditNameDialog() },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(13.dp),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Text(
                                text = stringResource(R.string.cancel),
                                fontFamily = InterFont,
                                fontWeight = FontWeight.SemiBold,
                                color = TextMedium
                            )
                        }
                        Button(
                            onClick = { vm.saveProfileName() },
                            enabled = !state.isLoading,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(13.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryGreen,
                                contentColor = Color.White
                            )
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.save),
                                    fontFamily = InterFont,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.showChangePasswordDialog) {
        Dialog(
            onDismissRequest = { vm.dismissChangePasswordDialog() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(24.dp),
                color = SurfaceWhite,
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SoftGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = PrimaryGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.change_password_title),
                        fontFamily = InterFont,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.password_requirements),
                        fontFamily = InterFont,
                        fontSize = 12.sp,
                        color = TextMedium,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PasswordField(
                            value = state.currentPasswordText,
                            onValueChange = vm::updateCurrentPassword,
                            label = stringResource(R.string.current_password)
                        )
                        PasswordField(
                            value = state.newPasswordText,
                            onValueChange = vm::updateNewPassword,
                            label = stringResource(R.string.new_password)
                        )
                        PasswordField(
                            value = state.confirmPasswordText,
                            onValueChange = vm::updateConfirmPassword,
                            label = stringResource(R.string.confirm_password)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { vm.dismissChangePasswordDialog() },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(13.dp),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Text(
                                text = stringResource(R.string.cancel),
                                fontFamily = InterFont,
                                fontWeight = FontWeight.SemiBold,
                                color = TextMedium
                            )
                        }
                        Button(
                            onClick = { vm.changePassword() },
                            enabled = !state.isLoading,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(13.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryGreen,
                                contentColor = Color.White
                            )
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.update_password),
                                    fontFamily = InterFont,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.showMorningTimePicker) {
        TimePickerDialog(
            title = stringResource(R.string.morning),
            initialHour = state.morningHour,
            initialMinute = state.morningMinute,
            onConfirm = { hour, minute -> vm.setMorningTime(hour, minute) },
            onDismiss = { vm.dismissTimePicker() },
            isMorning = true
        )
    }

    if (state.showEveningTimePicker) {
        TimePickerDialog(
            title = stringResource(R.string.evening),
            initialHour = state.eveningHour,
            initialMinute = state.eveningMinute,
            onConfirm = { hour, minute -> vm.setEveningTime(hour, minute) },
            onDismiss = { vm.dismissTimePicker() },
            isMorning = false
        )
    }

    if (state.showHardwareGuideDialog) {
        HardwareGuideDialog(onDismiss = { vm.setShowHardwareGuideDialog(false) })
    }

    // ─── Main Layout ─────────────────────────────────────────────────────────
    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 96.dp)
            )
        },
        containerColor = PageBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(bottom = HomeDimens.BottomBarClearance),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderSection()

            Column(
                modifier = Modifier.padding(horizontal = HomeDimens.ScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                ProfileSection(
                    state = state,
                    onSignInClick = { onNavigateToLogin?.invoke() },
                    onSignUpClick = { onNavigateToSignUp?.invoke() },
                    onEditNameClick = { vm.showEditNameDialog() },
                    onChangePasswordClick = { vm.showChangePasswordDialog() },
                    onSignOutClick = { showSignOutDialog = true }
                )

                SmartPlantCareBoxSection(
                    uiState = myPlantsUiState,
                    onOpenMyPlants = onOpenMyPlants
                )

                UnitsAndPreferencesSection(
                    tempUnit = state.temperatureUnit,
                    moistureUnit = state.measurementUnit,
                    onTempUnitChange = vm::setTemperatureUnit,
                    onMoistureUnitChange = vm::setMeasurementUnit,
                    onOpenGuide = { vm.setShowHardwareGuideDialog(true) }
                )

                NotificationSection(
                    morningReminder = state.morningReminder,
                    eveningReminder = state.eveningReminder,
                    morningHour = state.morningHour,
                    morningMinute = state.morningMinute,
                    eveningHour = state.eveningHour,
                    eveningMinute = state.eveningMinute,
                    vibrationEnabled = state.vibrationEnabled,
                    onMorningReminderChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!granted) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        vm.setMorningReminder(enabled)
                    },
                    onEveningReminderChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!granted) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        vm.setEveningReminder(enabled)
                    },
                    onMorningTimeClick = { vm.showMorningTimePicker() },
                    onEveningTimeClick = { vm.showEveningTimePicker() },
                    onVibrationChange = { vm.setVibration(it) }
                )

                LanguageAndStorageSection(
                    currentLanguage = if (state.language == "fil")
                        stringResource(R.string.filipino)
                    else
                        stringResource(R.string.english),
                    onLanguageClick = { vm.showLanguageDialog() },
                    cacheSize = state.cacheSizeFormatted,
                    onClearCache = { vm.clearAppCache() }
                )

                AboutSection(context = context)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.smartplantcare_text),
                        fontFamily = InterFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMedium
                    )
                    Text(
                        text = stringResource(R.string.version_text),
                        fontFamily = InterFont,
                        fontSize = 11.sp,
                        color = TextLight
                    )
                }
            }
        }
    }
}

// ─── Header Banner ───────────────────────────────────────────────────────────
@Composable
fun HeaderSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceWhite)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HomeDimens.ScreenHorizontalPadding)
                .padding(top = HomeDimens.ScreenTopPadding, bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontFamily = InterFont,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Manage your account, plant box & app preferences",
                        fontFamily = InterFont,
                        fontSize = 12.sp,
                        color = HomeColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = HomeColors.PageBg,
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(6.dp)) {
                        Image(
                            painter = painterResource(id = R.drawable.appslogo2),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = DividerLine, thickness = 1.dp)
    }
}

// ─── Profile Section ─────────────────────────────────────────────────────────
@Composable
fun ProfileSection(
    state: SettingsState,
    onSignInClick: () -> Unit = {},
    onSignUpClick: () -> Unit = {},
    onEditNameClick: () -> Unit = {},
    onChangePasswordClick: () -> Unit = {},
    onSignOutClick: () -> Unit = {}
) {
    Column {
        SectionTitle(stringResource(R.string.account))
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard {
            if (state.isGuest || (!state.isGoogleAccount && !state.isEmailAccount && !state.isGuest)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(SoftGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = stringResource(R.string.guest_user),
                            modifier = Modifier.size(28.dp),
                            tint = PrimaryGreen
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.guest_user),
                            fontFamily = InterFont,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Text(
                            text = "Sign in to sync your plant box & care plans",
                            fontFamily = InterFont,
                            fontSize = 12.sp,
                            color = TextMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val isSmallScreen = LocalConfiguration.current.screenWidthDp < 360
                        if (isSmallScreen) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Button(
                                    onClick = onSignInClick,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = HomeColors.ForestPrimary,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.sign_in),
                                        fontFamily = InterFont,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                }
                                OutlinedButton(
                                    onClick = onSignUpClick,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.5.dp, HomeColors.ForestPrimary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.sign_up),
                                        fontFamily = InterFont,
                                        fontWeight = FontWeight.Bold,
                                        color = HomeColors.ForestPrimary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = onSignInClick,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = HomeColors.ForestPrimary,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                    modifier = Modifier.height(42.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.sign_in),
                                        fontFamily = InterFont,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                OutlinedButton(
                                    onClick = onSignUpClick,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.5.dp, HomeColors.ForestPrimary),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                    modifier = Modifier.height(42.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.sign_up),
                                        fontFamily = InterFont,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HomeColors.ForestPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (state.isGoogleAccount) {
                GoogleProfileSection(
                    displayName = state.userDisplayName,
                    email = state.userEmail,
                    photoUrl = state.userPhotoUrl,
                    onEditNameClick = onEditNameClick,
                    onChangePasswordClick = onChangePasswordClick,
                    onSignOutClick = onSignOutClick
                )
            } else if (state.isEmailAccount) {
                EmailProfileSection(
                    displayName = state.userDisplayName,
                    email = state.userEmail,
                    onEditNameClick = onEditNameClick,
                    onChangePasswordClick = onChangePasswordClick,
                    onSignOutClick = onSignOutClick
                )
            }
        }
    }
}

// ─── Google Profile ──────────────────────────────────────────────────────────
@Composable
fun GoogleProfileSection(
    displayName: String,
    email: String,
    photoUrl: String?,
    onEditNameClick: () -> Unit,
    onChangePasswordClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SoftGreen.copy(alpha = 0.45f))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!photoUrl.isNullOrBlank()) {
            val context = LocalContext.current
            AsyncImage(
                model = remember(photoUrl) {
                    ImageRequest.Builder(context)
                        .data(photoUrl)
                        .size(160)
                        .crossfade(false)
                        .build()
                },
                contentDescription = stringResource(R.string.profile),
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .border(2.dp, PrimaryGreen.copy(alpha = 0.25f), CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(PrimaryGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = stringResource(R.string.profile),
                    modifier = Modifier.size(26.dp),
                    tint = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = displayName.ifBlank { email.substringBefore("@") },
                    fontFamily = InterFont,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Text(
                        text = "Google",
                        fontFamily = InterFont,
                        fontSize = 10.sp,
                        color = PrimaryGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = email,
                fontFamily = InterFont,
                fontSize = 12.sp,
                color = TextMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    HorizontalDivider(color = DividerLine, thickness = 1.dp)

    SettingsMenuItem(
        icon = Icons.Outlined.Edit,
        title = stringResource(R.string.edit_profile_name),
        showArrow = true,
        onClick = onEditNameClick
    )
    HorizontalDivider(color = DividerLine, thickness = 1.dp, modifier = Modifier.padding(start = 66.dp, end = 16.dp))

    SettingsMenuItem(
        icon = Icons.Outlined.Lock,
        title = stringResource(R.string.change_password),
        onClick = { },
        titleColor = TextLight,
        iconTint = TextLight,
        enabled = false,
        subtitle = stringResource(R.string.google_password_disabled)
    )
    HorizontalDivider(color = DividerLine, thickness = 1.dp, modifier = Modifier.padding(start = 66.dp, end = 16.dp))

    SettingsMenuItem(
        icon = Icons.AutoMirrored.Filled.ExitToApp,
        title = stringResource(R.string.sign_out),
        titleColor = ErrorRed,
        iconTint = ErrorRed,
        onClick = onSignOutClick
    )
}

// ─── Email/Password Profile ──────────────────────────────────────────────────
@Composable
fun EmailProfileSection(
    displayName: String,
    email: String,
    onEditNameClick: () -> Unit,
    onChangePasswordClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    val firstLetter = displayName.firstOrNull()?.uppercase() ?: "?"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SoftGreen.copy(alpha = 0.45f))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(PrimaryGreen),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = firstLetter,
                fontFamily = InterFont,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = displayName.ifBlank { email.substringBefore("@") },
                    fontFamily = InterFont,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Text(
                        text = "Verified",
                        fontFamily = InterFont,
                        fontSize = 10.sp,
                        color = PrimaryGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = email,
                fontFamily = InterFont,
                fontSize = 12.sp,
                color = TextMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    HorizontalDivider(color = DividerLine, thickness = 1.dp)

    SettingsMenuItem(
        icon = Icons.Outlined.Edit,
        title = stringResource(R.string.edit_profile_name),
        showArrow = true,
        onClick = onEditNameClick
    )
    HorizontalDivider(color = DividerLine, thickness = 1.dp, modifier = Modifier.padding(start = 66.dp, end = 16.dp))

    SettingsMenuItem(
        icon = Icons.Outlined.Lock,
        title = stringResource(R.string.change_password),
        showArrow = true,
        onClick = onChangePasswordClick
    )
    HorizontalDivider(color = DividerLine, thickness = 1.dp, modifier = Modifier.padding(start = 66.dp, end = 16.dp))

    SettingsMenuItem(
        icon = Icons.AutoMirrored.Filled.ExitToApp,
        title = stringResource(R.string.sign_out),
        titleColor = ErrorRed,
        iconTint = ErrorRed,
        onClick = onSignOutClick
    )
}

// ─── Language & Storage Section (Unified System Preferences Card) ────────────
@Composable
fun LanguageAndStorageSection(
    currentLanguage: String,
    onLanguageClick: () -> Unit,
    cacheSize: String,
    onClearCache: () -> Unit
) {
    Column {
        SectionTitle("Language & Storage")
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLanguageClick() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(SoftGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Language,
                        contentDescription = stringResource(R.string.language),
                        tint = PrimaryGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.language),
                        fontFamily = InterFont,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )
                    Text(
                        text = "Display language across the app",
                        fontFamily = InterFont,
                        fontSize = 12.sp,
                        color = TextMedium
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = SoftGreen
                ) {
                    Text(
                        text = currentLanguage,
                        fontFamily = InterFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryGreen,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.arrow),
                    tint = TextLight
                )
            }

            HorizontalDivider(color = DividerLine, thickness = 1.dp, modifier = Modifier.padding(start = 68.dp, end = 16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(SoftGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CleaningServices,
                        contentDescription = null,
                        tint = PrimaryGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "App Image & Temp Cache",
                        fontFamily = InterFont,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HomeColors.TextPrimary
                    )
                    Text(
                        text = "Current usage: $cacheSize",
                        fontFamily = InterFont,
                        fontSize = 12.sp,
                        color = HomeColors.TextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onClearCache,
                    shape = RoundedCornerShape(11.dp),
                    border = BorderStroke(1.dp, HomeColors.ForestPrimary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Text(
                        text = "Clear",
                        fontFamily = InterFont,
                        color = HomeColors.ForestPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ─── Notification Section ────────────────────────────────────────────────────
@Composable
fun NotificationSection(
    morningReminder: Boolean,
    eveningReminder: Boolean,
    morningHour: Int,
    morningMinute: Int,
    eveningHour: Int,
    eveningMinute: Int,
    vibrationEnabled: Boolean,
    onMorningReminderChange: (Boolean) -> Unit,
    onEveningReminderChange: (Boolean) -> Unit,
    onMorningTimeClick: () -> Unit,
    onEveningTimeClick: () -> Unit,
    onVibrationChange: (Boolean) -> Unit
) {
    Column {
        SectionTitle("Care Reminders & Alerts")
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard {
            SettingsSwitchItem(
                icon = Icons.Outlined.WbSunny,
                title = stringResource(R.string.morning_reminder),
                subtitle = "Daily morning plant check notification",
                isChecked = morningReminder,
                onCheckedChange = onMorningReminderChange
            )
            HorizontalDivider(color = DividerLine, thickness = 1.dp, modifier = Modifier.padding(start = 68.dp, end = 16.dp))

            NotificationTimeRow(
                label = "${stringResource(R.string.morning)} Schedule",
                time = formatTime(morningHour, morningMinute),
                enabled = morningReminder,
                onClick = onMorningTimeClick
            )
            HorizontalDivider(color = DividerLine, thickness = 1.dp, modifier = Modifier.padding(start = 68.dp, end = 16.dp))

            SettingsSwitchItem(
                icon = Icons.Outlined.Nightlight,
                title = stringResource(R.string.evening_reminder),
                subtitle = "Evening care routine follow-up",
                isChecked = eveningReminder,
                onCheckedChange = onEveningReminderChange
            )
            HorizontalDivider(color = DividerLine, thickness = 1.dp, modifier = Modifier.padding(start = 68.dp, end = 16.dp))

            NotificationTimeRow(
                label = "${stringResource(R.string.evening)} Schedule",
                time = formatTime(eveningHour, eveningMinute),
                enabled = eveningReminder,
                onClick = onEveningTimeClick
            )
            HorizontalDivider(color = DividerLine, thickness = 1.dp, modifier = Modifier.padding(start = 68.dp, end = 16.dp))

            SettingsSwitchItem(
                icon = Icons.Outlined.Vibration,
                title = stringResource(R.string.vibration),
                subtitle = "Vibrate on plant care & device alerts",
                isChecked = vibrationEnabled,
                onCheckedChange = onVibrationChange
            )
        }
    }
}

// ─── About Section ───────────────────────────────────────────────────────────
@Composable
fun AboutSection(context: android.content.Context) {
    Column {
        SectionTitle("About SmartPlantCare")
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                AboutInfoRow(stringResource(R.string.app_version), "1.0.0")
                AboutInfoRow(stringResource(R.string.ml_model), "On-Device TFLite AI")
                AboutInfoRow(stringResource(R.string.disease_classes), "22 Leaf Classes")
                AboutInfoRow(
                    stringResource(R.string.plantnet_connected),
                    stringResource(R.string.yes),
                    isValueBold = true,
                    valueColor = HomeColors.SuccessGreen
                )
                AboutInfoRow(stringResource(R.string.framework), "Jetpack Compose")
                AboutInfoRow(stringResource(R.string.database), "Firebase Realtime Sync")
                AboutInfoRow(stringResource(R.string.hardware), "SmartPlantCare ESP32 Box")
            }

            HorizontalDivider(color = DividerLine, thickness = 1.dp)

            SettingsMenuItem(
                icon = Icons.Outlined.Policy,
                title = stringResource(R.string.privacy_policy),
                showArrow = true,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_policy_url)))
                    try { context.startActivity(intent) } catch (_: Exception) { }
                }
            )
            HorizontalDivider(color = DividerLine, thickness = 1.dp, modifier = Modifier.padding(start = 68.dp, end = 16.dp))

            SettingsMenuItem(
                icon = Icons.Outlined.Description,
                title = stringResource(R.string.terms_of_service),
                showArrow = true,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.terms_of_service_url)))
                    try { context.startActivity(intent) } catch (_: Exception) { }
                }
            )
        }
    }
}

// ─── Time Picker Dialog ──────────────────────────────────────────────────────
@Composable
fun TimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    isMorning: Boolean? = null
) {
    var isAm by remember { mutableStateOf(initialHour < 12) }
    var displayHour by remember {
        mutableStateOf(
            when {
                initialHour == 0 -> 12
                initialHour > 12 -> initialHour - 12
                else -> initialHour
            }
        )
    }
    var minute by remember { mutableStateOf(initialMinute) }

    LaunchedEffect(isMorning) {
        isMorning?.let { isAm = it }
    }

    fun get24Hour(): Int {
        return when {
            !isAm && displayHour == 12 -> 12
            !isAm -> displayHour + 12
            displayHour == 12 -> 0
            else -> displayHour
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = SurfaceWhite,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SoftGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMorning == true) Icons.Outlined.WbSunny else Icons.Outlined.Nightlight,
                        contentDescription = null,
                        tint = PrimaryGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "$title ${stringResource(R.string.reminder_time)}",
                    fontFamily = InterFont,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Spacer(modifier = Modifier.height(20.dp))

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = PageBg,
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TimeSpinner(
                            value = displayHour,
                            onIncrement = { displayHour = if (displayHour == 12) 1 else displayHour + 1 },
                            onDecrement = { displayHour = if (displayHour == 1) 12 else displayHour - 1 }
                        )
                        Text(
                            text = ":",
                            fontFamily = InterFont,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryGreen,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        TimeSpinner(
                            value = minute,
                            onIncrement = { minute = (minute + 5) % 60 },
                            onDecrement = { minute = if (minute < 5) 55 else minute - 5 }
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (isMorning == null) {
                                AmPmButton(label = "AM", selected = isAm, onClick = { isAm = true })
                                Spacer(modifier = Modifier.height(6.dp))
                                AmPmButton(label = "PM", selected = !isAm, onClick = { isAm = false })
                            } else {
                                val label = if (isMorning) "AM" else "PM"
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = PrimaryGreen
                                ) {
                                    Text(
                                        text = label,
                                        fontFamily = InterFont,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(13.dp),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            fontFamily = InterFont,
                            fontWeight = FontWeight.SemiBold,
                            color = TextMedium
                        )
                    }
                    Button(
                        onClick = { onConfirm(get24Hour(), minute) },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryGreen,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.save),
                            fontFamily = InterFont,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ─── Reusable Components ─────────────────────────────────────────────────────

@Composable
fun PlantDialog(
    onDismissRequest: () -> Unit,
    icon: ImageVector,
    iconTint: Color = PrimaryGreen,
    iconBackground: Color = SoftGreen,
    title: String,
    body: String,
    confirmText: String,
    confirmColor: Color = PrimaryGreen,
    onConfirm: () -> Unit,
    dismissText: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceWhite,
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(iconBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = title,
                    fontFamily = InterFont,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = body,
                    fontFamily = InterFont,
                    fontSize = 14.sp,
                    color = TextMedium,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(13.dp),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Text(
                            text = dismissText,
                            fontFamily = InterFont,
                            fontWeight = FontWeight.SemiBold,
                            color = TextMedium
                        )
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = confirmColor,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = confirmText,
                            fontFamily = InterFont,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageOption(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(13.dp),
        color = if (isSelected) SoftGreen else PageBg,
        border = if (isSelected) BorderStroke(1.5.dp, PrimaryGreen) else BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontFamily = InterFont,
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) PrimaryGreen else TextDark,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = PrimaryGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun TimeSpinner(value: Int, onIncrement: () -> Unit, onDecrement: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onIncrement, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = "Increase",
                tint = PrimaryGreen,
                modifier = Modifier.size(26.dp)
            )
        }
        Text(
            text = String.format("%02d", value),
            fontFamily = InterFont,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark
        )
        IconButton(onClick = onDecrement, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Decrease",
                tint = PrimaryGreen,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
fun AmPmButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) PrimaryGreen else Color.Transparent,
        border = if (!selected) BorderStroke(1.dp, CardBorder) else null
    ) {
        Text(
            text = label,
            fontFamily = InterFont,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else TextMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontFamily = InterFont, fontSize = 13.sp, color = TextMedium) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryGreen,
            unfocusedBorderColor = CardBorder,
            focusedLabelColor = PrimaryGreen,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = PageBg
        )
    )
}

@Composable
fun NotificationTimeRow(
    label: String,
    time: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (enabled) SoftGreen else MutedIconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = label,
                tint = if (enabled) PrimaryGreen else TextLight,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            fontFamily = InterFont,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) TextDark else TextLight,
            modifier = Modifier.weight(1f)
        )
        Surface(
            color = if (enabled) SoftGreen else PageBg,
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, if (enabled) PrimaryGreen.copy(alpha = 0.25f) else CardBorder)
        ) {
            Text(
                text = time,
                fontFamily = InterFont,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) PrimaryGreen else TextLight,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        fontFamily = InterFont,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = HomeColors.TextPrimary,
        modifier = Modifier.padding(start = 2.dp)
    )
}

@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column { content() }
    }
}

@Composable
fun SettingsMenuItem(
    icon: ImageVector? = null,
    title: String,
    titleColor: Color = TextDark,
    iconTint: Color = PrimaryGreen,
    showArrow: Boolean = false,
    onClick: () -> Unit = {},
    enabled: Boolean = true,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (titleColor == ErrorRed)
                            HomeColors.AlertBg
                        else if (enabled) SoftGreen
                        else MutedIconBg
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (enabled) iconTint else TextLight,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = InterFont,
                fontSize = 14.sp,
                color = if (enabled) titleColor else TextLight,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontFamily = InterFont,
                    fontSize = 12.sp,
                    color = TextMedium,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (showArrow) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = TextLight,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    isChecked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = isChecked,
                        role = Role.Switch,
                        onValueChange = onCheckedChange
                    )
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (isChecked) SoftGreen else MutedIconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isChecked) PrimaryGreen else TextMedium,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = InterFont,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDark
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontFamily = InterFont,
                    fontSize = 12.sp,
                    color = TextMedium
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = isChecked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFCDD4CD)
            )
        )
    }
}

@Composable
fun AboutInfoRow(
    label: String,
    value: String,
    isValueBold: Boolean = false,
    valueColor: Color = TextDark
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontFamily = InterFont,
            fontSize = 13.sp,
            color = TextMedium
        )
        Text(
            text = value,
            fontFamily = InterFont,
            fontSize = 13.sp,
            color = valueColor,
            fontWeight = if (isValueBold) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

// ─── Helper ──────────────────────────────────────────────────────────────────
private fun formatTime(hour: Int, minute: Int): String {
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return String.format("%d:%02d %s", displayHour, minute, amPm)
}

// ─── SmartPlantCare Box Section ──────────────────────────────────────────────
@Composable
private fun SmartPlantCareBoxSection(
    uiState: com.example.smartplantcare.iot.MyPlantsUiState?,
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
        nowMs = System.currentTimeMillis()
        while (snapshot != null) {
            kotlinx.coroutines.delay(2_000L)
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
    val isOnline = snapshot != null && com.example.smartplantcare.iot.IotDomainLogic.isDeviceOnlineFresh(
        status = snapshot.status,
        nowMs = evalNowMs,
        receivedAtClientMs = snapshot.receivedAtClientMs,
        telemetryUpdatedAt = snapshot.telemetry.updatedAt
    )
    val reservoirPctText = if (isOnline && snapshot?.telemetry?.reservoirValid == true && snapshot.telemetry.reservoirPct != null) {
        "${snapshot.telemetry.reservoirPct.toInt()}%"
    } else {
        "--"
    }
    val isAuto = snapshot?.config?.autoIrrigationEnabled ?: true

    Column {
        SectionTitle("SmartPlantCare Controller")
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isConnected) SoftGreen else MutedIconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Sensors,
                            contentDescription = null,
                            tint = if (isConnected) PrimaryGreen else TextMedium,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (isConnected) boxName else "No Plant Box Paired",
                                fontFamily = InterFont,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isConnected) {
                                Surface(
                                    color = if (isOnline && isPhoneOnline) HomeColors.SuccessBg else HomeColors.AlertBg,
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (isOnline && isPhoneOnline) HomeColors.SuccessGreen else HomeColors.AlertRed)
                                        )
                                        Text(
                                            text = when {
                                                isOnline && !isPhoneOnline -> "No Internet"
                                                isOnline -> "Online"
                                                else -> "Offline"
                                            },
                                            fontFamily = InterFont,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isOnline && isPhoneOnline) HomeColors.SuccessGreen else HomeColors.AlertRed
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isConnected) {
                                when {
                                    isOnline && !isPhoneOnline -> "Phone has no internet connection"
                                    isOnline -> "${if (isAuto) "Auto Care" else "Manual"} • Water Tank $reservoirPctText"
                                    else -> "ESP32 box offline • Telemetry paused (--)"
                                }
                            } else "Pair your ESP32 controller for 3-zone automated irrigation",
                            fontFamily = InterFont,
                            fontSize = 12.sp,
                            color = TextMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onOpenMyPlants,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HomeColors.ForestPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (isConnected) "Manage in My Plants" else "Pair / Connect Plant Box",
                        fontFamily = InterFont,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
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

// ─── Units & Hardware Preferences Section ────────────────────────────────────
@Composable
private fun UnitsAndPreferencesSection(
    tempUnit: String,
    moistureUnit: String,
    onTempUnitChange: (String) -> Unit,
    onMoistureUnitChange: (String) -> Unit,
    onOpenGuide: () -> Unit
) {
    Column {
        SectionTitle("Units & Hardware Guide")
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard {
            // Temperature Unit Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(SoftGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Thermostat,
                        contentDescription = null,
                        tint = PrimaryGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Temperature Unit",
                        fontFamily = InterFont,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HomeColors.TextPrimary
                    )
                    Text(
                        text = "Weather forecast & ambient sensor",
                        fontFamily = InterFont,
                        fontSize = 12.sp,
                        color = HomeColors.TextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(HomeColors.PageBg)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AmPmButton(label = "°C", selected = tempUnit == "C", onClick = { onTempUnitChange("C") })
                    AmPmButton(label = "°F", selected = tempUnit == "F", onClick = { onTempUnitChange("F") })
                }
            }

            HorizontalDivider(color = DividerLine, thickness = 1.dp, modifier = Modifier.padding(start = 68.dp, end = 16.dp))

            // Measurement Depth Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(SoftGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Straighten,
                        contentDescription = null,
                        tint = PrimaryGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reservoir Depth Unit",
                        fontFamily = InterFont,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HomeColors.TextPrimary
                    )
                    Text(
                        text = "Ultrasonic water tank air gap",
                        fontFamily = InterFont,
                        fontSize = 12.sp,
                        color = HomeColors.TextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(HomeColors.PageBg)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AmPmButton(label = "in", selected = moistureUnit == "in", onClick = { onMoistureUnitChange("in") })
                    AmPmButton(label = "cm", selected = moistureUnit == "cm", onClick = { onMoistureUnitChange("cm") })
                }
            }

            HorizontalDivider(color = DividerLine, thickness = 1.dp, modifier = Modifier.padding(start = 68.dp, end = 16.dp))

            // How It Works & FAQ Guide Row
            SettingsMenuItem(
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                title = "How SmartPlantCare Works (FAQ)",
                subtitle = "Rain guard, auto-watering, sounds & family sharing",
                showArrow = true,
                onClick = onOpenGuide
            )
        }
    }
}

// ─── How SmartPlantCare Works (Interactive FAQ Guide Dialog) ─────────────────
private data class FaqGuideEntry(
    val question: String,
    val answer: String,
    val icon: ImageVector
)

@Composable
private fun HardwareGuideDialog(onDismiss: () -> Unit) {
    val faqItems = remember {
        listOf(
            FaqGuideEntry(
                question = "How does Automatic Watering work?",
                answer = "Your SmartPlantCare box monitors soil moisture for Tomato, Chili, and Eggplant around the clock. In Auto mode, when soil drops below your Start % dry threshold, it waters that specific plant until it reaches your Stop % target, then pauses to let the soil absorb the water.",
                icon = Icons.Outlined.Eco
            ),
            FaqGuideEntry(
                question = "How do Weather Forecast & Rain Sensor work?",
                answer = "In Outdoor Mode, the box combines the 3-hour rain forecast (from the Owner's GPS location) with the rain sensor. The weather chance must reach your threshold FIRST before the rain sensor gets wet to pause watering as real rain. If the sensor gets wet before weather meets the threshold, it is treated as accidental wet — you receive a notification and watering continues normally.",
                icon = Icons.Outlined.Cloud
            ),
            FaqGuideEntry(
                question = "How does Water Tank protection work?",
                answer = "The box continuously tracks how much water is left in your 7-inch tank. You get a Low Water alert at 20%, and auto-watering safely pauses at 10% or empty so the water pump never runs dry.",
                icon = Icons.Outlined.WaterDrop
            ),
            FaqGuideEntry(
                question = "What do the Box Beeps mean?",
                answer = "• 1 short beep: Watering started, watering finished, Wi-Fi connected, or box ready.\n• 2 short beeps: Low water warning or confirmed rain pause.\n• 3 short beeps: Empty water tank or emergency safety stop.\nYou can mute box sounds anytime in Device Settings.",
                icon = Icons.Outlined.NotificationsActive
            ),
            FaqGuideEntry(
                question = "What is Outdoor vs. Indoor Mode?",
                answer = "Outdoor Mode turns on weather and rain protection so your plants are not overwatered during rain. Indoor Mode turns off rain checks so indoor or greenhouse plants follow their normal soil moisture schedule.",
                icon = Icons.Outlined.WbSunny
            ),
            FaqGuideEntry(
                question = "How does Sharing a Plant Box work?",
                answer = "Only the Owner can create a 24-hour invite code in Share My Plant. Invite family or caretakers as Operator (can water and adjust settings) or Viewer (view-only). Shared members automatically use the Owner's saved box location without needing GPS permission.",
                icon = Icons.Outlined.Group
            )
        )
    }

    var expandedIndex by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceWhite,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(HomeColors.SoftMint),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = null,
                            tint = HomeColors.ForestPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "How SmartPlantCare Works",
                            fontFamily = InterFont,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.TextPrimary
                        )
                        Text(
                            text = "Tap any question to learn more",
                            fontFamily = InterFont,
                            fontSize = 12.sp,
                            color = HomeColors.TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    faqItems.forEachIndexed { index, item ->
                        FaqAccordionItem(
                            item = item,
                            expanded = expandedIndex == index,
                            onToggle = {
                                expandedIndex = if (expandedIndex == index) -1 else index
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HomeColors.ForestPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Got It",
                        fontFamily = InterFont,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun FaqAccordionItem(
    item: FaqGuideEntry,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (expanded) HomeColors.SoftMint.copy(alpha = 0.45f) else HomeColors.PageBg,
        border = BorderStroke(
            width = 1.dp,
            color = if (expanded) HomeColors.ForestPrimary.copy(alpha = 0.28f) else CardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (expanded) HomeColors.ForestPrimary else HomeColors.SoftMint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = if (expanded) Color.White else HomeColors.ForestPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = item.question,
                    fontFamily = InterFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary,
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = HomeColors.ForestMedium,
                    modifier = Modifier.size(20.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = HomeColors.DividerLine, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = item.answer,
                        fontFamily = InterFont,
                        fontSize = 12.sp,
                        color = HomeColors.TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

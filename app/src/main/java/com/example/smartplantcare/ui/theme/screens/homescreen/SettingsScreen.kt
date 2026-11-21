package com.example.smartplantcare.ui.theme.screens.homescreen

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.smartplantcare.R
import com.example.smartplantcare.ui.theme.ErrorRed
import com.example.smartplantcare.ui.theme.PrimaryGreen
import com.example.smartplantcare.ui.theme.SoftGreen
import com.example.smartplantcare.ui.theme.TextDark
import com.example.smartplantcare.ui.theme.TextLight
import com.example.smartplantcare.ui.theme.TextMedium

// ─── Design Tokens (shared from HomeUiTokens) ────────────────────────────────
private val PageBg        = HomeColors.PageBg
private val SurfaceWhite  = HomeColors.SurfaceWhite
private val CardBorder    = HomeColors.CardBorder
private val MutedIconBg   = HomeColors.PlaceholderBg
private val DividerLine   = HomeColors.DividerLine
private val InterFont     = HomeInterFont

// ─── SettingsScreen ────────────────────────────────────────────────────────────
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
    val scope = rememberCoroutineScope()

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

    // ─── Dialogs ───────────────────────────────────────────────────────────────

    if (showSignOutDialog) {
        PlantDialog(
            onDismissRequest = { showSignOutDialog = false },
            icon = Icons.AutoMirrored.Filled.ExitToApp,
            iconTint = ErrorRed,
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
                            .clip(CircleShape)
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

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { vm.dismissLanguageDialog() },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.cancel), color = TextMedium)
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
                            .clip(CircleShape)
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
                        label = { Text(stringResource(R.string.edit_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryGreen,
                            unfocusedBorderColor = CardBorder,
                            focusedLabelColor = PrimaryGreen,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = SoftGreen.copy(alpha = 0.4f)
                        )
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { vm.dismissEditNameDialog() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Text(stringResource(R.string.cancel), color = TextMedium)
                        }
                        Button(
                            onClick = { vm.saveProfileName() },
                            enabled = !state.isLoading,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
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
                                    stringResource(R.string.save),
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
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
                            .clip(CircleShape)
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
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Text(stringResource(R.string.cancel), color = TextMedium)
                        }
                        Button(
                            onClick = { vm.changePassword() },
                            enabled = !state.isLoading,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
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
                                    stringResource(R.string.update_password),
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
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

    // ─── Main Layout ───────────────────────────────────────────────────────────
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
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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

                StorageAndCacheSection(
                    cacheSize = state.cacheSizeFormatted,
                    onClearCache = { vm.clearAppCache() }
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

                LanguageSection(
                    currentLanguage = if (state.language == "fil")
                        stringResource(R.string.filipino)
                    else
                        stringResource(R.string.english),
                    onClick = { vm.showLanguageDialog() }
                )

                AboutSection(context = context)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.smartplantcare_text),
                        fontSize = 12.sp,
                        color = TextLight
                    )
                    Text(
                        text = stringResource(R.string.version_text),
                        fontSize = 12.sp,
                        color = TextLight.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// ─── Header Banner ─────────────────────────────────────────────────────────────
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
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 16.dp)
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
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_subtitle),
                        fontFamily = InterFont,
                        fontSize = 13.sp,
                        color = TextMedium
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Image(
                    painter = painterResource(id = R.drawable.appslogo2),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
        HorizontalDivider(color = DividerLine, thickness = 1.dp)
    }
}

// ─── Profile Section ──────────────────────────────────────────────────────────
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
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(SoftGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = stringResource(R.string.guest_user),
                            modifier = Modifier.size(30.dp),
                            tint = PrimaryGreen
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.guest_user),
                            fontFamily = InterFont,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Text(
                            text = "Sign in to save your progress",
                            fontSize = 13.sp,
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
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PrimaryGreen,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.fillMaxWidth().height(36.dp)
                                ) {
                                    Text(stringResource(R.string.sign_in), color = Color.White,
                                        fontSize = 13.sp)
                                }
                                OutlinedButton(
                                    onClick = onSignUpClick,
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.5.dp, PrimaryGreen),
                                    modifier = Modifier.fillMaxWidth().height(36.dp)
                                ) {
                                    Text(stringResource(R.string.sign_up), color = PrimaryGreen,
                                        fontSize = 13.sp)
                                }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onSignInClick,
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryGreen,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.sign_in),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                            OutlinedButton(
                                onClick = onSignUpClick,
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.5.dp, PrimaryGreen),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.sign_up),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryGreen
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

// ─── Google Profile ───────────────────────────────────────────────────────────
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
            .background(SoftGreen.copy(alpha = 0.35f))
            .padding(horizontal = 16.dp, vertical = 18.dp),
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
                    .size(58.dp)
                    .clip(CircleShape)
                    .border(2.dp, PrimaryGreen.copy(alpha = 0.25f), CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(PrimaryGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = stringResource(R.string.profile),
                    modifier = Modifier.size(28.dp),
                    tint = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName.ifBlank { email.substringBefore("@") },
                fontFamily = InterFont,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Text(
                text = email,
                fontSize = 13.sp,
                color = TextMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SoftGreen
            ) {
                Text(
                    text = "Google Account",
                    fontSize = 11.sp,
                    color = PrimaryGreen,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
    }

    HorizontalDivider(color = DividerLine, thickness = 1.dp)

    SettingsMenuItem(
        icon = Icons.Outlined.Edit,
        title = stringResource(R.string.edit_profile_name),
        onClick = onEditNameClick
    )
    HorizontalDivider(color = DividerLine, thickness = 1.dp, modifier = Modifier.padding(start = 56.dp))

    SettingsMenuItem(
        icon = Icons.Outlined.Lock,
        title = stringResource(R.string.change_password),
        onClick = { },
        titleColor = TextLight.copy(alpha = 0.6f),
        iconTint = TextLight.copy(alpha = 0.4f),
        enabled = false,
        subtitle = stringResource(R.string.google_password_disabled)
    )
    HorizontalDivider(color = DividerLine, thickness = 1.dp, modifier = Modifier.padding(start = 56.dp))

    SettingsMenuItem(
        icon = Icons.AutoMirrored.Filled.ExitToApp,
        title = stringResource(R.string.sign_out),
        titleColor = ErrorRed,
        iconTint = ErrorRed,
        onClick = onSignOutClick
    )
}

// ─── Email/Password Profile ───────────────────────────────────────────────────
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
            .background(SoftGreen.copy(alpha = 0.35f))
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(PrimaryGreen),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = firstLetter,
                fontFamily = InterFont,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName.ifBlank { email.substringBefore("@") },
                fontFamily = InterFont,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Text(
                text = email,
                fontSize = 13.sp,
                color = TextMedium
            )
        }
    }

    HorizontalDivider(color = DividerLine, thickness = 1.dp)

    SettingsMenuItem(
        icon = Icons.Outlined.Edit,
        title = stringResource(R.string.edit_profile_name),
        onClick = onEditNameClick
    )
    HorizontalDivider(color = DividerLine, thickness = 1.dp, modifier = Modifier.padding(start = 56.dp))

    SettingsMenuItem(
        icon = Icons.Outlined.Lock,
        title = stringResource(R.string.change_password),
        onClick = onChangePasswordClick
    )
    HorizontalDivider(color = DividerLine, thickness = 1.dp, modifier = Modifier.padding(start = 56.dp))

    SettingsMenuItem(
        icon = Icons.AutoMirrored.Filled.ExitToApp,
        title = stringResource(R.string.sign_out),
        titleColor = ErrorRed,
        iconTint = ErrorRed,
        onClick = onSignOutClick
    )
}

// ─── Language Section ─────────────────────────────────────────────────────────
@Composable
fun LanguageSection(
    currentLanguage: String,
    onClick: () -> Unit
) {
    Column {
        SectionTitle(stringResource(R.string.language).uppercase())
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
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
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextDark
                    )
                    Text(
                        text = currentLanguage,
                        fontSize = 13.sp,
                        color = TextMedium
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.arrow),
                    tint = TextLight.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ─── Notification Section ─────────────────────────────────────────────────────
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
        SectionTitle(stringResource(R.string.notification_preferences))
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.notification_subtitle),
            fontSize = 12.sp,
            color = TextMedium,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )
        SettingsCard {
            SettingsSwitchItem(
                icon = Icons.Outlined.WbSunny,
                title = stringResource(R.string.morning_reminder),
                isChecked = morningReminder,
                onCheckedChange = onMorningReminderChange
            )
            HorizontalDivider(color = DividerLine, thickness = 1.dp, modifier = Modifier.padding(start = 56.dp))

            SettingsSwitchItem(
                icon = Icons.Outlined.Nightlight,
                title = stringResource(R.string.evening_reminder),
                isChecked = eveningReminder,
                onCheckedChange = onEveningReminderChange
            )
            HorizontalDivider(color = DividerLine, thickness = 1.dp, modifier = Modifier.padding(start = 56.dp))

            // Morning time row
            NotificationTimeRow(
                label = stringResource(R.string.morning),
                time = formatTime(morningHour, morningMinute),
                enabled = morningReminder,
                onClick = onMorningTimeClick
            )
            HorizontalDivider(color = DividerLine, thickness = 1.dp, modifier = Modifier.padding(start = 56.dp))

            // Evening time row
            NotificationTimeRow(
                label = stringResource(R.string.evening),
                time = formatTime(eveningHour, eveningMinute),
                enabled = eveningReminder,
                onClick = onEveningTimeClick
            )
            HorizontalDivider(color = DividerLine, thickness = 1.dp, modifier = Modifier.padding(start = 56.dp))

            SettingsSwitchItem(
                icon = Icons.Outlined.Vibration,
                title = stringResource(R.string.vibration),
                isChecked = vibrationEnabled,
                onCheckedChange = onVibrationChange
            )
        }
    }
}

// ─── About Section ────────────────────────────────────────────────────────────
@Composable
fun AboutSection(context: android.content.Context) {
    Column {
        SectionTitle(stringResource(R.string.about_smartplantcare))
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                AboutInfoRow(stringResource(R.string.app_version), "1.0.0")
                AboutInfoRow(stringResource(R.string.ml_model), "int8")
                AboutInfoRow(stringResource(R.string.disease_classes), "22")
                AboutInfoRow(
                    stringResource(R.string.plantnet_connected),
                    stringResource(R.string.yes),
                    isValueBold = true,
                    valueColor = PrimaryGreen
                )
                AboutInfoRow(stringResource(R.string.framework), "Jetpack Compose")
                AboutInfoRow(stringResource(R.string.database), "Firebase")
                AboutInfoRow(stringResource(R.string.hardware), "IoT ESP32")
            }

            HorizontalDivider(color = DividerLine, thickness = 1.dp)

            SettingsMenuItem(
                title = stringResource(R.string.privacy_policy),
                showArrow = true,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_policy_url)))
                    try { context.startActivity(intent) } catch (_: Exception) { }
                }
            )
            HorizontalDivider(color = DividerLine, thickness = 1.dp)

            SettingsMenuItem(
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

// ─── Time Picker Dialog (preserved logic, improved look) ─────────────────────
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
                        .clip(CircleShape)
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
                Spacer(modifier = Modifier.height(24.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SoftGreen.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryGreen,
                            modifier = Modifier.padding(horizontal = 6.dp)
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
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Text(stringResource(R.string.cancel), color = TextMedium)
                    }
                    Button(
                        onClick = { onConfirm(get24Hour(), minute) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryGreen,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            stringResource(R.string.save),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ─── Reusable Components ──────────────────────────────────────────────────────

/** Generic confirmation dialog with icon badge */
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
                        .clip(CircleShape)
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
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Text(dismissText, color = TextMedium)
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = confirmColor,
                            contentColor = Color.White
                        )
                    ) {
                        Text(confirmText, color = Color.White, fontWeight = FontWeight.SemiBold)
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
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) SoftGreen else Color.Transparent,
        border = if (isSelected) BorderStroke(1.5.dp, PrimaryGreen.copy(alpha = 0.4f)) else BorderStroke(1.dp, CardBorder),
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
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) PrimaryGreen else TextDark,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
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
        IconButton(onClick = onIncrement, modifier = Modifier.size(42.dp)) {
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
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark
        )
        IconButton(onClick = onDecrement, modifier = Modifier.size(42.dp)) {
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
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
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
        label = { Text(label, fontSize = 13.sp, color = TextMedium) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryGreen,
            unfocusedBorderColor = CardBorder,
            focusedLabelColor = PrimaryGreen,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = SoftGreen.copy(alpha = 0.3f)
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
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (enabled) SoftGreen else SoftGreen.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = label,
                tint = if (enabled) PrimaryGreen else TextLight.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) TextDark else TextLight.copy(alpha = 0.5f)
            )
            Text(
                text = time,
                fontSize = 13.sp,
                color = if (enabled) PrimaryGreen else TextLight.copy(alpha = 0.35f)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = if (enabled) TextLight.copy(alpha = 0.7f) else TextLight.copy(alpha = 0.25f)
        )
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = TextMedium
    )
}

@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
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
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (titleColor == ErrorRed)
                            ErrorRed.copy(alpha = 0.08f)
                        else if (enabled) SoftGreen
                        else SoftGreen.copy(alpha = 0.3f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (enabled) iconTint else iconTint.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                color = if (enabled) titleColor else titleColor.copy(alpha = 0.4f),
                fontWeight = FontWeight.Normal
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = TextLight.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (showArrow) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = TextLight.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isChecked) SoftGreen else SoftGreen.copy(alpha = 0.35f)),
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
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = TextDark,
            modifier = Modifier.weight(1f)
        )
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
    valueColor: Color = TextMedium
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 14.sp, color = TextMedium)
        Text(
            text = value,
            fontSize = 14.sp,
            color = valueColor,
            fontWeight = if (isValueBold) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

// ─── Helper ───────────────────────────────────────────────────────────────────
private fun formatTime(hour: Int, minute: Int): String {
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return String.format("%d:%02d %s", displayHour, minute, amPm)
}

// ─── SmartPlantCare Box Section ────────────────────────────────────────────────
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
            kotlinx.coroutines.delay(5_000L)
            nowMs = System.currentTimeMillis()
        }
    }
    val isOnline = snapshot != null && com.example.smartplantcare.iot.IotDomainLogic.isDeviceOnlineFresh(
        status = snapshot.status,
        nowMs = nowMs,
        receivedAtClientMs = snapshot.receivedAtClientMs,
        telemetryUpdatedAt = snapshot.telemetry.updatedAt
    )
    val reservoirPctText = if (isOnline && snapshot?.telemetry?.reservoirValid == true && snapshot.telemetry.reservoirPct != null) {
        "${snapshot.telemetry.reservoirPct.toInt()}%"
    } else {
        "--"
    }

    Column {
        SectionTitle("SMARTPLANTCARE BOX")
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isConnected) boxName else "No Box Paired",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextDark
                            )
                            if (isConnected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isOnline) HomeColors.SuccessGreen else HomeColors.AlertRed)
                                )
                            }
                        }
                        Text(
                            text = if (isConnected) {
                                if (isOnline) "Online • Water $reservoirPctText"
                                else "Offline • Sensors --"
                            } else "Connect ESP32 for 3-zone automated care",
                            fontSize = 12.sp,
                            color = TextMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onOpenMyPlants,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryGreen,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (isConnected) "Manage in My Plants" else "Pair / Connect Box",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// ─── Units & Hardware Preferences Section ──────────────────────────────────────
@Composable
private fun UnitsAndPreferencesSection(
    tempUnit: String,
    moistureUnit: String,
    onTempUnitChange: (String) -> Unit,
    onMoistureUnitChange: (String) -> Unit,
    onOpenGuide: () -> Unit
) {
    Column {
        SectionTitle("PREFERENCES & HARDWARE")
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
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
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
                    Text("Temperature Unit", fontSize = 15.sp, color = TextDark)
                    Text("Display weather and sensor temp", fontSize = 12.sp, color = TextMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AmPmButton(label = "°C", selected = tempUnit == "C", onClick = { onTempUnitChange("C") })
                    AmPmButton(label = "°F", selected = tempUnit == "F", onClick = { onTempUnitChange("F") })
                }
            }

            HorizontalDivider(color = DividerLine, thickness = 1.dp)

            // Measurement Depth Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
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
                    Text("Depth Units", fontSize = 15.sp, color = TextDark)
                    Text("Tank depth and soil height", fontSize = 12.sp, color = TextMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AmPmButton(label = "Inches", selected = moistureUnit == "in", onClick = { onMoistureUnitChange("in") })
                    AmPmButton(label = "cm", selected = moistureUnit == "cm", onClick = { onMoistureUnitChange("cm") })
                }
            }

            HorizontalDivider(color = DividerLine, thickness = 1.dp)

            // Hardware Guide Row
            SettingsMenuItem(
                icon = Icons.Outlined.MenuBook,
                title = "Hardware & Sensors Guide",
                subtitle = "How soil, ultrasonic, rain & relays operate",
                showArrow = true,
                onClick = onOpenGuide
            )
        }
    }
}

// ─── Storage & Cache Section ──────────────────────────────────────────────────
@Composable
private fun StorageAndCacheSection(
    cacheSize: String,
    onClearCache: () -> Unit
) {
    Column {
        SectionTitle("STORAGE & DATA")
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
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
                    Text("App Image & Temp Cache", fontSize = 15.sp, color = TextDark)
                    Text("Current size: $cacheSize", fontSize = 12.sp, color = TextMedium)
                }
                Button(
                    onClick = onClearCache,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryGreen,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Clear", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Hardware Guide Dialog ────────────────────────────────────────────────────
@Composable
private fun HardwareGuideDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceWhite,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState())
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
                            imageVector = Icons.Outlined.Memory,
                            contentDescription = null,
                            tint = PrimaryGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Hardware & Sensors Guide",
                            fontFamily = InterFont,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Text(
                            text = "SmartPlantCare ESP32 Architecture",
                            fontSize = 12.sp,
                            color = TextMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                GuideItem(
                    title = "🌱 3-Zone Soil Moisture Sensors",
                    desc = "Capacitive analog sensors in Tomato (GPIO 34), Chili (GPIO 35), and Eggplant (GPIO 32). In Auto mode, irrigation automatically triggers when moisture falls below the dry threshold and stops when target % is reached."
                )
                Spacer(modifier = Modifier.height(10.dp))
                GuideItem(
                    title = "💧 Ultrasonic Water Tank Level (HC-SR04)",
                    desc = "Measures reservoir depth calibrated for a standard 7-inch (17.8 cm) tank. Real-time water percentage is tracked, and low-water safety locks prevent the 12V pump from dry-running when level falls below 20%."
                )
                Spacer(modifier = Modifier.height(10.dp))
                GuideItem(
                    title = "🌧️ Rain & Weather Safety Gate",
                    desc = "The physical digital rain sensor (GPIO 39) combined with GPS open-meteo hourly forecasts halts irrigation during rainfall, protecting plant root systems from over-saturation."
                )
                Spacer(modifier = Modifier.height(10.dp))
                GuideItem(
                    title = "⚡ Dual 4-Channel Relay Actuation",
                    desc = "Irrigation energizes the 12V submersible power pump and the selected plant zone's 12V solenoid valve simultaneously, delivering water exclusively to the designated plant."
                )

                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryGreen,
                        contentColor = Color.White
                    )
                ) {
                    Text("Got It", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun GuideItem(title: String, desc: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = HomeColors.PageBg,
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = desc, fontSize = 12.sp, color = TextMedium, lineHeight = 17.sp)
        }
    }
}

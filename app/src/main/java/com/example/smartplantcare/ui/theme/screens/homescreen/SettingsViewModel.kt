package com.example.smartplantcare.ui.theme.screens.homescreen

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartplantcare.MainActivity
import com.example.smartplantcare.auth.AuthViewModel
import com.example.smartplantcare.notification.AlertPreferences
import com.example.smartplantcare.notification.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.example.smartplantcare.util.NetworkMonitor
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class SettingsState(
    val language: String = "en",
    val waterTankAlertsEnabled: Boolean = true,
    val irrigationRainAlertsEnabled: Boolean = true,
    val deviceHealthAlertsEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val isGoogleAccount: Boolean = false,
    val isGuest: Boolean = false,
    val isEmailAccount: Boolean = false,
    val userDisplayName: String = "",
    val userEmail: String = "",
    val userPhotoUrl: String? = null,
    val showLanguageDialog: Boolean = false,
    val showEditNameDialog: Boolean = false,
    val showChangePasswordDialog: Boolean = false,
    val snackbarMessage: String? = null,
    val editNameText: String = "",
    val currentPasswordText: String = "",
    val newPasswordText: String = "",
    val confirmPasswordText: String = "",
    val isLoading: Boolean = false,
    // Storage, Units & Hardware Guide
    val cacheSizeFormatted: String = "0 KB",
    val temperatureUnit: String = "C",
    val measurementUnit: String = "in",
    val showHardwareGuideDialog: Boolean = false
)

class SettingsViewModel(
    private val context: Context
) : ViewModel() {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("smartplantcare_settings", Context.MODE_PRIVATE)

    private val auth = FirebaseAuth.getInstance()
    private val authViewModel = AuthViewModel(context)

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadSettings()
        loadAuthState()
    }

    private fun loadSettings() {
        val language = prefs.getString("language", "en") ?: "en"
        val waterTankAlerts = prefs.getBoolean(AlertPreferences.KEY_WATER_TANK, true)
        val irrigationRainAlerts = prefs.getBoolean(AlertPreferences.KEY_IRRIGATION_RAIN, true)
        val deviceHealthAlerts = prefs.getBoolean(AlertPreferences.KEY_DEVICE_HEALTH, true)
        val vibrationEnabled = prefs.getBoolean("vibration_enabled", true)
        val temperatureUnit = prefs.getString("temp_unit", "C") ?: "C"
        val measurementUnit = prefs.getString("measurement_unit", "in") ?: "in"

        _state.value = _state.value.copy(
            language = language,
            waterTankAlertsEnabled = waterTankAlerts,
            irrigationRainAlertsEnabled = irrigationRainAlerts,
            deviceHealthAlertsEnabled = deviceHealthAlerts,
            vibrationEnabled = vibrationEnabled,
            temperatureUnit = temperatureUnit,
            measurementUnit = measurementUnit
        )

        refreshCacheSize()
    }

    private fun loadAuthState() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Check if user has email/password provider by looking at providerData
            val hasEmailProvider = currentUser.providerData.any { provider ->
                provider.providerId.contains("password", ignoreCase = true)
            }
            val isGoogle = !hasEmailProvider
            val displayName = currentUser.displayName ?: ""
            val email = currentUser.email ?: ""
            val photoUrl = currentUser.photoUrl?.toString()

            _state.value = _state.value.copy(
                isGoogleAccount = isGoogle,
                isEmailAccount = !isGoogle,
                isGuest = false,
                userDisplayName = displayName.ifBlank {
                    // Try Firestore name
                    displayName.ifBlank { email.substringBefore("@") }
                },
                userEmail = email,
                userPhotoUrl = photoUrl
            )

            // Load display name from Firestore
            loadFirestoreProfile()
        } else {
            // Check guest mode
            val guestPrefs = context.getSharedPreferences("smartplantcare_prefs", Context.MODE_PRIVATE)
            val isGuestMode = guestPrefs.getBoolean("is_guest_mode", false)

            if (isGuestMode) {
                _state.value = _state.value.copy(
                    isGoogleAccount = false,
                    isEmailAccount = false,
                    isGuest = true,
                    userDisplayName = "Guest User",
                    userEmail = "guest@smartplantcare.app",
                    userPhotoUrl = null
                )
            } else {
                _state.value = _state.value.copy(
                    isGoogleAccount = false,
                    isEmailAccount = false,
                    isGuest = false,
                    userDisplayName = "",
                    userEmail = "",
                    userPhotoUrl = null
                )
            }
        }
    }

    private fun loadFirestoreProfile() {
        viewModelScope.launch {
            try {
                NetworkMonitor.notifyIfUnstable()
                val currentUser = auth.currentUser ?: return@launch
                val email = currentUser.email.orEmpty()
                val snapshot = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUser.uid)
                    .get()
                    .await()

                val savedName = snapshot.getString("name").orEmpty()
                val displayName = if (savedName.isNotBlank()) savedName
                    else currentUser.displayName.orEmpty().ifBlank { email.substringBefore("@") }

                _state.value = _state.value.copy(
                    userDisplayName = displayName,
                    userEmail = snapshot.getString("email").orEmpty().ifBlank { email }
                )
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Failed to load Firestore profile", e)
            }
        }
    }

    // ─── Language ─────────────────────────────────────────────────────────────

    fun showLanguageDialog() {
        _state.value = _state.value.copy(showLanguageDialog = true)
    }

    fun dismissLanguageDialog() {
        _state.value = _state.value.copy(showLanguageDialog = false)
    }

    fun setLanguage(lang: String) {
        prefs.edit().putString("language", lang).apply()
        _state.value = _state.value.copy(language = lang, showLanguageDialog = false)
        // Locale will be applied by the SharedPreferences listener in MainScreen
        // using the Activity context, which triggers full recomposition
    }

    // ─── Profile Name ─────────────────────────────────────────────────────────

    fun showEditNameDialog() {
        _state.value = _state.value.copy(
            showEditNameDialog = true,
            editNameText = _state.value.userDisplayName
        )
    }

    fun dismissEditNameDialog() {
        _state.value = _state.value.copy(showEditNameDialog = false)
    }

    fun updateEditNameText(text: String) {
        _state.value = _state.value.copy(editNameText = text)
    }

    fun saveProfileName() {
        val newName = _state.value.editNameText.trim()
        if (newName.isBlank()) {
            _state.value = _state.value.copy(
                snackbarMessage = "Name cannot be empty.",
                showEditNameDialog = false
            )
            return
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            _state.value = _state.value.copy(
                snackbarMessage = "You must be signed in to edit your name.",
                showEditNameDialog = false
            )
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            NetworkMonitor.notifyIfUnstable()
            try {
                // Update Firebase display name
                val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(newName)
                    .build()
                currentUser.updateProfile(profileUpdates).await()

                // Update Firestore
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUser.uid)
                    .update("name", newName)
                    .await()

                _state.value = _state.value.copy(
                    userDisplayName = newName,
                    isLoading = false,
                    showEditNameDialog = false,
                    snackbarMessage = "Profile name updated successfully."
                )
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Failed to update profile name", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    snackbarMessage = "Failed to update name: ${e.localizedMessage ?: "Unknown error"}",
                    showEditNameDialog = false
                )
            }
        }
    }

    // ─── Change Password ──────────────────────────────────────────────────────

    fun showChangePasswordDialog() {
        _state.value = _state.value.copy(
            showChangePasswordDialog = true,
            currentPasswordText = "",
            newPasswordText = "",
            confirmPasswordText = ""
        )
    }

    fun dismissChangePasswordDialog() {
        _state.value = _state.value.copy(showChangePasswordDialog = false)
    }

    fun updateCurrentPassword(text: String) {
        _state.value = _state.value.copy(currentPasswordText = text)
    }

    fun updateNewPassword(text: String) {
        _state.value = _state.value.copy(newPasswordText = text)
    }

    fun updateConfirmPassword(text: String) {
        _state.value = _state.value.copy(confirmPasswordText = text)
    }

    fun changePassword() {
        val currentPw = _state.value.currentPasswordText
        val newPw = _state.value.newPasswordText
        val confirmPw = _state.value.confirmPasswordText

        if (currentPw.isBlank() || newPw.isBlank() || confirmPw.isBlank()) {
            _state.value = _state.value.copy(
                snackbarMessage = "All password fields are required.",
                showChangePasswordDialog = false
            )
            return
        }

        if (newPw != confirmPw) {
            _state.value = _state.value.copy(
                snackbarMessage = "New passwords do not match.",
                showChangePasswordDialog = false
            )
            return
        }

        if (newPw.length < 6) {
            _state.value = _state.value.copy(
                snackbarMessage = "Password should be at least 6 characters.",
                showChangePasswordDialog = false
            )
            return
        }

        val currentUser = auth.currentUser ?: return
        val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(
            _state.value.userEmail, currentPw
        )

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            NetworkMonitor.notifyIfUnstable()
            try {
                currentUser.reauthenticate(credential).await()
                currentUser.updatePassword(newPw).await()
                _state.value = _state.value.copy(
                    isLoading = false,
                    showChangePasswordDialog = false,
                    snackbarMessage = "Password updated successfully."
                )
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Failed to change password", e)
                val errorMsg = when {
                    e.message?.contains("password is invalid", ignoreCase = true) == true ||
                    e.message?.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) == true ->
                        "Current password is incorrect."
                    e.message?.contains("network", ignoreCase = true) == true ->
                        "Network error. Check your internet connection."
                    else -> "Failed to change password: ${e.localizedMessage ?: "Unknown error"}"
                }
                _state.value = _state.value.copy(
                    isLoading = false,
                    snackbarMessage = errorMsg,
                    showChangePasswordDialog = false
                )
            }
        }
    }

    // ─── Alert Preferences ────────────────────────────────────────────────────

    fun setWaterTankAlerts(enabled: Boolean) {
        prefs.edit().putBoolean(AlertPreferences.KEY_WATER_TANK, enabled).apply()
        _state.value = _state.value.copy(waterTankAlertsEnabled = enabled)
    }

    fun setIrrigationRainAlerts(enabled: Boolean) {
        prefs.edit().putBoolean(AlertPreferences.KEY_IRRIGATION_RAIN, enabled).apply()
        _state.value = _state.value.copy(irrigationRainAlertsEnabled = enabled)
    }

    fun setDeviceHealthAlerts(enabled: Boolean) {
        prefs.edit().putBoolean(AlertPreferences.KEY_DEVICE_HEALTH, enabled).apply()
        _state.value = _state.value.copy(deviceHealthAlertsEnabled = enabled)
    }

    fun setVibration(enabled: Boolean) {
        prefs.edit().putBoolean("vibration_enabled", enabled).apply()
        _state.value = _state.value.copy(vibrationEnabled = enabled)
        NotificationHelper.updateVibrationSetting(context, enabled)
    }

    // ─── Sign Out ─────────────────────────────────────────────────────────────

    fun signOut() {
        authViewModel.signOut()
        _state.value = _state.value.copy(
            isGoogleAccount = false,
            isEmailAccount = false,
            isGuest = false,
            userDisplayName = "",
            userEmail = "",
            userPhotoUrl = null
        )
    }

    // ─── Snackbar ─────────────────────────────────────────────────────────────

    fun clearSnackbar() {
        _state.value = _state.value.copy(snackbarMessage = null)
    }

    // ─── Storage & Cache Management ──────────────────────────────────────────

    fun refreshCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val sizeStr = calculateCacheSize()
            _state.value = _state.value.copy(cacheSizeFormatted = sizeStr)
        }
    }

    private fun calculateCacheSize(): String {
        return try {
            var size = getDirSize(context.cacheDir)
            context.externalCacheDir?.let { size += getDirSize(it) }
            formatFileSize(size)
        } catch (e: Exception) {
            "0 KB"
        }
    }

    private fun getDirSize(dir: java.io.File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var bytes = 0L
        dir.listFiles()?.forEach { file ->
            bytes += if (file.isDirectory) getDirSize(file) else file.length()
        }
        return bytes
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
            bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024f)
            else -> "$bytes B"
        }
    }

    fun clearAppCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                deleteDirContents(context.cacheDir)
                context.externalCacheDir?.let { deleteDirContents(it) }
                val newSize = calculateCacheSize()
                _state.value = _state.value.copy(
                    cacheSizeFormatted = newSize,
                    snackbarMessage = "App cache cleared successfully."
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(snackbarMessage = "Failed to clear cache.")
            }
        }
    }

    private fun deleteDirContents(dir: java.io.File?): Boolean {
        if (dir == null || !dir.exists()) return true
        var success = true
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                if (!deleteDirContents(file) || !file.delete()) success = false
            } else {
                if (!file.delete()) success = false
            }
        }
        return success
    }

    // ─── Units & Hardware Guide ──────────────────────────────────────────────

    fun setTemperatureUnit(unit: String) {
        prefs.edit().putString("temp_unit", unit).apply()
        _state.value = _state.value.copy(temperatureUnit = unit)
    }

    fun setMeasurementUnit(unit: String) {
        prefs.edit().putString("measurement_unit", unit).apply()
        _state.value = _state.value.copy(measurementUnit = unit)
    }

    fun setShowHardwareGuideDialog(show: Boolean) {
        _state.value = _state.value.copy(showHardwareGuideDialog = show)
    }

    // ─── Locale Helper ────────────────────────────────────────────────────────

    companion object {
        fun applyLocale(context: Context, lang: String) {
            val locale = when (lang) {
                "fil" -> Locale("fil")
                else -> Locale("en")
            }
            Locale.setDefault(locale)
            val config = android.content.res.Configuration(context.resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            // Update both application and Activity resources
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context.applicationContext.resources.updateConfiguration(config, context.applicationContext.resources.displayMetrics)
        }

        /**
         * Sets the app language using the Activity context.
         * Returns the updated Resources for the Activity.
         */
        fun applyAppLocale(context: android.content.Context): android.content.res.Resources {
            val prefs = context.getSharedPreferences("smartplantcare_settings", android.content.Context.MODE_PRIVATE)
            val lang = prefs.getString("language", "en") ?: "en"
            applyLocale(context, lang)
            return context.resources
        }

        fun setAppLanguage(context: Context) {
            val prefs = context.getSharedPreferences("smartplantcare_settings", Context.MODE_PRIVATE)
            val lang = prefs.getString("language", "en") ?: "en"
            applyLocale(context, lang)
        }
    }
}

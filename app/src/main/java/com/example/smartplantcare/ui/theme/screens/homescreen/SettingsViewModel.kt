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
import com.example.smartplantcare.notification.DailyAlarmScheduler
import com.example.smartplantcare.notification.NotificationHelper
import com.example.smartplantcare.notification.NotificationScheduler
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
    val morningReminder: Boolean = true,
    val eveningReminder: Boolean = true,
    val morningHour: Int = 8,
    val morningMinute: Int = 0,
    val eveningHour: Int = 18,
    val eveningMinute: Int = 0,
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
    val showMorningTimePicker: Boolean = false,
    val showEveningTimePicker: Boolean = false,
    val snackbarMessage: String? = null,
    val editNameText: String = "",
    val currentPasswordText: String = "",
    val newPasswordText: String = "",
    val confirmPasswordText: String = "",
    val isLoading: Boolean = false,
    // Treatment-specific notification settings
    val treatmentMorningEnabled: Boolean = true,
    val treatmentEveningEnabled: Boolean = true,
    val treatmentMorningHour: Int = 8,
    val treatmentMorningMinute: Int = 0,
    val treatmentEveningHour: Int = 18,
    val treatmentEveningMinute: Int = 0
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
        val morningReminder = prefs.getBoolean("morning_reminder", true)
        val eveningReminder = prefs.getBoolean("evening_reminder", true)
        val morningHour = prefs.getInt("morning_hour", 8)
        val morningMinute = prefs.getInt("morning_minute", 0)
        val eveningHour = prefs.getInt("evening_hour", 18)
        val eveningMinute = prefs.getInt("evening_minute", 0)
        val vibrationEnabled = prefs.getBoolean("vibration_enabled", true)

        val treatmentMorningEnabled = prefs.getBoolean("treatment_morning_enabled", true)
        val treatmentEveningEnabled = prefs.getBoolean("treatment_evening_enabled", true)
        val treatmentMorningHour = prefs.getInt("treatment_morning_hour", 8)
        val treatmentMorningMinute = prefs.getInt("treatment_morning_minute", 0)
        val treatmentEveningHour = prefs.getInt("treatment_evening_hour", 18)
        val treatmentEveningMinute = prefs.getInt("treatment_evening_minute", 0)

        _state.value = _state.value.copy(
            language = language,
            morningReminder = morningReminder,
            eveningReminder = eveningReminder,
            morningHour = morningHour,
            morningMinute = morningMinute,
            eveningHour = eveningHour,
            eveningMinute = eveningMinute,
            vibrationEnabled = vibrationEnabled,
            treatmentMorningEnabled = treatmentMorningEnabled,
            treatmentEveningEnabled = treatmentEveningEnabled,
            treatmentMorningHour = treatmentMorningHour,
            treatmentMorningMinute = treatmentMorningMinute,
            treatmentEveningHour = treatmentEveningHour,
            treatmentEveningMinute = treatmentEveningMinute
        )
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

    // ─── Notification Reminders ───────────────────────────────────────────────

    fun setMorningReminder(enabled: Boolean) {
        prefs.edit()
            .putBoolean("morning_reminder", enabled)
            .putBoolean("treatment_morning_enabled", enabled)
            .apply()
        _state.value = _state.value.copy(
            morningReminder = enabled,
            treatmentMorningEnabled = enabled
        )

        if (enabled) {
            scheduleReminder(isMorning = true)
        } else {
            cancelReminder(isMorning = true)
        }
        rescheduleActivePlans()
    }

    fun setEveningReminder(enabled: Boolean) {
        prefs.edit()
            .putBoolean("evening_reminder", enabled)
            .putBoolean("treatment_evening_enabled", enabled)
            .apply()
        _state.value = _state.value.copy(
            eveningReminder = enabled,
            treatmentEveningEnabled = enabled
        )

        if (enabled) {
            scheduleReminder(isMorning = false)
        } else {
            cancelReminder(isMorning = false)
        }
        rescheduleActivePlans()
    }

    fun showMorningTimePicker() {
        _state.value = _state.value.copy(showMorningTimePicker = true)
    }

    fun showEveningTimePicker() {
        _state.value = _state.value.copy(showEveningTimePicker = true)
    }

    fun dismissTimePicker() {
        _state.value = _state.value.copy(
            showMorningTimePicker = false,
            showEveningTimePicker = false
        )
    }

    fun setMorningTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt("treatment_morning_hour", hour)
            .putInt("treatment_morning_minute", minute)
            .putInt("morning_hour", hour)
            .putInt("morning_minute", minute)
            .apply()
        _state.value = _state.value.copy(
            morningHour = hour,
            morningMinute = minute,
            treatmentMorningHour = hour,
            treatmentMorningMinute = minute,
            showMorningTimePicker = false
        )
        if (_state.value.morningReminder) {
            scheduleReminder(isMorning = true)
        }
        rescheduleActivePlans()
    }

    fun setEveningTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt("treatment_evening_hour", hour)
            .putInt("treatment_evening_minute", minute)
            .putInt("evening_hour", hour)
            .putInt("evening_minute", minute)
            .apply()
        _state.value = _state.value.copy(
            eveningHour = hour,
            eveningMinute = minute,
            treatmentEveningHour = hour,
            treatmentEveningMinute = minute,
            showEveningTimePicker = false
        )
        if (_state.value.eveningReminder) {
            scheduleReminder(isMorning = false)
        }
        rescheduleActivePlans()
    }

    fun setVibration(enabled: Boolean) {
        prefs.edit().putBoolean("vibration_enabled", enabled).apply()
        _state.value = _state.value.copy(vibrationEnabled = enabled)
        // Update notification channel
        NotificationHelper.updateVibrationSetting(context, enabled)
    }

    private fun scheduleReminder(isMorning: Boolean) {
        val hour = if (isMorning) _state.value.morningHour else _state.value.eveningHour
        val minute = if (isMorning) _state.value.morningMinute else _state.value.eveningMinute
        val tag = if (isMorning) "morning_reminder" else "evening_reminder"
        val title = if (isMorning) "🌅 Good Morning!" else "🌆 Good Evening!"
        val message = if (isMorning) "Time to check on your plants and start the day's care routine."
            else "Don't forget to give your plants evening care before the night."

        viewModelScope.launch(Dispatchers.IO) {
            android.util.Log.d("SettingsViewModel", "Scheduling $tag reminder at $hour:$minute")
            DailyAlarmScheduler.schedule(
                context = context,
                tag = tag,
                hour = hour,
                minute = minute,
                title = title,
                message = message
            )
        }
    }

    private fun cancelReminder(isMorning: Boolean) {
        val tag = if (isMorning) "morning_reminder" else "evening_reminder"
        viewModelScope.launch(Dispatchers.IO) {
            DailyAlarmScheduler.cancel(context, tag)
        }
    }

    private fun rescheduleActivePlans() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                NotificationScheduler.rescheduleAllActivePlans(context)
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Failed to reschedule active plans", e)
            }
        }
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

package com.example.smartplantcare.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.example.smartplantcare.util.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel(private val context: Context? = null) : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val prefs: SharedPreferences? = context?.getSharedPreferences("smartplantcare_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        AuthUiState(isAuthenticated = auth.currentUser != null)
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    init {
        android.util.Log.d("AuthViewModel", "AuthViewModel init started")
        android.util.Log.d("AuthViewModel", "Context available: ${context != null}")
        android.util.Log.d("AuthViewModel", "Prefs available: ${prefs != null}")

        // Check for persisted guest mode
        val isGuestMode = prefs?.getBoolean("is_guest_mode", false) ?: false
        android.util.Log.d("AuthViewModel", "isGuestMode from prefs: $isGuestMode")
        android.util.Log.d("AuthViewModel", "auth.currentUser: ${auth.currentUser}")

        if (isGuestMode && auth.currentUser == null) {
            android.util.Log.d("AuthViewModel", "Restoring guest mode")
            _uiState.value = _uiState.value.copy(isAuthenticated = true, isGuest = true)
            _userProfile.value = UserProfile(
                name = "Guest User",
                email = "guest@smartplantcare.app"
            )
        }

        // Add Firebase Auth state listener to properly handle authentication persistence
        auth.addAuthStateListener { firebaseAuth ->
            val isAuthenticated = firebaseAuth.currentUser != null
            android.util.Log.d("AuthViewModel", "Auth state changed - isAuthenticated: $isAuthenticated, current isGuest: ${_uiState.value.isGuest}")

            // Only update auth state if not in guest mode, or if Firebase auth is detected
            if (!_uiState.value.isGuest || isAuthenticated) {
                _uiState.value = _uiState.value.copy(isAuthenticated = isAuthenticated, isGuest = false)
                if (isAuthenticated) {
                    // Clear guest mode when Firebase auth is detected
                    prefs?.edit()?.putBoolean("is_guest_mode", false)?.apply()
                    loadCurrentUserProfile()
                }
            }
        }

        // Initial check
        if (auth.currentUser != null) {
            android.util.Log.d("AuthViewModel", "Loading current user profile")
            loadCurrentUserProfile()
        }

        android.util.Log.d("AuthViewModel", "AuthViewModel init completed - isAuthenticated: ${_uiState.value.isAuthenticated}, isGuest: ${_uiState.value.isGuest}")
    }

    fun signInWithEmail(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Email and password are required.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            NetworkMonitor.notifyIfUnstable()
            runCatching {
                auth.signInWithEmailAndPassword(email.trim(), password).await()
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, isAuthenticated = true, isGuest = false)
                loadCurrentUserProfile()
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = mapFirebaseError(throwable)
                )
            }
        }
    }

    fun signUpWithEmail(name: String, email: String, password: String) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Name, email, and password are required."
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            NetworkMonitor.notifyIfUnstable()
            runCatching {
                val authResult = auth.createUserWithEmailAndPassword(email.trim(), password).await()
                val uid = authResult.user?.uid ?: error("Unable to get user UID")
                uid to email.trim()
            }.onSuccess { result ->
                _uiState.value = _uiState.value.copy(isLoading = false, isAuthenticated = true, isGuest = false)
                _userProfile.value = UserProfile(name = name.trim(), email = email.trim())

                // Do not block navigation when Firestore write fails.
                viewModelScope.launch {
                    runCatching {
                        saveUserToFirestore(
                            uid = result.first,
                            name = name.trim(),
                            email = result.second
                        )
                    }
                }
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = mapFirebaseError(throwable)
                )
            }
        }
    }

    fun signInWithGoogleAccount(account: GoogleSignInAccount) {
        val idToken = account.idToken
        if (idToken.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Google sign-in failed. Please try again.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            NetworkMonitor.notifyIfUnstable()
            runCatching {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(credential).await()
                authResult.user ?: error("Unable to get Google user data")
            }.onSuccess { user ->
                _uiState.value = _uiState.value.copy(isLoading = false, isAuthenticated = true, isGuest = false)
                loadCurrentUserProfile()

                // Save/update profile in background so auth success still navigates to Home.
                viewModelScope.launch {
                    runCatching {
                        saveUserToFirestore(
                            uid = user.uid,
                            name = user.displayName.orEmpty(),
                            email = user.email.orEmpty()
                        )
                    }
                }
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = mapFirebaseError(throwable)
                )
            }
        }
    }

    fun signInAsGuest() {
        android.util.Log.d("AuthViewModel", "signInAsGuest called")
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isAuthenticated = true,
            isGuest = true,
            errorMessage = null
        )
        _userProfile.value = UserProfile(
            name = "Guest User",
            email = "guest@smartplantcare.app"
        )
        // Persist guest mode
        android.util.Log.d("AuthViewModel", "Saving is_guest_mode = true to prefs")
        prefs?.edit()?.putBoolean("is_guest_mode", true)?.apply()
        android.util.Log.d("AuthViewModel", "signInAsGuest completed - isAuthenticated: ${_uiState.value.isAuthenticated}, isGuest: ${_uiState.value.isGuest}")
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun setError(message: String) {
        _uiState.value = _uiState.value.copy(errorMessage = message)
    }

    fun signOut() {
        val wasGuest = _uiState.value.isGuest
        val appContext = context?.applicationContext
        // Clear guest mode & local UI state immediately
        prefs?.edit()?.putBoolean("is_guest_mode", false)?.apply()
        _userProfile.value = UserProfile()
        _uiState.value = AuthUiState(isAuthenticated = false, isGuest = false)

        if (!wasGuest) {
            if (appContext != null) {
                viewModelScope.launch {
                    runCatching {
                        com.example.smartplantcare.notification.FcmTokenManager
                            .unregisterCurrentDeviceToken(appContext)
                    }
                    auth.signOut()
                }
            } else {
                auth.signOut()
            }
        }
    }

    fun refreshAuthState() {
        val authenticated = auth.currentUser != null
        _uiState.value = _uiState.value.copy(isAuthenticated = authenticated)
        if (authenticated) {
            loadCurrentUserProfile()
        }
    }

    private suspend fun saveUserToFirestore(uid: String, name: String, email: String) {
        val userMap = mapOf(
            "name" to name,
            "email" to email,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        firestore.collection("users")
            .document(uid)
            .set(userMap)
            .await()
    }

    private fun loadCurrentUserProfile() {
        val currentUser = auth.currentUser ?: return
        viewModelScope.launch {
            val email = currentUser.email.orEmpty()
            runCatching {
                firestore.collection("users")
                    .document(currentUser.uid)
                    .get()
                    .await()
            }.onSuccess { snapshot ->
                val savedName = snapshot.getString("name").orEmpty()
                _userProfile.value = UserProfile(
                    name = if (savedName.isBlank()) currentUser.displayName.orEmpty() else savedName,
                    email = snapshot.getString("email").orEmpty().ifBlank { email }
                )
            }.onFailure {
                _userProfile.value = UserProfile(
                    name = currentUser.displayName.orEmpty(),
                    email = email
                )
            }
        }
    }

    private fun mapFirebaseError(throwable: Throwable): String {
        val raw = throwable.message.orEmpty()
        return when {
            raw.contains("The supplied auth credential is malformed", ignoreCase = true) ->
                "Google sign-in credential is invalid. Please try again."
            raw.contains("password is invalid", ignoreCase = true) ||
                raw.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ->
                "Invalid email or password."
            raw.contains("badly formatted", ignoreCase = true) ->
                "Please enter a valid email address."
            raw.contains("network", ignoreCase = true) ->
                "Network error. Check your internet connection."
            raw.contains("email address is already in use", ignoreCase = true) ->
                "This email is already registered."
            raw.contains("Password should be at least", ignoreCase = true) ->
                "Password should be at least 6 characters."
            else -> if (raw.isBlank()) "Authentication failed. Please try again." else raw
        }
    }
}

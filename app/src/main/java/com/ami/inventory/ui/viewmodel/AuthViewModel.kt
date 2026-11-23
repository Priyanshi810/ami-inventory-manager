package com.ami.inventory.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ami.inventory.data.models.UserProfile
import com.ami.inventory.data.models.UserRole
import com.ami.inventory.data.repository.InventoryRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Authenticated(val profile: UserProfile) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(
    private val repository: InventoryRepository = InventoryRepository()
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        checkCurrentSession()
    }

    fun checkCurrentSession() {
        val user = auth.currentUser
        if (user != null) {
            viewModelScope.launch {
                _authState.value = AuthState.Loading
                val profile = repository.getUserProfile(user.uid)
                if (profile != null) {
                    _authState.value = AuthState.Authenticated(profile)
                } else {
                    val fallback = UserProfile(user.uid, user.displayName ?: "Staff", user.email ?: "", UserRole.INVENTORY)
                    repository.saveUserProfile(fallback)
                    _authState.value = AuthState.Authenticated(fallback)
                }
            }
        } else {
            _authState.value = AuthState.Idle
        }
    }

    fun signIn(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _authState.value = AuthState.Error("Please fill in both email and password")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val result = auth.signInWithEmailAndPassword(email.trim(), pass.trim()).await()
                val uid = result.user?.uid ?: throw Exception("User not found")
                val profile = repository.getUserProfile(uid)
                    ?: UserProfile(uid, "User", email.trim(), UserRole.INVENTORY)
                _authState.value = AuthState.Authenticated(profile)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.localizedMessage ?: "Sign in failed")
            }
        }
    }

    fun signUp(name: String, email: String, pass: String, role: UserRole) {
        if (name.isBlank() || email.isBlank() || pass.isBlank()) {
            _authState.value = AuthState.Error("Please fill in all fields")
            return
        }
        if (pass.length < 6) {
            _authState.value = AuthState.Error("Password must be at least 6 characters")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val result = auth.createUserWithEmailAndPassword(email.trim(), pass.trim()).await()
                val uid = result.user?.uid ?: throw Exception("User creation failed")
                val profile = UserProfile(
                    uid = uid,
                    name = name.trim(),
                    email = email.trim(),
                    role = role
                )
                repository.saveUserProfile(profile)
                _authState.value = AuthState.Authenticated(profile)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.localizedMessage ?: "Registration failed")
            }
        }
    }

    fun signOut() {
        repository.signOut()
        _authState.value = AuthState.Idle
    }
}

package com.ami.inventory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ami.inventory.ui.screens.LoginScreen
import com.ami.inventory.ui.screens.MainAppScaffold
import com.ami.inventory.ui.theme.AMIInventoryManagerTheme
import com.ami.inventory.ui.viewmodel.AuthState
import com.ami.inventory.ui.viewmodel.AuthViewModel
import com.ami.inventory.ui.viewmodel.InventoryViewModel

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val inventoryViewModel: InventoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AMIInventoryManagerTheme {
                MainAppEntry(
                    authViewModel = authViewModel,
                    inventoryViewModel = inventoryViewModel
                )
            }
        }
    }
}

@Composable
fun MainAppEntry(
    authViewModel: AuthViewModel,
    inventoryViewModel: InventoryViewModel
) {
    val authState by authViewModel.authState.collectAsState()

    when (val state = authState) {
        is AuthState.Authenticated -> {
            MainAppScaffold(
                userProfile = state.profile,
                authViewModel = authViewModel,
                inventoryViewModel = inventoryViewModel
            )
        }
        is AuthState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        else -> {
            LoginScreen(
                authViewModel = authViewModel,
                onLoginSuccess = {}
            )
        }
    }
}

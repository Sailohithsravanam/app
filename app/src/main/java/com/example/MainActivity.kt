package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.ui.FinoraaxOnboardingContainer
import com.example.ui.MainAppScreen
import com.example.ui.MyApplicationTheme
import com.example.viewmodel.FinoraaxViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize Finoraax VM via local Factory
        val viewModel = ViewModelProvider(
            this, 
            FinoraaxViewModel.Factory(applicationContext)
        )[FinoraaxViewModel::class.java]

        setContent {
            MyApplicationTheme {
                val user by viewModel.userState.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Determine if session token verified
                    val isLoggedIn = user != null && user?.sessionToken != null

                    if (isLoggedIn) {
                        MainAppScreen(
                            viewModel = viewModel,
                            onLogout = { viewModel.logOut() },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        FinoraaxOnboardingContainer(
                            user = user,
                            onOnboardCompleted = { name, email, pin ->
                                viewModel.completeProfileRegistration(name, email, pin)
                            },
                            onLoginSuccess = {
                                // Pin or biometric login successful, complete flow
                                viewModel.completeOnboardingStep("session_validated")
                                // Force login status manually by setting session token
                                viewModel.unlockVault()
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

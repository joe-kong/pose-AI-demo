package com.example.posedetection.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(navController)
        }
        
        composable("hamstring_stretch") {
            StretchDetectionScreen(navController)
        }
        
        composable("shoulder_flexion") {
            ShoulderFlexionScreen(navController)
        }
    }
} 
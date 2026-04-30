package com.example.smartplantcare.ui.theme.screens.homescreen



import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.smartplantcare.ui.theme.DarkGreen


@Composable
fun MyPlantsScreen() {
    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = "My Plants Screen",
            fontSize   = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color      = DarkGreen
        )
    }
}
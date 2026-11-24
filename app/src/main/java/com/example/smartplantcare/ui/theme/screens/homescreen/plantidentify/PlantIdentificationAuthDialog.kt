package com.example.smartplantcare.ui.theme.screens.homescreen.plantidentify

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.smartplantcare.ui.theme.screens.homescreen.HomeColors
import com.example.smartplantcare.ui.theme.screens.homescreen.HomeInterFont

@Composable
fun PlantIdentificationAuthDialog(
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = HomeColors.SurfaceWhite,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(HomeColors.SoftMint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = HomeColors.ForestPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Sign In Required",
                    fontFamily = HomeInterFont,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Plant Identification is an online feature that requires a registered account. Please sign in or create an account to continue.",
                    fontFamily = HomeInterFont,
                    fontSize = 13.sp,
                    color = HomeColors.TextSecondary,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = onSignIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HomeColors.ForestPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Sign In",
                        fontFamily = HomeInterFont,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onCreateAccount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    border = BorderStroke(1.5.dp, HomeColors.ForestPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HomeColors.ForestPrimary)
                ) {
                    Text(
                        text = "Create Account",
                        fontFamily = HomeInterFont,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "Cancel",
                        fontFamily = HomeInterFont,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = HomeColors.TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun PlantIdentificationAuthDialogPreviewContent() {
    PlantIdentificationAuthDialog(
        onSignIn = {},
        onCreateAccount = {},
        onDismiss = {}
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewPlantIdentificationAuthDialog() {
    PlantIdentificationAuthDialogPreviewContent()
}

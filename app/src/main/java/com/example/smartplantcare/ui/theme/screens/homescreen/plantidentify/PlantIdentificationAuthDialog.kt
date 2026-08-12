package com.example.smartplantcare.ui.theme.screens.homescreen.plantidentify

import android.R
import android.widget.Space
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

private val ForestGreen   = Color(0xFF166534)
private val SoftGreenBg   = Color(0xFFDCFCE7)
private val DialogSurface = Color(0xFFFFFFFF)
private val TextPrimary   = Color(0xFF0F172A)
private val TextSecondary = Color(0xFF475569)

@Composable
fun PlantIdentificationAuthDialog(
    onSignIn        : () -> Unit,
    onCreateAccount : () -> Unit,
    onDismiss       : () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ){
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = DialogSurface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Box(
                    modifier = Modifier.size(72.dp)
                        .clip(CircleShape).background(SoftGreenBg),
                        contentAlignment = Alignment.Center

                ){
                    //icon
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = ForestGreen,
                        modifier = Modifier.size(34.dp)
                    )

                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Sign In Required",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Plant Identification is an online feature that requires a " +
                            "registered account.\n\nPlease sign in or create an account to continue.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center
                    )
                Spacer(modifier = Modifier.height(28.dp))
                
                // Sign In Button (Filled)
                Button(
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ForestGreen,
                        contentColor = Color.White
                    )
                ) {
                    Text("Sign In", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Create Account Button (Outlined)
                OutlinedButton(
                    onClick = onCreateAccount,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, ForestGreen),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ForestGreen)
                ) {
                    Text("Create Account", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                // Cancel Button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
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

@Preview(
    showBackground = true,
    widthDp = 360,
    heightDp = 640
)
@Composable
fun PreviewPlantIdentificationAuthDialog() {
    MaterialTheme {
        PlantIdentificationAuthDialogPreviewContent()
    }
}





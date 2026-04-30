package com.example.smartplantcare.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartplantcare.R
import com.example.smartplantcare.ui.theme.*

@Composable
fun LoginScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onSignIn:         (String, String) -> Unit,
    onGoogleSignIn:   () -> Unit,
    onSignUp:         () -> Unit,
    onForgotPassword: () -> Unit,
    onDismissError:   () -> Unit
) {
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {

            // ── Hero image ────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                Image(
                    painter            = painterResource(id = R.drawable.login_bg),
                    contentDescription = null,
                    modifier           = Modifier.fillMaxSize(),
                    contentScale       = ContentScale.Crop
                )

                // Gradient: transparent → dark green at bottom
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f  to Color.Transparent,
                                    0.5f  to Color.Transparent,
                                    1.0f  to DarkGreen.copy(alpha = 0.88f)
                                )
                            )
                        )
                )

                // Branding
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 28.dp, bottom = 28.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text       = "🪴",
                            fontSize   = 22.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text       = "SmartPlant",
                            fontSize   = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = Color.White,
                            letterSpacing = (-0.3).sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text     = "Grow together.",
                        fontSize = 14.sp,
                        color    = Color.White.copy(alpha = 0.80f)
                    )
                }
            }

            // ── White card form — overlaps the hero ──────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-24).dp),
                shape    = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color    = Color.White,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp)
                        .padding(top = 36.dp, bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    // Drag handle
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFDDE8DD))
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Heading
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text       = "Welcome back",
                            fontSize   = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = DarkGreen,
                            letterSpacing = (-0.3).sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text     = "Sign in to monitor your garden.",
                            fontSize = 14.sp,
                            color    = TextMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // ── Email ─────────────────────────────────────────────────
                    OutlinedTextField(
                        value         = email,
                        onValueChange = { email = it },
                        modifier      = Modifier.fillMaxWidth(),
                        placeholder   = { Text("Email address", color = TextLight, fontSize = 14.sp) },
                        leadingIcon   = {
                            Icon(
                                imageVector        = Icons.Default.Email,
                                contentDescription = null,
                                tint               = IconColor,
                                modifier           = Modifier.size(20.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape           = RoundedCornerShape(14.dp),
                        singleLine      = true,
                        colors          = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor    = BorderColor,
                            focusedBorderColor      = DarkGreen,
                            unfocusedContainerColor = InputBg,
                            focusedContainerColor   = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // ── Password ──────────────────────────────────────────────
                    OutlinedTextField(
                        value         = password,
                        onValueChange = { password = it },
                        modifier      = Modifier.fillMaxWidth(),
                        placeholder   = { Text("Password", color = TextLight, fontSize = 14.sp) },
                        leadingIcon   = {
                            Icon(
                                imageVector        = Icons.Default.Lock,
                                contentDescription = null,
                                tint               = IconColor,
                                modifier           = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon  = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector        = if (passwordVisible) Icons.Default.Visibility
                                    else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint               = IconColor,
                                    modifier           = Modifier.size(20.dp)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape                = RoundedCornerShape(14.dp),
                        singleLine           = true,
                        colors               = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor    = BorderColor,
                            focusedBorderColor      = DarkGreen,
                            unfocusedContainerColor = InputBg,
                            focusedContainerColor   = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // ── Forgot password ───────────────────────────────────────
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text       = "Forgot password?",
                            fontSize   = 13.sp,
                            color      = DarkGreen,
                            fontWeight = FontWeight.SemiBold,
                            modifier   = Modifier
                                .align(Alignment.CenterEnd)
                                .clickable { onForgotPassword() }
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // ── Sign In button ────────────────────────────────────────
                    Button(
                        onClick   = { onSignIn(email, password) },
                        enabled = !isLoading,
                        modifier  = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .shadow(
                                elevation   = 10.dp,
                                shape       = RoundedCornerShape(27.dp),
                                spotColor   = DarkGreen.copy(alpha = 0.35f)
                            ),
                        shape     = RoundedCornerShape(27.dp),
                        colors    = ButtonDefaults.buttonColors(containerColor = DarkGreen),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text(
                                text          = "SIGN IN",
                                fontSize      = 15.sp,
                                fontWeight    = FontWeight.ExtraBold,
                                color         = Color.White,
                                letterSpacing = 1.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))


                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderColor)
                        Text(
                            text          = "  or continue with  ",
                            fontSize      = 12.sp,
                            color         = TextLight,
                            letterSpacing = 0.5.sp
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderColor)
                    }

                    Spacer(modifier = Modifier.height(24.dp))


                    OutlinedButton(
                        onClick  = onGoogleSignIn,
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape    = RoundedCornerShape(27.dp),
                        border   = BorderStroke(1.dp, BorderColor),
                        colors   = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor   = TextDark
                        )
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter            = painterResource(id = R.drawable.ic_google),
                                contentDescription = null,
                                modifier           = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text       = "Continue with Google",
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color      = TextDark
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    // ── Footer ────────────────────────────────────────────────
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            text     = "Don't have an account?",
                            fontSize = 14.sp,
                            color    = TextMedium
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text       = "Sign up",
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = DarkGreen,
                            modifier   = Modifier.clickable { onSignUp() }
                        )
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFFEBEE)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = errorMessage,
                                    color = Color(0xFFC62828),
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "Dismiss",
                                    color = DarkGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.clickable { onDismissError() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
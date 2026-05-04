package com.example.smartplantcare.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartplantcare.R
import com.example.smartplantcare.ui.theme.*
import com.example.smartplantcare.ui.theme.screens.SystemUiController

@SuppressLint("ContextCastToActivity")
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
    val activity = LocalContext.current as Activity
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    SideEffect {
        SystemUiController.hideSystemBars(activity)
    }

    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Animation States
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    // Smooth zooming effect for the hero image
    val imageScale by animateFloatAsState(
        targetValue = if (isVisible) 1.05f else 1.0f,
        animationSpec = tween(durationMillis = 3000, easing = LinearOutSlowInEasing),
        label = "HeroScale"
    )

    // Staggered alphas
    val heroAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "HeroAlpha"
    )

    val formAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 800, delayMillis = 200, easing = FastOutSlowInEasing),
        label = "FormAlpha"
    )

    val formOffset by animateDpAsState(
        targetValue = if (isVisible) (-24).dp else 40.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "FormOffset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // ── Hero image (With Parallax Effect) ─────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .graphicsLayer {
                        // Creates the parallax effect on scroll
                        translationY = scrollState.value * 0.4f
                        alpha = 1f - (scrollState.value / 600f).coerceIn(0f, 1f)
                    }
            ) {
                Image(
                    painter            = painterResource(id = R.drawable.login),
                    contentDescription = null, // Set to null for accessibility (decorative)
                    modifier           = Modifier
                        .fillMaxSize()
                        .graphicsLayer(scaleX = imageScale, scaleY = imageScale),
                    contentScale       = ContentScale.Crop
                )

                // Gradient Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f  to Color.Transparent,
                                    0.4f  to Color.Transparent,
                                    1.0f  to DarkGreen.copy(alpha = 0.90f)
                                )
                            )
                        )
                )

                // Animated Branding
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 28.dp, bottom = 40.dp)
                        .alpha(heroAlpha)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text       = "🪴",
                            fontSize   = 24.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text       = "SmartPlant",
                            fontSize   = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = Color.White,
                            letterSpacing = (-0.5).sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text     = "Grow together.",
                        fontSize = 15.sp,
                        color    = Color.White.copy(alpha = 0.85f),
                        letterSpacing = 0.2.sp
                    )
                }
            }

            // ── White card form — animated entrance ──────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = formOffset)
                    .alpha(formAlpha),
                shape    = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color    = Color.White,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp)
                        .padding(top = 24.dp, bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    // Drag handle
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(Color(0xFFE0EBE0))
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Heading
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text       = "Welcome back",
                            fontSize   = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = DarkGreen,
                            letterSpacing = (-0.3).sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text     = "Sign in to monitor your garden.",
                            fontSize = 15.sp,
                            color    = TextMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    // ── Email ─────────────────────────────────────────────────
                    OutlinedTextField(
                        value         = email,
                        onValueChange = { email = it },
                        modifier      = Modifier.fillMaxWidth(),
                        placeholder   = { Text("Email address", color = TextLight, fontSize = 15.sp) },
                        leadingIcon   = {
                            Icon(
                                imageVector        = Icons.Default.Email,
                                contentDescription = "Email Icon",
                                tint               = IconColor,
                                modifier           = Modifier.size(22.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next // Moves to next field on keyboard tap
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        shape           = RoundedCornerShape(16.dp),
                        singleLine      = true,
                        colors          = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor    = BorderColor,
                            focusedBorderColor      = DarkGreen,
                            unfocusedContainerColor = InputBg,
                            focusedContainerColor   = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Password ──────────────────────────────────────────────
                    OutlinedTextField(
                        value         = password,
                        onValueChange = { password = it },
                        modifier      = Modifier.fillMaxWidth(),
                        placeholder   = { Text("Password", color = TextLight, fontSize = 15.sp) },
                        leadingIcon   = {
                            Icon(
                                imageVector        = Icons.Default.Lock,
                                contentDescription = "Lock Icon",
                                tint               = IconColor,
                                modifier           = Modifier.size(22.dp)
                            )
                        },
                        trailingIcon  = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Crossfade(targetState = passwordVisible, label = "PasswordVisibility") { isVisible ->
                                    Icon(
                                        imageVector        = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle Password Visibility",
                                        tint               = IconColor,
                                        modifier           = Modifier.size(22.dp)
                                    )
                                }
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions      = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done // Finishes input
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                onSignIn(email, password)
                            }
                        ),
                        shape                = RoundedCornerShape(16.dp),
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
                            fontSize   = 14.sp,
                            color      = DarkGreen,
                            fontWeight = FontWeight.SemiBold,
                            modifier   = Modifier
                                .align(Alignment.CenterEnd)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onForgotPassword() }
                                .padding(vertical = 6.dp, horizontal = 4.dp) // Larger touch target
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // ── Sign In button ────────────────────────────────────────
                    Button(
                        onClick   = {
                            focusManager.clearFocus() // Hide keyboard on manual click
                            onSignIn(email, password)
                        },
                        enabled   = !isLoading,
                        modifier  = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(
                                elevation   = 12.dp,
                                shape       = RoundedCornerShape(28.dp),
                                spotColor   = DarkGreen.copy(alpha = 0.4f)
                            ),
                        shape     = RoundedCornerShape(28.dp),
                        colors    = ButtonDefaults.buttonColors(containerColor = DarkGreen),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        AnimatedContent(
                            targetState = isLoading,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                            },
                            label = "LoadingAnimation"
                        ) { loading ->
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp,
                                    color = Color.White
                                )
                            } else {
                                Text(
                                    text          = "SIGN IN",
                                    fontSize      = 16.sp,
                                    fontWeight    = FontWeight.ExtraBold,
                                    color         = Color.White,
                                    letterSpacing = 1.2.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderColor)
                        Text(
                            text          = "  or continue with  ",
                            fontSize      = 13.sp,
                            color         = TextLight,
                            letterSpacing = 0.5.sp
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderColor)
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    OutlinedButton(
                        onClick  = onGoogleSignIn,
                        enabled  = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape    = RoundedCornerShape(28.dp),
                        border   = BorderStroke(1.5.dp, BorderColor),
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
                                contentDescription = "Google Logo",
                                modifier           = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text       = "Continue with Google",
                                fontSize   = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color      = TextDark
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    // ── Footer ────────────────────────────────────────────────
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            text     = "Don't have an account?",
                            fontSize = 15.sp,
                            color    = TextMedium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text       = "Sign up",
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = DarkGreen,
                            modifier   = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onSignUp() }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }

                    // ── Animated Error Message ────────────────────────────────
                    AnimatedVisibility(
                        visible = !errorMessage.isNullOrBlank(),
                        enter = expandVertically(animationSpec = tween(400, easing = FastOutSlowInEasing)) + fadeIn(),
                        exit = shrinkVertically(animationSpec = tween(400, easing = FastOutSlowInEasing)) + fadeOut()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(24.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFDECEA)),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color(0xFFF5C2C7))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = errorMessage ?: "",
                                        color = Color(0xFFB3261E),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "Dismiss",
                                        color = DarkGreen,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable { onDismissError() }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
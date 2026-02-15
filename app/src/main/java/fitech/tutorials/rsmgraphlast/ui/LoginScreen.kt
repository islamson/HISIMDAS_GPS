package fitech.tutorials.rsmgraphlast.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fitech.tutorials.rsmgraphlast.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    isLoading: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val canSubmit = username.isNotBlank() && password.isNotBlank() && !isLoading

    val bgBrush = remember {
        Brush.horizontalGradient(
            colorStops = arrayOf(
                0.00f to Color(0xFF797979).copy(alpha = 0.75f),
                0.36f to Color(0xFF4B5A74).copy(alpha = 0.91f),
                1.00f to Color(0xFF0D234A)
            ),
            startX = 0f,
            endX = Float.POSITIVE_INFINITY
        )
    }

    val cardColor = Color.White.copy(alpha = 0.08f)
    val fieldShape = RoundedCornerShape(18.dp)

    val tfColors = TextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        disabledTextColor = Color.White.copy(alpha = 0.6f),
        cursorColor = Color.White,
        focusedIndicatorColor = Color.White.copy(alpha = 0.85f),
        unfocusedIndicatorColor = Color.White.copy(alpha = 0.55f),
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedLeadingIconColor = Color.White,
        unfocusedLeadingIconColor = Color.White.copy(alpha = 0.85f),
        focusedTrailingIconColor = Color.White,
        unfocusedTrailingIconColor = Color.White.copy(alpha = 0.85f)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .statusBarsPadding()
            .padding(horizontal = 28.dp, vertical = 5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_train),
                    contentDescription = "Train",
                    tint = Color.White,
                    modifier = Modifier.size(60.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Sürüş Asistanına Hoşgeldiniz",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = "Lütfen size verilen giriş bilgilerinizle giriş yapınız",
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 13.sp
            )

            Spacer(Modifier.height(5.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(cardColor)
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Username",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(5.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    singleLine = true,
                    placeholder = { Text("Username", color = Color.White.copy(alpha = 0.45f)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_user),
                            contentDescription = "User",
                            tint = Color.White.copy(alpha = 0.90f)
                        )
                    },
                    shape = fieldShape,
                    colors = tfColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Password",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(5.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    placeholder = { Text("Password", color = Color.White.copy(alpha = 0.45f)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_lock),
                            contentDescription = "Lock",
                            tint = Color.White.copy(alpha = 0.90f)
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                painter = painterResource(
                                    if (showPassword) R.drawable.ic_eye_off else R.drawable.ic_eye
                                ),
                                contentDescription = "Toggle password",
                                tint = Color.White.copy(alpha = 0.90f)
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    shape = fieldShape,
                    colors = tfColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                )

                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = error,
                        color = Color(0xFFFF6B6B),
                        fontSize = 15.sp
                    )
                }

                Spacer(Modifier.height(10.dp))

                // === Gradient Login Button (enabled/disabled + loading) ===
                val buttonAlpha = if (canSubmit) 1f else 0.45f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            brush = if (canSubmit) {
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF001B5E), Color(0xFF0A45FF))
                                )
                            } else {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF001B5E).copy(alpha = 0.4f),
                                        Color(0xFF0A45FF).copy(alpha = 0.4f)
                                    )
                                )
                            }
                        )
                        .clickable(
                            enabled = canSubmit,
                            role = Role.Button
                        ) {
                            onLogin(username, password)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(targetState = isLoading, label = "login_loading") { loading ->
                        if (loading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.5.dp,
                                    color = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Giriş Yapılıyor",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        } else {
                            Text(
                                text = "Giriş Yap",
                                color = if (canSubmit) Color.White else Color.White.copy(alpha = 0.55f),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

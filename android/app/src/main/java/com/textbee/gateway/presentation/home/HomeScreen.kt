package com.textbee.gateway.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.vernu.sms.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.textbee.gateway.presentation.theme.*

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToServerConfig: () -> Unit,
    onNavigateToConfiguracao: () -> Unit = {},
    onGetStartedClick: () -> Unit,
    onReconnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top row with status pill and quick server button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gateway Ready badge
            Surface(
                shape = CircleShape,
                color = Color(0xFFFEF3C7),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Text(
                        text = "Gateway Ready",
                        color = Color(0xFF78350F),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Quick Servidor button
            Surface(
                shape = RoundedCornerShape(9999.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, HairlineOutline.copy(alpha = 0.5f)),
                modifier = Modifier.clickable { onNavigateToServerConfig() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "Servidor",
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Servidor",
                        color = OnSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        // Hero Graphic Circular Plate
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, HairlineOutline.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Logo do textbee (o mesmo do dashboard anterior, splash e boas-vindas)
            Image(
                painter = painterResource(id = R.drawable.ic_app_logo),
                contentDescription = "textbee",
                modifier = Modifier.size(72.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Brand Title
        Text(
            text = "textbee",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryOrange,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Subtitle
        Text(
            text = "Turn your Android into a\nprogrammatic SMS gateway",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF2E3A47),
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 3 Simple Steps
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StepItem(
                number = "1.",
                text = "Create a free account at textbee.dev",
                boldPart = "textbee.dev"
            )
            StepItem(
                number = "2.",
                text = "Connect this phone as your SMS gateway"
            )
            StepItem(
                number = "3.",
                text = "Send SMS via API from any app or automation"
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        // Primary Button: Get Started
        Button(
            onClick = onGetStartedClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryOrange,
                contentColor = OnPrimary
            ),
            shape = RoundedCornerShape(9999.dp)
        ) {
            Text(
                text = "Get Started",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Secondary Button: Reconnect a device
        OutlinedButton(
            onClick = onReconnectClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                contentColor = PrimaryOrange
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, HairlineOutline.copy(alpha = 0.7f)),
            shape = RoundedCornerShape(9999.dp)
        ) {
            Text(
                text = "Reconnect a device",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Special Card: Configurar Servidor (Self-Hosted)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToServerConfig() },
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFFFFDF5),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFEF08A))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Server icon tile
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFFEDD5)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = PrimaryOrange,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Configurar Servid...",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = YellowBadgeBg
                        ) {
                            Text(
                                text = "SELF-HOSTED",
                                color = YellowBadgeText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Defina IP e porta offline antes de parear",
                        fontSize = 12.sp,
                        color = OnSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Acessar",
                    tint = OnSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Card: Configuração (módulo da Home)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToConfiguracao() },
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, HairlineOutline.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFFEDD5)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = PrimaryOrange,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Configuração",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Servidor, filtros e saúde do dispositivo",
                        fontSize = 12.sp,
                        color = OnSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Acessar",
                    tint = OnSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Footer links
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Don't have an account? ",
                fontSize = 13.sp,
                color = OnSurfaceVariant
            )
            Text(
                text = "Sign up free",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryOrange
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "textbee.dev",
            fontSize = 13.sp,
            color = OnSurfaceVariant.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun StepItem(
    number: String,
    text: String,
    boldPart: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = number,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = PrimaryOrange,
            modifier = Modifier.width(24.dp)
        )
        Text(
            text = text,
            fontSize = 14.sp,
            color = Color(0xFF374151),
            lineHeight = 20.sp
        )
    }
}

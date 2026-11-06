package com.textbee.gateway.presentation.configuracao

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.textbee.gateway.presentation.theme.HairlineOutline
import com.textbee.gateway.presentation.theme.OnSurface
import com.textbee.gateway.presentation.theme.OnSurfaceVariant
import com.textbee.gateway.presentation.theme.PrimaryOrange
import com.textbee.gateway.presentation.theme.SurfaceBackground
import com.textbee.gateway.presentation.theme.YellowBadgeBg
import com.textbee.gateway.presentation.theme.YellowBadgeText

/**
 * Tela "Configuração" — módulo da Home.
 *
 * Funciona como um hub que agrupa as configurações do app. O servidor local
 * (self-hosted) sempre aparece; filtros de SMS e saúde do dispositivo só
 * aparecem quando quem hospeda a tela fornece o destino (callback não nulo).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracaoScreen(
    onNavigateBack: () -> Unit,
    onNavigateToServerConfig: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSmsFilters: (() -> Unit)? = null,
    onNavigateToDeviceHealth: (() -> Unit)? = null
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Configuração",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = OnSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceBackground)
            )
        },
        containerColor = SurfaceBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Ajustes do gateway",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface
            )
            Text(
                text = "Escolha o que deseja configurar. Cada item abre sua própria tela.",
                fontSize = 14.sp,
                color = OnSurfaceVariant,
                lineHeight = 20.sp
            )

            ConfigItem(
                icon = Icons.Default.Dns,
                title = "Servidor",
                badge = "SELF-HOSTED",
                subtitle = "IP, porta, token e modo offline na rede local",
                onClick = onNavigateToServerConfig
            )

            onNavigateToSmsFilters?.let {
                ConfigItem(
                    icon = Icons.Default.FilterAlt,
                    title = "Filtros de SMS",
                    subtitle = "Defina quais remetentes são permitidos ou bloqueados",
                    onClick = it
                )
            }

            onNavigateToDeviceHealth?.let {
                ConfigItem(
                    icon = Icons.Default.MonitorHeart,
                    title = "Saúde do dispositivo",
                    subtitle = "Permissões, bateria e status do envio de SMS",
                    onClick = it
                )
            }
        }
    }
}

@Composable
private fun ConfigItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    badge: String? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, HairlineOutline.copy(alpha = 0.4f))
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = PrimaryOrange,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                    if (badge != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(4.dp), color = YellowBadgeBg) {
                            Text(
                                text = badge,
                                color = YellowBadgeText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = OnSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = OnSurfaceVariant
            )
        }
    }
}

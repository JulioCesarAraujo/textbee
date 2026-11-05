package com.textbee.gateway.presentation.server

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.textbee.gateway.domain.model.ConnectionTestResult
import com.textbee.gateway.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerConfigScreen(
    viewModel: ServerConfigViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isProtocolDropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Configuracao Servidor ...",
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
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(PrimaryOrange),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Perfil",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceBackground
                )
            )
        },
        containerColor = SurfaceBackground
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Badges row: OPEN SOURCE & v2.4.0 Core
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(9999.dp),
                    color = TechTealContainer
                ) {
                    Text(
                        text = "OPEN SOURCE",
                        color = TechTealOnContainer,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(9999.dp),
                    color = SurfaceContainerHighest
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen)
                        )
                        Text(
                            text = "v2.4.0 Core",
                            color = OnSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Headline & Description
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Modo Offline (Self-Hosted)",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
                Text(
                    text = "Sincronize despachos SMS diretamente com seu servidor local na rede interna (LAN), com privacidade total e latência zero em nuvem.",
                    fontSize = 14.sp,
                    color = OnSurfaceVariant,
                    lineHeight = 20.sp
                )
            }

            // Card 1: Servidor Local Privado
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, HairlineOutline.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFFFEDD5)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = null,
                                    tint = PrimaryOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Servidor Local Privado",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = OnSurface
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (uiState.config.isPrivateServerEnabled) SuccessGreen else Color.Gray)
                                    )
                                    Text(
                                        text = if (uiState.config.isPrivateServerEnabled) "Conexão Direta Ativa" else "Desativado",
                                        fontSize = 12.sp,
                                        color = if (uiState.config.isPrivateServerEnabled) SuccessGreen else OnSurfaceVariant
                                    )
                                }
                            }
                        }

                        Switch(
                            checked = uiState.config.isPrivateServerEnabled,
                            onCheckedChange = { viewModel.onPrivateServerToggled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PrimaryOrange
                            )
                        )
                    }

                    // Shield note
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = TechTeal,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "As requisições REST contornam o cluster Textbee Cloud. O tráfego permanece estritamente encapsulado na rede corporativa ou residencial.",
                                fontSize = 12.sp,
                                color = OnSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Card 2: Parâmetros de Rede
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, HairlineOutline.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SyncAlt,
                                contentDescription = null,
                                tint = OnSurface,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Parâmetros de Rede",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = OnSurface
                            )
                        }
                        Text(
                            text = "REST API",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = OnSurfaceVariant
                        )
                    }

                    // Host Field
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Endereço Host / Domínio Local",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = OnSurface
                            )
                            Text(
                                text = "IPv4 ou mDNS",
                                fontSize = 11.sp,
                                color = OnSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Protocol selector
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SurfaceContainer,
                                modifier = Modifier.clickable { isProtocolDropdownExpanded = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = uiState.config.protocol,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null
                                    )
                                }
                                DropdownMenu(
                                    expanded = isProtocolDropdownExpanded,
                                    onDismissRequest = { isProtocolDropdownExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("http://") },
                                        onClick = {
                                            viewModel.onProtocolChanged("http://")
                                            isProtocolDropdownExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("https://") },
                                        onClick = {
                                            viewModel.onProtocolChanged("https://")
                                            isProtocolDropdownExpanded = false
                                        }
                                    )
                                }
                            }

                            // Host text field
                            OutlinedTextField(
                                value = uiState.config.host,
                                onValueChange = { viewModel.onHostChanged(it) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = HairlineOutline.copy(alpha = 0.5f),
                                    focusedBorderColor = PrimaryOrange
                                )
                            )
                        }
                        Text(
                            text = "Exemplo: gateway.lan ou endereço IP estático fixo.",
                            fontSize = 11.sp,
                            color = OnSurfaceVariant
                        )
                    }

                    // Port Field
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Porta do Serviço",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = OnSurface
                            )
                            Text(
                                text = "Port TCP",
                                fontSize = 11.sp,
                                color = OnSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = uiState.config.port,
                            onValueChange = { viewModel.onPortChanged(it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = {
                                Text("#", fontWeight = FontWeight.Bold, color = OnSurfaceVariant)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = HairlineOutline.copy(alpha = 0.5f),
                                focusedBorderColor = PrimaryOrange
                            )
                        )
                        Text(
                            text = "Porta padrão sugerida pelo daemon Textbee: 8080 ou 3000.",
                            fontSize = 11.sp,
                            color = OnSurfaceVariant
                        )
                    }

                    // API Token Field
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Chave de Acesso / API Token",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = OnSurface
                            )
                            Text(
                                text = "Opcional",
                                fontSize = 11.sp,
                                color = OnSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = uiState.config.apiToken,
                            onValueChange = { viewModel.onApiTokenChanged(it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = OnSurfaceVariant)
                            },
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Alternar visibilidade",
                                        tint = OnSurfaceVariant
                                    )
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = HairlineOutline.copy(alpha = 0.5f),
                                focusedBorderColor = PrimaryOrange
                            )
                        )
                    }

                    // Test Connection Button
                    OutlinedButton(
                        onClick = { viewModel.testConnection() },
                        enabled = !uiState.isTestingConnection,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = SurfaceContainer
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, HairlineOutline.copy(alpha = 0.5f))
                    ) {
                        if (uiState.isTestingConnection) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = PrimaryOrange)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testando conexão...", color = OnSurface)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = PrimaryOrange,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Testar Conexão Local",
                                fontWeight = FontWeight.Medium,
                                color = OnSurface
                            )
                        }
                    }

                    // Connection Result Banner
                    uiState.testResult?.let { result ->
                        when (result) {
                            is ConnectionTestResult.Success -> {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFD1FAE5)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = SuccessGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Conexão bem-sucedida (${result.latencyMs}ms)",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = SuccessGreen
                                            )
                                            Text(
                                                text = "Textbee Engine ${result.engineVersion} • Endpoint ${result.endpoint} • ${result.message}",
                                                fontSize = 11.sp,
                                                color = Color(0xFF065F46)
                                            )
                                        }
                                    }
                                }
                            }
                            is ConnectionTestResult.Error -> {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFFE4E6)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Error,
                                            contentDescription = null,
                                            tint = Color(0xFFE11D48),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Falha ao conectar no host",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color(0xFFE11D48)
                                            )
                                            Text(
                                                text = result.message,
                                                fontSize = 11.sp,
                                                color = Color(0xFF9F1239)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Card 3: Políticas de Fallback e Buffer
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, HairlineOutline.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = OnSurface)
                        Text(
                            text = "Políticas de Fallback e Buffer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = OnSurface
                        )
                    }

                    // Worker foreground switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Worker em Segundo Plano",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = OnSurface
                            )
                            Text(
                                text = "Mantém serviço Android foreground ouvindo mensagens locais",
                                fontSize = 12.sp,
                                color = OnSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.config.isWorkerForegroundEnabled,
                            onCheckedChange = { viewModel.onWorkerForegroundToggled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PrimaryOrange
                            )
                        )
                    }

                    HorizontalDivider(color = HairlineOutline.copy(alpha = 0.3f))

                    // SQLite buffer switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Fila Offline em SQLite",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = OnSurface
                            )
                            Text(
                                text = "Armazena SMS no aparelho até reestabelecer conexão de rede",
                                fontSize = 12.sp,
                                color = OnSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.config.isSqliteBufferEnabled,
                            onCheckedChange = { viewModel.onSqliteBufferToggled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PrimaryOrange
                            )
                        )
                    }
                }
            }

            // Card 4: Dica de Configuração Wi-Fi
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF0FDF4).copy(alpha = 0.7f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF5EEAD4)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = Color(0xFF0F766E),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Dica de Configuração Wi-Fi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = OnSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Verifique se o dispositivo está conectado no mesmo SSID/VLAN do host e desative o recurso de isolamento AP no roteador para permitir descoberta local.",
                            fontSize = 12.sp,
                            color = OnSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Save Button
            Button(
                onClick = { viewModel.saveConfig() },
                enabled = !uiState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryOrange,
                    contentColor = OnPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.isSaving) "Salvando..." else "Salvar e Aplicar Configurações",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            // Restore defaults
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Restaurar Padrão • Textbee Cloud Oficial",
                    fontSize = 12.sp,
                    color = OnSurfaceVariant,
                    modifier = Modifier
                        .clickable { viewModel.resetDefaults() }
                        .padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

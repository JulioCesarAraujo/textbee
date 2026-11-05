package com.textbee.gateway.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * As telas do Textbee Gateway usam uma paleta clara fixa (superfícies brancas e
 * cores quentes definidas em Color.kt), então o tema é sempre claro, mesmo
 * quando o sistema está em modo escuro. Isso evita texto claro sobre fundo claro.
 */
private val TextbeeLightColors = lightColorScheme(
    primary = PrimaryOrange,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryOrangeContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = TechTeal,
    secondaryContainer = TechTealContainer,
    onSecondaryContainer = TechTealOnContainer,
    background = SurfaceBackground,
    onBackground = OnSurface,
    surface = SurfaceBackground,
    onSurface = OnSurface,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = OnSurfaceVariant,
    outline = OutlineMuted,
    outlineVariant = HairlineOutline
)

@Composable
fun TextbeeGatewayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TextbeeLightColors,
        content = content
    )
}

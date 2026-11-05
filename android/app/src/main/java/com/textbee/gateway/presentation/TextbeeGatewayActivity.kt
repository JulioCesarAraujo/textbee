package com.textbee.gateway.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.textbee.gateway.presentation.navigation.TextbeeNavGraph
import com.textbee.gateway.presentation.theme.TextbeeGatewayTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity independente para facilitar a integração:
 * Para abrir essas telas a partir de qualquer lugar no seu app existente, basta chamar:
 * 
 * val intent = Intent(context, TextbeeGatewayActivity::class.java)
 * context.startActivity(intent)
 */
@AndroidEntryPoint
class TextbeeGatewayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TextbeeGatewayTheme {
                TextbeeNavGraph()
            }
        }
    }
}

# Textbee Gateway - Guia de Integração Jetpack Compose & Clean Architecture

Este pacote contém as telas nativas Android e a camada Clean Architecture para o Textbee SMS Gateway.

## Estrutura de Pacotes:
- domain/ (Modelos, Repositório e Casos de Uso puros)
- data/ (DataStore de Preferências e Retrofit Client)
- presentation/ (Telas Compose Material 3: HomeScreen e ServerConfigScreen)
- di/ (Módulos Dagger Hilt)
- worker/ (Serviço de Segundo Plano Foreground)

## Pré-requisitos:
- Kotlin 2.0+
- Jetpack Compose BOM 2024.10+
- Dagger Hilt 2.51+
- DataStore 1.1+

# Modo offline: estado da implementação

Referência: `plano.txt` na raiz. Atualizado em 2026-11-09.

| Fase | Estado |
|---|---|
| 0. Preparação | ✅ branch `feature/offline-mode` |
| 1. Canal local no servidor (NestJS) | ✅ |
| 2. Canal local no Android | ✅ |
| 3. Fila local e idempotência (Room + WorkManager) | ✅ |
| 4. Modo offline manual e sincronização | ✅ |
| 5. Testes, bateria e entrega | ⏳ pendente (nenhum teste automatizado do modo offline ainda) |
| 6. Modo offline por configuração | ✅ |

Os builds passam (`pnpm build` na API e `./gradlew :app:assembleDevDebug` no Android), mas o fluxo ainda não foi testado de ponta a ponta com um aparelho real.

## Backend (`api/src/gateway/offline/`)

| Arquivo | Plano | O que faz |
|---|---|---|
| `local-gateway.ts` | 1.1, 1.2 | WebSocket em `/ws?deviceId=<id>`, com autenticação pela API key (header `x-api-key`). Mantém o mapa `deviceId → socket`, faz ping a cada 30 s e reenvia `SEND_SMS` sem ACK. Um `RESULT` atualiza a operação e o SMS do dashboard, com webhook e contadores do lote. |
| `protocol.ts` | 1.3 | `SEND_SMS`, `ACK`, `RESULT` e `RESULT_ACK`. |
| `delivery-channel.ts`, `delivery-channel.service.ts`, `mode-manager.ts` | 1.4, 6.4 | Interface `DeliveryChannel` com `FcmChannel` e `LocalWsChannel`. O `ModeManager` escolhe o canal; se o FCM estiver inacessível, o envio vai para o WebSocket local. |
| `operation.schema.ts` | 1.5, 4.4 | Cada SMS é gravado antes da entrega. O índice único no UUID garante a idempotência. |
| `sync-manager.ts`, `offline.controller.ts` | 4.3–4.5 | Envia as operações para a nuvem em ordem e marca SYNCED só após a confirmação. Na nuvem, `already_processed` conta como sucesso. Só uma sincronização roda por vez. |
| `offline.config.ts`, `auth/auth.service.ts` | 6.1, 6.2 | Variáveis `OFFLINE_MODE_*`. Cadastro e login sem verificação de e-mail no modo offline. |

## Android (`android/app/src/main/java/com/vernu/sms/`)

| Arquivo | Plano | O que faz |
|---|---|---|
| `offline/MessageChannel.kt` | 2.1 | Interface `connect`, `disconnect`, `onMessage` e `send`. |
| `offline/LocalChannel.kt` | 2.2, 6.4 | WebSocket OkHttp com reconexão e backoff exponencial de 1 s a 60 s, com variação aleatória. Com o fallback ligado, alterna entre o servidor offline e o servidor local. |
| `offline/LocalChannelService.kt` | 2.3 | Foreground service (`connectedDevice`), ativo só no modo offline. |
| `offline/OfflineOperationProcessor.kt` | 2.4, 2.6, 3.3, 3.4 | Grava no Room, responde ACK e só depois põe o envio na fila do `SmsSendWorker`. UUID repetido devolve o resultado anterior. Resultados sem confirmação ficam no Room até o `RESULT_ACK`. Falha por falta de sinal é tentada de novo até 5 vezes, com intervalo de 30 s a 15 min. |
| `offline/OperationEntity.kt`, `OperationDao.kt`, `OfflineDatabase.kt` | 3.1, 3.2 | Tabela `operations` e as operações `insertIfAbsent`, `getPending` (em ordem), `markSent` e `markSynced`. |
| `offline/OfflineRetryWorker.kt` | 2.6 | Nova tentativa agendada no WorkManager. |
| `offline/OfflineModeController.kt` | 4.1, 4.2 | O switch liga e desliga a conexão. Não há detecção automática de rede. |
| `managers/NetworkManager.kt`, `OfflineModeManager.kt` | 6.3 | Fonte única das configurações. Escolhe o servidor da API e do WebSocket. |
| `ApiManager.java`, `ApiManagerKt.kt` | 6.3 | O Retrofit segue o servidor escolhido. |
| `ui/settings/*`, `com/textbee/gateway/*` | 2.5, 4.1, 6.3 | Switch de modo offline, URL do servidor, checkbox de e-mail, fallback e tela "Servidor local" (IP, porta e token). |

## Como ativar

1. No `api/.env` do servidor local, defina `OFFLINE_MODE_ENABLED=true` e reinicie a API. Confira em `GET /api/v1/gateway/offline/status`: `localGateway.enabled` deve ser `true`.
2. No app, abra Configurações → Offline Mode, ligue o switch e informe a URL do servidor, por exemplo `http://192.168.0.10:3001`. Outra opção é preencher a tela "Servidor local" e salvar.
3. O app abre `ws://<servidor>/ws` e mostra uma notificação fixa com o estado da conexão.
4. A nuvem só recebe o que o servidor local enviou depois que `OFFLINE_SYNC_TARGET_URL` e `OFFLINE_SYNC_API_KEY` estiverem configurados no servidor local.

## Limitações conhecidas

- O app não faz login nem cadastro de usuário (usa API key), então não tem `authFlags` para enviar. Quem pula a verificação de e-mail é o servidor, quando `OFFLINE_MODE_ENABLED=true`. O checkbox do app fica só como preferência.
- Na tela "Servidor local", os switches "worker em primeiro plano" e "buffer SQLite" não têm efeito. O serviço em primeiro plano e a fila Room estão sempre ativos no modo offline.
- Uma mensagem que falhou por falta de sinal volta para o fim da fila, depois das mensagens criadas após ela.
- Fase 5 (testes unitários e de integração, resiliência e bateria): não feita.

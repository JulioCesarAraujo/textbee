// Offline mode settings (plan phase 6.1). Read from the environment on every
// call so a restart is enough to change them and tests can set process.env.

export type DeliveryMode = 'fcm' | 'local' | 'auto'

const isTrue = (value: string | undefined, fallback: boolean): boolean => {
  if (value === undefined || value.trim() === '') return fallback
  return value.trim().toLowerCase() === 'true'
}

const intOr = (value: string | undefined, fallback: number): number => {
  const parsed = Number.parseInt(value ?? '', 10)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

export function offlineConfig() {
  const enabled = isTrue(process.env.OFFLINE_MODE_ENABLED, false)
  const rawMode = (process.env.DELIVERY_MODE ?? '').trim().toLowerCase()
  const deliveryMode: DeliveryMode =
    rawMode === 'fcm' || rawMode === 'local' || rawMode === 'auto'
      ? rawMode
      : enabled
        ? 'auto'
        : 'fcm'

  return {
    // OFFLINE_MODE_ENABLED: master switch for the offline features
    enabled,
    // OFFLINE_MODE_API_URL: public address of this offline server, reported to the app
    apiUrl: (process.env.OFFLINE_MODE_API_URL ?? '').trim(),
    // OFFLINE_MODE_SKIP_EMAIL_VERIFICATION: registering does not need the verification email
    skipEmailVerification: isTrue(
      process.env.OFFLINE_MODE_SKIP_EMAIL_VERIFICATION,
      true,
    ),
    // OFFLINE_MODE_LOCAL_WS_FALLBACK: when FCM (the remote push service) is
    // unavailable, hand the message to the local WebSocket channel instead
    localWsFallback: isTrue(process.env.OFFLINE_MODE_LOCAL_WS_FALLBACK, true),
    // fcm = always push, local = always local WebSocket, auto = local when the
    // phone is connected to this server, FCM otherwise
    deliveryMode,
    // The WebSocket server runs whenever something can route to it
    localGatewayEnabled: enabled || deliveryMode !== 'fcm',
    localGatewayPath: (process.env.LOCAL_GATEWAY_PATH ?? '/ws').trim() || '/ws',
    // How long a SEND_SMS may stay without ACK before it is sent again
    ackTimeoutMs: intOr(process.env.LOCAL_GATEWAY_ACK_TIMEOUT_MS, 60_000),
    // Cloud the local server syncs its operations to (phase 4.3)
    syncTargetUrl: (process.env.OFFLINE_SYNC_TARGET_URL ?? '').trim(),
    syncApiKey: (process.env.OFFLINE_SYNC_API_KEY ?? '').trim(),
    syncIntervalMs: intOr(process.env.OFFLINE_SYNC_INTERVAL_MS, 60_000),
    syncBatchSize: intOr(process.env.OFFLINE_SYNC_BATCH_SIZE, 50),
  }
}

export type OfflineConfig = ReturnType<typeof offlineConfig>

import { Injectable } from '@nestjs/common'
import { isFirebaseReady, DeliveryTarget } from './delivery-channel'
import { LocalGateway } from './local-gateway'
import { offlineConfig } from './offline.config'

// FCM error codes that mean the push service could not be reached, as opposed
// to a problem with one device's token
const UNREACHABLE_CODES = new Set([
  'app/network-error',
  'app/network-timeout',
  'app/fcm-not-configured',
  'messaging/unavailable',
  'messaging/internal-error',
  'messaging/server-unavailable',
])

export function isFcmUnreachable(error: unknown): boolean {
  const code = String((error as { code?: unknown })?.code ?? '')
  return UNREACHABLE_CODES.has(code)
}

// Decides which channel a send uses (plan phases 1.4 and 6.4)
@Injectable()
export class ModeManager {
  constructor(private readonly gateway: LocalGateway) {}

  resolve(device: DeliveryTarget): 'fcm' | 'local' {
    const config = offlineConfig()
    if (config.deliveryMode === 'fcm') return 'fcm'
    if (config.deliveryMode === 'local') return 'local'

    // auto: a phone connected to this server is in offline mode
    if (device?._id && this.gateway.isConnected(String(device._id))) return 'local'
    if (config.localWsFallback && (!device?.fcmToken || !isFirebaseReady())) {
      return 'local'
    }
    return 'fcm'
  }

  // Whether messages FCM could not take may go to the local channel instead
  canFallBack(): boolean {
    const config = offlineConfig()
    return config.localWsFallback && config.localGatewayEnabled
  }
}

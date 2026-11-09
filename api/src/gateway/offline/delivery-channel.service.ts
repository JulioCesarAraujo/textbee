import { Injectable, Logger } from '@nestjs/common'
import { BatchResponse, Message } from 'firebase-admin/messaging'
import { DeliveryTarget, FcmChannel, LocalWsChannel } from './delivery-channel'
import { isFcmUnreachable, ModeManager } from './mode-manager'

// Single entry point the send paths call instead of firebase-admin directly.
// It picks the channel through ModeManager and, when the push service is
// unreachable, moves the affected messages to the local WebSocket channel.
@Injectable()
export class DeliveryChannelService {
  private readonly logger = new Logger(DeliveryChannelService.name)

  constructor(
    private readonly modeManager: ModeManager,
    private readonly fcmChannel: FcmChannel,
    private readonly localChannel: LocalWsChannel,
  ) {}

  async dispatch(device: DeliveryTarget, messages: Message[]): Promise<BatchResponse> {
    if (messages.length === 0) {
      return { responses: [], successCount: 0, failureCount: 0 }
    }

    if (this.modeManager.resolve(device) === 'local') {
      return this.localChannel.send(device, messages)
    }

    let response: BatchResponse
    try {
      response = await this.fcmChannel.send(device, messages)
    } catch (error) {
      if (!this.modeManager.canFallBack() || !isFcmUnreachable(error)) throw error
      this.logger.warn(
        `FCM unavailable (${error.message}); using the local WebSocket channel`,
      )
      return this.localChannel.send(device, messages)
    }

    if (!this.modeManager.canFallBack()) return response

    // sendEach resolves even when Google cannot be reached, with one error per
    // message, so the unreachable ones are picked out here
    const retryIndexes = response.responses
      .map((r, i) => (!r.success && isFcmUnreachable(r.error) ? i : -1))
      .filter((i) => i >= 0)
    if (retryIndexes.length === 0) return response

    this.logger.warn(
      `FCM unreachable for ${retryIndexes.length} message(s); using the local WebSocket channel`,
    )
    const local = await this.localChannel.send(
      device,
      retryIndexes.map((i) => messages[i]),
    )
    const responses = [...response.responses]
    retryIndexes.forEach((original, j) => {
      responses[original] = local.responses[j]
    })
    return {
      responses,
      successCount: responses.filter((r) => r.success).length,
      failureCount: responses.filter((r) => !r.success).length,
    }
  }
}

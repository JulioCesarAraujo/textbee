import { Injectable, Logger } from '@nestjs/common'
import { InjectModel } from '@nestjs/mongoose'
import * as firebaseAdmin from 'firebase-admin'
import { BatchResponse, Message } from 'firebase-admin/messaging'
import { Model, Types } from 'mongoose'
import { randomUUID } from 'crypto'
import { LocalGateway } from './local-gateway'
import { Operation, OperationDocument } from './operation.schema'

// The device fields the channels need
export interface DeliveryTarget {
  _id?: Types.ObjectId | string
  user?: unknown
  fcmToken?: string
}

// Where a batch of SMS pushes goes (plan phase 1.4). Both channels take the
// FCM messages the send path already builds, so the rest of that path does
// not change with the channel.
export interface DeliveryChannel {
  readonly name: 'fcm' | 'local'
  send(device: DeliveryTarget, messages: Message[]): Promise<BatchResponse>
}

export function isFirebaseReady(): boolean {
  return firebaseAdmin.apps.length > 0
}

export class FcmUnavailableError extends Error {
  readonly code = 'app/fcm-not-configured'
  constructor() {
    super('FCM is not configured on this server')
  }
}

@Injectable()
export class FcmChannel implements DeliveryChannel {
  readonly name = 'fcm' as const

  async send(_device: DeliveryTarget, messages: Message[]): Promise<BatchResponse> {
    if (!isFirebaseReady()) throw new FcmUnavailableError()
    return firebaseAdmin.messaging().sendEach(messages)
  }
}

interface SmsData {
  smsId?: unknown
  smsBatchId?: unknown
  message?: unknown
  recipients?: unknown
  simSubscriptionId?: unknown
}

function readSmsData(message: Message): SmsData | null {
  const raw = message?.data?.smsData
  if (typeof raw !== 'string') return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

// Stores each SMS as an operation before anything is sent (plan phase 1.5),
// then hands what it can to the phone's socket. A phone that is not connected
// gets the operations, oldest first, when it connects.
@Injectable()
export class LocalWsChannel implements DeliveryChannel {
  readonly name = 'local' as const
  private readonly logger = new Logger(LocalWsChannel.name)

  constructor(
    private readonly gateway: LocalGateway,
    @InjectModel(Operation.name)
    private readonly operationModel: Model<OperationDocument>,
  ) {}

  async send(device: DeliveryTarget, messages: Message[]): Promise<BatchResponse> {
    const deviceId = String(device._id)
    const userId = device.user
      ? String((device.user as { _id?: unknown })?._id ?? device.user)
      : undefined

    const responses: BatchResponse['responses'] = []
    const operations: Partial<Operation>[] = []
    const base = Date.now()

    messages.forEach((message, index) => {
      const data = readSmsData(message)
      const recipient = Array.isArray(data?.recipients) ? data.recipients[0] : undefined
      if (!data || typeof data.message !== 'string' || typeof recipient !== 'string') {
        responses.push({
          success: false,
          error: Object.assign(new Error('Message has no SMS payload'), {
            code: 'local/invalid-payload',
          }) as any,
        })
        return
      }
      const operationId = randomUUID()
      operations.push({
        operationId,
        origin: 'local',
        deviceId,
        ...(userId && Types.ObjectId.isValid(userId) && {
          user: new Types.ObjectId(userId),
        }),
        ...(data.smsId !== undefined && { smsId: String(data.smsId) }),
        ...(data.smsBatchId !== undefined && { smsBatchId: String(data.smsBatchId) }),
        recipient,
        message: data.message,
        ...(typeof data.simSubscriptionId === 'number' && {
          simSubscriptionId: data.simSubscriptionId,
        }),
        sendStatus: 'PENDING',
        syncStatus: 'PENDING',
        attempts: 0,
        // A millisecond apart, so the creation order survives equal clocks
        operationCreatedAt: new Date(base + index),
      })
      responses.push({ success: true, messageId: operationId })
    })

    if (operations.length > 0) {
      await this.operationModel.insertMany(operations, { ordered: true })
      const delivered = await this.gateway.flushPending(deviceId)
      if (delivered === 0 && !this.gateway.isConnected(deviceId)) {
        this.logger.log(
          `Device ${deviceId} is not connected; ${operations.length} operation(s) wait for it`,
        )
      }
    }

    return {
      responses,
      successCount: responses.filter((r) => r.success).length,
      failureCount: responses.filter((r) => !r.success).length,
    }
  }
}

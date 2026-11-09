import {
  Injectable,
  Logger,
  OnModuleDestroy,
  OnModuleInit,
} from '@nestjs/common'
import { InjectModel } from '@nestjs/mongoose'
import axios from 'axios'
import { Model, Types } from 'mongoose'
import { offlineConfig } from './offline.config'
import {
  Operation,
  OperationDocument,
  OPERATION_SEND_STATUSES,
  OperationSendStatus,
} from './operation.schema'
import { isUuid } from './protocol'

export interface SyncResult {
  success: boolean
  syncedCount: number
  message: string
}

// What one local server sends to the cloud for one operation
export interface SyncedOperationInput {
  id: string
  deviceId: string
  recipient: string
  message: string
  sendStatus: OperationSendStatus
  smsId?: string
  smsBatchId?: string
  simSubscriptionId?: number
  error?: string
  createdAt: string
}

export type SyncItemStatus = 'processed' | 'already_processed' | 'rejected'

export interface SyncItemResult {
  id: string
  status: SyncItemStatus
  reason?: string
}

export const MAX_SYNC_BATCH = 500

// Later states win, so an older copy never overwrites a newer one
const STATUS_RANK: Record<OperationSendStatus, number> = {
  PENDING: 0,
  ACKED: 1,
  SENT: 2,
  FAILED: 2,
}

function isDuplicateKey(error: unknown): boolean {
  return (error as { code?: number })?.code === 11000
}

// Both ends of the sync (plan phase 4):
//  - on the local server, sync() sends the operations the cloud has not
//    confirmed yet, oldest first, and marks each SYNCED only after the cloud
//    answers processed or already_processed (4.3). Only one sync runs at a
//    time (4.5).
//  - on the cloud, receive() stores what a local server sent. The unique
//    index on operationId makes a repeat an already_processed answer, which
//    the sender counts as success (4.4).
@Injectable()
export class SyncManager implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(SyncManager.name)
  private running: Promise<SyncResult> | null = null
  private timer?: NodeJS.Timeout

  constructor(
    @InjectModel(Operation.name)
    private readonly operationModel: Model<OperationDocument>,
  ) {}

  onModuleInit() {
    const config = offlineConfig()
    if (!this.isConfigured()) return
    this.timer = setInterval(() => {
      this.sync().catch((error) => this.logger.error('Scheduled sync failed', error))
    }, config.syncIntervalMs)
    this.logger.log(
      `Syncing operations to ${config.syncTargetUrl} every ${config.syncIntervalMs} ms`,
    )
  }

  onModuleDestroy() {
    if (this.timer) clearInterval(this.timer)
  }

  isConfigured(): boolean {
    const config = offlineConfig()
    return !!config.syncTargetUrl && !!config.syncApiKey
  }

  isSyncing(): boolean {
    return this.running !== null
  }

  // A call made while a sync runs joins that sync instead of starting another
  sync(): Promise<SyncResult> {
    if (this.running) return this.running
    this.running = this.runSync().finally(() => {
      this.running = null
    })
    return this.running
  }

  private async runSync(): Promise<SyncResult> {
    if (!this.isConfigured()) {
      return {
        success: false,
        syncedCount: 0,
        message: 'OFFLINE_SYNC_TARGET_URL and OFFLINE_SYNC_API_KEY are not set',
      }
    }
    const config = offlineConfig()
    const endpoint = `${config.syncTargetUrl.replace(/\/+$/, '')}/gateway/operations/sync`
    let syncedCount = 0

    // Batches until nothing is left or the cloud stops confirming
    for (;;) {
      const batch = await this.operationModel
        .find({ origin: 'local', syncStatus: 'PENDING' })
        .sort({ operationCreatedAt: 1, _id: 1 })
        .limit(Math.min(config.syncBatchSize, MAX_SYNC_BATCH))
        .exec()
      if (batch.length === 0) {
        return { success: true, syncedCount, message: `Synced ${syncedCount} operation(s)` }
      }

      let results: SyncItemResult[]
      try {
        const response = await axios.post(
          endpoint,
          { operations: batch.map((op) => this.toInput(op)) },
          {
            headers: { 'x-api-key': config.syncApiKey },
            timeout: 30_000,
          },
        )
        results = response.data?.data?.results
        if (!Array.isArray(results)) throw new Error('Unexpected sync response')
      } catch (error) {
        this.logger.warn(`Sync stopped: ${error.message}`)
        return {
          success: false,
          syncedCount,
          message: `Sync stopped after ${syncedCount} operation(s): ${error.message}`,
        }
      }

      const byId = new Map(results.map((r) => [r.id, r]))
      for (const operation of batch) {
        const result = byId.get(operation.operationId)
        if (result?.status !== 'processed' && result?.status !== 'already_processed') {
          // Order matters: nothing after an unconfirmed operation is marked
          return {
            success: false,
            syncedCount,
            message: `Cloud did not confirm operation ${operation.operationId}${
              result?.reason ? `: ${result.reason}` : ''
            }`,
          }
        }
        // Matching on the status that was sent: if the phone reported a new
        // state meanwhile, the operation stays PENDING and goes in the next sync
        const marked = await this.operationModel
          .updateOne(
            {
              operationId: operation.operationId,
              syncStatus: 'PENDING',
              sendStatus: operation.sendStatus,
            },
            { $set: { syncStatus: 'SYNCED', syncedAt: new Date() } },
          )
          .exec()
        if (marked.modifiedCount > 0) syncedCount++
      }
    }
  }

  private toInput(op: Operation): SyncedOperationInput {
    return {
      id: op.operationId,
      deviceId: op.deviceId,
      recipient: op.recipient,
      message: op.message,
      sendStatus: op.sendStatus,
      ...(op.smsId && { smsId: op.smsId }),
      ...(op.smsBatchId && { smsBatchId: op.smsBatchId }),
      ...(typeof op.simSubscriptionId === 'number' && {
        simSubscriptionId: op.simSubscriptionId,
      }),
      ...(op.error && { error: op.error }),
      createdAt: new Date(op.operationCreatedAt ?? op.createdAt ?? Date.now()).toISOString(),
    }
  }

  // Cloud side: stores operations from a local server, one answer per input
  async receive(userId: string, inputs: unknown[]): Promise<SyncItemResult[]> {
    const results: SyncItemResult[] = []
    for (const input of inputs) {
      results.push(await this.receiveOne(userId, input))
    }
    return results
  }

  private async receiveOne(userId: string, input: any): Promise<SyncItemResult> {
    const id = input?.id
    if (!isUuid(id)) return { id: String(id ?? ''), status: 'rejected', reason: 'id must be a UUID' }
    if (typeof input.recipient !== 'string' || typeof input.message !== 'string') {
      return { id, status: 'rejected', reason: 'recipient and message are required' }
    }
    if (!OPERATION_SEND_STATUSES.includes(input.sendStatus)) {
      return { id, status: 'rejected', reason: 'unknown sendStatus' }
    }
    const createdAt = new Date(input.createdAt)
    const sendStatus = input.sendStatus as OperationSendStatus

    try {
      await this.operationModel.create({
        operationId: id,
        origin: 'synced',
        ...(Types.ObjectId.isValid(userId) && { user: new Types.ObjectId(userId) }),
        deviceId: String(input.deviceId ?? ''),
        recipient: input.recipient,
        message: input.message,
        sendStatus,
        syncStatus: 'SYNCED',
        syncedAt: new Date(),
        ...(typeof input.smsId === 'string' && { smsId: input.smsId }),
        ...(typeof input.smsBatchId === 'string' && { smsBatchId: input.smsBatchId }),
        ...(typeof input.simSubscriptionId === 'number' && {
          simSubscriptionId: input.simSubscriptionId,
        }),
        ...(typeof input.error === 'string' && { error: input.error.slice(0, 500) }),
        operationCreatedAt: Number.isNaN(createdAt.getTime()) ? new Date() : createdAt,
      })
      return { id, status: 'processed' }
    } catch (error) {
      if (!isDuplicateKey(error)) throw error
    }

    // Seen before: keep the copy current, never move it backwards
    const existing = await this.operationModel.findOne({ operationId: id }).exec()
    if (
      existing &&
      String(existing.user ?? '') === String(userId) &&
      STATUS_RANK[sendStatus] > STATUS_RANK[existing.sendStatus]
    ) {
      await this.operationModel
        .updateOne(
          { operationId: id },
          {
            $set: {
              sendStatus,
              syncedAt: new Date(),
              ...(typeof input.error === 'string' && { error: input.error.slice(0, 500) }),
            },
          },
        )
        .exec()
    }
    return { id, status: 'already_processed' }
  }
}

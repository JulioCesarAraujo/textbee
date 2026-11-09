import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose'
import { Document, SchemaTypes, Types } from 'mongoose'

export type OperationDocument = Operation & Document

// PENDING: stored, not acknowledged by the phone yet
// ACKED: the phone stored it in its own queue
// SENT / FAILED: final result reported by the phone
export const OPERATION_SEND_STATUSES = ['PENDING', 'ACKED', 'SENT', 'FAILED'] as const
export type OperationSendStatus = (typeof OPERATION_SEND_STATUSES)[number]

// PENDING: not yet confirmed by the cloud. SYNCED: the cloud confirmed it
// (processed or already_processed). Operations received from a local server
// are stored as SYNCED, since they are already the cloud copy.
export const OPERATION_SYNC_STATUSES = ['PENDING', 'SYNCED'] as const
export type OperationSyncStatus = (typeof OPERATION_SYNC_STATUSES)[number]

// Every SMS handed to the local WebSocket channel is stored here first
// (plan phase 1.5), so a phone that is offline gets it when it reconnects.
@Schema({ timestamps: true, collection: 'operations' })
export class Operation {
  _id?: Types.ObjectId

  // UUID sent to the phone and to the cloud. The unique index is the
  // idempotency key (plan phase 4.4).
  @Prop({ type: String, required: true, unique: true })
  operationId: string

  @Prop({ type: String, enum: ['local', 'synced'], default: 'local' })
  origin: 'local' | 'synced'

  @Prop({ type: SchemaTypes.ObjectId, index: true })
  user: Types.ObjectId

  // Device on this server; for synced copies, the device on the local server
  @Prop({ type: String, required: true })
  deviceId: string

  @Prop({ type: String })
  smsId?: string

  @Prop({ type: String })
  smsBatchId?: string

  @Prop({ type: String, required: true })
  recipient: string

  @Prop({ type: String, required: true })
  message: string

  @Prop({ type: Number })
  simSubscriptionId?: number

  @Prop({ type: String, enum: OPERATION_SEND_STATUSES, default: 'PENDING' })
  sendStatus: OperationSendStatus

  @Prop({ type: String, enum: OPERATION_SYNC_STATUSES, default: 'PENDING' })
  syncStatus: OperationSyncStatus

  // Times the SEND_SMS frame was written to the phone's socket
  @Prop({ type: Number, default: 0 })
  attempts: number

  @Prop({ type: Date })
  lastAttemptAt?: Date

  @Prop({ type: Date })
  ackedAt?: Date

  @Prop({ type: Date })
  resultAt?: Date

  @Prop({ type: String })
  error?: string

  @Prop({ type: Date })
  syncedAt?: Date

  // When the operation was created on the server that first stored it. For
  // synced copies this is the local server's time, not the arrival time.
  @Prop({ type: Date, default: () => new Date() })
  operationCreatedAt: Date

  createdAt?: Date
  updatedAt?: Date
}

export const OperationSchema = SchemaFactory.createForClass(Operation)

// Redelivery to a device, in creation order
OperationSchema.index({ deviceId: 1, sendStatus: 1, operationCreatedAt: 1 })
// Sync to the cloud, in creation order
OperationSchema.index({ origin: 1, syncStatus: 1, operationCreatedAt: 1 })

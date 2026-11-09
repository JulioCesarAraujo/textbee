import {
  Injectable,
  Logger,
  OnApplicationBootstrap,
  OnModuleDestroy,
} from '@nestjs/common'
import { HttpAdapterHost, ModuleRef } from '@nestjs/core'
import { InjectModel } from '@nestjs/mongoose'
import type { IncomingMessage, Server } from 'http'
import type { Duplex } from 'stream'
import { Model, Types } from 'mongoose'
import { WebSocket, WebSocketServer } from 'ws'
import { AuthService } from '../../auth/auth.service'
import { Device, DeviceDocument } from '../schemas/device.schema'
import { SMS } from '../schemas/sms.schema'
// Usado só em tempo de execução (dentro de método), então o ciclo
// gateway.service -> delivery-channel -> local-gateway não causa problema
import { GatewayService } from '../gateway.service'
import { offlineConfig } from './offline.config'
import { Operation, OperationDocument } from './operation.schema'
import { parseClientFrame, ResultAckFrame, ResultFrame, SendSmsFrame } from './protocol'

const PING_INTERVAL_MS = 30_000
const REDELIVERY_INTERVAL_MS = 15_000

// WebSocket channel between this server and the phones (plan phase 1).
//
// The phone connects to ws(s)://<server>/ws?deviceId=<id> with its API key in
// the x-api-key header (or ?apiKey=). The handshake is refused unless the key
// is valid and the device belongs to the key's account (1.1). The gateway keeps
// one socket per device and notices disconnects through close events and a
// ping every 30 seconds (1.2).
@Injectable()
export class LocalGateway implements OnApplicationBootstrap, OnModuleDestroy {
  private readonly logger = new Logger(LocalGateway.name)
  private wss?: WebSocketServer
  private readonly sockets = new Map<string, WebSocket>()
  private readonly alive = new WeakMap<WebSocket, boolean>()
  private readonly timers: NodeJS.Timeout[] = []
  private redelivering = false

  constructor(
    private readonly httpAdapterHost: HttpAdapterHost,
    private readonly authService: AuthService,
    @InjectModel(Device.name) private readonly deviceModel: Model<DeviceDocument>,
    @InjectModel(Operation.name)
    private readonly operationModel: Model<OperationDocument>,
    @InjectModel(SMS.name) private readonly smsModel: Model<SMS>,
    private readonly moduleRef: ModuleRef,
  ) {}

  onApplicationBootstrap() {
    if (!offlineConfig().localGatewayEnabled) return
    const server = this.httpAdapterHost?.httpAdapter?.getHttpServer?.()
    if (!server) {
      this.logger.warn('No HTTP server available, local gateway not started')
      return
    }
    this.attach(server)
  }

  onModuleDestroy() {
    this.timers.forEach((t) => clearInterval(t))
    this.timers.length = 0
    for (const socket of this.sockets.values()) {
      socket.close(1001, 'Server shutting down')
    }
    this.sockets.clear()
    this.wss?.close()
  }

  attach(server: Server) {
    const path = offlineConfig().localGatewayPath
    this.wss = new WebSocketServer({ noServer: true })

    server.on('upgrade', (req: IncomingMessage, socket: Duplex, head: Buffer) => {
      const pathname = new URL(req.url ?? '/', 'http://localhost').pathname
      if (pathname !== path) {
        socket.destroy()
        return
      }
      this.authenticate(req)
        .then((deviceId) => {
          if (!deviceId) {
            socket.write('HTTP/1.1 401 Unauthorized\r\nConnection: close\r\n\r\n')
            socket.destroy()
            return
          }
          this.wss.handleUpgrade(req, socket, head, (ws) =>
            this.onConnection(deviceId, ws),
          )
        })
        .catch((error) => {
          this.logger.error('Local gateway handshake failed', error)
          socket.destroy()
        })
    })

    this.timers.push(setInterval(() => this.pingAll(), PING_INTERVAL_MS))
    this.timers.push(
      setInterval(() => {
        this.redeliverStale().catch((error) =>
          this.logger.error('Redelivery failed', error),
        )
      }, REDELIVERY_INTERVAL_MS),
    )
    this.logger.log(`Local gateway listening for WebSocket upgrades on ${path}`)
  }

  // Returns the device id when the API key may act for that device
  async authenticate(req: IncomingMessage): Promise<string | null> {
    const url = new URL(req.url ?? '/', 'http://localhost')
    const deviceId = url.searchParams.get('deviceId')
    const headerKey = req.headers['x-api-key']
    const apiKey =
      (Array.isArray(headerKey) ? headerKey[0] : headerKey) ||
      url.searchParams.get('apiKey')

    if (!deviceId || !Types.ObjectId.isValid(deviceId) || !apiKey) return null

    const key = await this.authService.verifyApiKey(String(apiKey))
    if (!key) return null

    const device = await this.deviceModel.findById(deviceId).exec()
    if (!device || !device.enabled) return null
    if (String(device.user) !== String(key.user)) return null

    return deviceId
  }

  onConnection(deviceId: string, ws: WebSocket) {
    const previous = this.sockets.get(deviceId)
    if (previous && previous !== ws) {
      previous.close(4000, 'Replaced by a newer connection')
    }
    this.sockets.set(deviceId, ws)
    this.alive.set(ws, true)
    this.logger.log(`Device ${deviceId} connected to the local gateway`)

    ws.on('pong', () => this.alive.set(ws, true))
    ws.on('message', (data) => {
      this.handleFrame(deviceId, data.toString()).catch((error) =>
        this.logger.error(`Failed to handle frame from ${deviceId}`, error),
      )
    })
    ws.on('close', () => {
      if (this.sockets.get(deviceId) === ws) {
        this.sockets.delete(deviceId)
        this.logger.log(`Device ${deviceId} disconnected from the local gateway`)
      }
    })
    ws.on('error', (error) =>
      this.logger.warn(`Socket error for device ${deviceId}: ${error.message}`),
    )

    // Whatever waited while the phone was away goes out now, oldest first
    this.flushPending(deviceId).catch((error) =>
      this.logger.error(`Failed to flush pending operations for ${deviceId}`, error),
    )
  }

  isConnected(deviceId: string): boolean {
    return this.sockets.get(String(deviceId))?.readyState === WebSocket.OPEN
  }

  connectedDevices(): string[] {
    return [...this.sockets.entries()]
      .filter(([, ws]) => ws.readyState === WebSocket.OPEN)
      .map(([id]) => id)
  }

  // Writes one SEND_SMS frame. Returns false when the device is not connected.
  async deliver(operation: Operation): Promise<boolean> {
    const ws = this.sockets.get(operation.deviceId)
    if (!ws || ws.readyState !== WebSocket.OPEN) return false

    const frame: SendSmsFrame = {
      type: 'SEND_SMS',
      id: operation.operationId,
      to: operation.recipient,
      message: operation.message,
      ...(operation.smsId && { smsId: operation.smsId }),
      ...(operation.smsBatchId && { smsBatchId: operation.smsBatchId }),
      ...(operation.simSubscriptionId !== undefined &&
        operation.simSubscriptionId !== null && {
          simSubscriptionId: operation.simSubscriptionId,
        }),
      createdAt: new Date(operation.operationCreatedAt ?? Date.now()).toISOString(),
    }

    try {
      ws.send(JSON.stringify(frame))
    } catch (error) {
      this.logger.warn(`Failed to write SEND_SMS ${frame.id}: ${error.message}`)
      return false
    }

    await this.operationModel
      .updateOne(
        { operationId: operation.operationId },
        { $inc: { attempts: 1 }, $set: { lastAttemptAt: new Date() } },
      )
      .exec()
    return true
  }

  // Sends every unacknowledged operation of the device, in creation order
  async flushPending(deviceId: string): Promise<number> {
    const pending = await this.operationModel
      .find({ deviceId, origin: 'local', sendStatus: 'PENDING' })
      .sort({ operationCreatedAt: 1, _id: 1 })
      .exec()

    let delivered = 0
    for (const operation of pending) {
      if (!(await this.deliver(operation))) break
      delivered++
    }
    return delivered
  }

  // SEND_SMS frames can be lost with a socket that dies before the ACK. Those
  // go out again; the phone answers a repeated id without sending twice.
  async redeliverStale(now = new Date()): Promise<void> {
    if (this.redelivering) return
    this.redelivering = true
    try {
      const cutoff = new Date(now.getTime() - offlineConfig().ackTimeoutMs)
      for (const deviceId of this.connectedDevices()) {
        const stale = await this.operationModel
          .find({
            deviceId,
            origin: 'local',
            sendStatus: 'PENDING',
            $or: [{ lastAttemptAt: null }, { lastAttemptAt: { $lte: cutoff } }],
          })
          .sort({ operationCreatedAt: 1, _id: 1 })
          .exec()
        for (const operation of stale) {
          if (!(await this.deliver(operation))) break
        }
      }
    } finally {
      this.redelivering = false
    }
  }

  async handleFrame(deviceId: string, raw: string): Promise<void> {
    const frame = parseClientFrame(raw)
    if (!frame) {
      this.logger.warn(`Ignoring malformed frame from device ${deviceId}`)
      return
    }

    const now = new Date()
    if (frame.type === 'ACK') {
      // Only the first ACK moves the state; a late ACK never undoes a RESULT
      await this.operationModel
        .updateOne(
          { operationId: frame.id, deviceId, sendStatus: 'PENDING' },
          { $set: { sendStatus: 'ACKED', ackedAt: now, syncStatus: 'PENDING' } },
        )
        .exec()
      return
    }

    const operation = await this.operationModel
      .findOneAndUpdate(
        { operationId: frame.id, deviceId },
        {
          $set: {
            sendStatus: frame.status,
            resultAt: now,
            syncStatus: 'PENDING',
            ...(frame.error && { error: frame.error }),
          },
        },
      )
      .exec()

    if (operation?.smsId) {
      await this.applyResultToSms(deviceId, operation, frame).catch((error) =>
        this.logger.warn(
          `Failed to update SMS ${operation.smsId} from RESULT ${frame.id}: ${error.message}`,
        ),
      )
    }

    // Confirmed even for an unknown id, so the phone stops repeating it
    const ack: ResultAckFrame = { type: 'RESULT_ACK', id: frame.id }
    this.send(deviceId, ack)
  }

  // Moves the dashboard message to sent/failed through the same path as the
  // HTTP status report, so webhooks and batch counters stay right. A repeated
  // RESULT (the phone resends until RESULT_ACK) changes nothing.
  private async applyResultToSms(
    deviceId: string,
    operation: Operation,
    frame: ResultFrame,
  ): Promise<void> {
    if (!Types.ObjectId.isValid(operation.smsId)) return
    const sms = await this.smsModel.findById(operation.smsId).select('status').lean().exec()
    if (!sms) return

    const target = frame.status === 'SENT' ? 'sent' : 'failed'
    const current = String((sms as { status?: string }).status ?? '').toLowerCase()
    // Delivered is later than sent; a report already applied is not repeated
    if (current === target || current === 'delivered') return

    const gatewayService = this.moduleRef.get<GatewayService>(GatewayService, {
      strict: false,
    })
    const at = Date.now()
    await gatewayService.updateSMSStatus(deviceId, {
      smsId: operation.smsId,
      smsBatchId: operation.smsBatchId ?? '',
      status: target,
      ...(target === 'sent'
        ? { sentAtInMillis: at }
        : {
            failedAtInMillis: at,
            errorCode: 'local/device-failed',
            errorMessage: frame.error || 'The phone could not send the SMS',
          }),
    })
  }

  private send(deviceId: string, frame: object) {
    const ws = this.sockets.get(deviceId)
    if (ws?.readyState === WebSocket.OPEN) ws.send(JSON.stringify(frame))
  }

  private pingAll() {
    for (const [deviceId, ws] of this.sockets.entries()) {
      if (this.alive.get(ws) === false) {
        this.logger.log(`Device ${deviceId} stopped answering pings, closing`)
        ws.terminate()
        this.sockets.delete(deviceId)
        continue
      }
      this.alive.set(ws, false)
      try {
        ws.ping()
      } catch {
        // close handler cleans up
      }
    }
  }
}

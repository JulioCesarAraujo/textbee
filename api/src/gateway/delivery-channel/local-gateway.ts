// LocalGateway WebSocket server implementation for offline mode (Phase 1)
// Handles device authentication, connection management, and WebSocket protocol

import WebSocket, { Server as WebSocketServer } from 'ws'
import { randomUUID } from 'crypto'
import { deliveryChannelConfig as config } from '../delivery-channel.config'
import { Device, DeviceDocument } from '../schemas/device.schema'

export interface LocalGatewayMessage {
  type: 'SEND_SMS' | 'ACK' | 'RESULT'
  id: string
  to?: string
  message?: string
  status?: 'sent' | 'failed' | 'queued'
  timestamp?: Date
}

export interface WebSocketDeviceConnection {
  deviceId: string
  socket: WebSocket
  subscriptions: Set<string>
  lastSeen: Date
}

export class LocalGateway {
  private wss: WebSocketServer
  private connections = new Map<string, WebSocketDeviceConnection>()
  private deviceModel: typeof Device

  constructor(deviceModel: typeof Device) {
    this.deviceModel = deviceModel
    this.wss = new WebSocketServer({
      port: Number.parseInt(config.localServerUrl.split(':')[2]) || 8080,
    })
    
    this.setupEventHandlers()
  }

  private setupEventHandlers(): void {
    this.wss.on('connection', (socket: WebSocket, request: any) => {
      this.handleConnection(socket, request)
    })

    this.wss.on('listening', () => {
      console.log(`LocalGateway WebSocket server listening on port ${this.wss.options.port}`)
    })

    this.wss.on('error', (error: Error) => {
      console.error('LocalGateway WebSocket server error:', error)
    })
  }

  async start(): Promise<void> {
    return new Promise((resolve, reject) => {
      this.wss.on('listening', () => {
        console.log(`LocalGateway WebSocket server started successfully on port ${this.wss.options.port}`)
        resolve()
      })
      
      this.wss.on('error', (error: Error) => {
        console.error('Failed to start LocalGateway WebSocket server:', error)
        reject(error)
      })
      
      // Start the server
      this.wss.listen(this.wss.options.port || 8080, () => {
        console.log(`LocalGateway WebSocket server listening on port ${this.wss.options.port}`)
      })
    })
  }

  private async handleConnection(socket: WebSocket, request: any): Promise<void> {
    const deviceId = request.url?.includes('deviceId=') 
      ? request.url.split('deviceId=')[1].split('&')[0]
      : null

    if (!deviceId) {
      console.log('Connection rejected: deviceId not provided')
      socket.close(1008, 'Device ID required for authentication')
      return
    }

    // Authenticate device
    const device = await this.authenticateDevice(deviceId)
    if (!device) {
      console.log(`Connection rejected: device ${deviceId} not found or not enabled`) 
      socket.close(1008, 'Invalid or disabled device')
      return
    }

    // Register connection
    const connection: WebSocketDeviceConnection = {
      deviceId,
      socket,
      subscriptions: new Set(),
      lastSeen: new Date(),
    }

    this.connections.set(deviceId, connection)
    console.log(`Device ${deviceId} authenticated and connected`) 

    // Send connection confirmation to device
    this.sendMessage(deviceId, {
      type: 'ACK',
      id: randomUUID(),
      timestamp: new Date(),
      status: 'connected',
    })

    // Handle socket events
    socket.on('message', (data: Buffer) => {
      this.handleMessage(deviceId, data)
    })

    socket.on('close', (code: number, reason: string) => {
      this.handleDisconnection(deviceId, code, reason)
    })

    socket.on('error', (error: Error) => {
      console.error(`WebSocket error for device ${deviceId}:`, error)
      this.handleDisconnection(deviceId, 1006, error.message)
    })

    // Keep connection alive
    socket.on('pong', () => {
      const connection = this.connections.get(deviceId)
      if (connection) {
        connection.lastSeen = new Date()
      }
    })
  }

  private async authenticateDevice(deviceId: string): Promise<DeviceDocument | null> {
    try {
      const device = await this.deviceModel.findById(deviceId).exec()
      if (!device) {
        return null
      }

      if (!device.enabled) {
        console.log(`Device ${deviceId} is not enabled`) 
        return null
      }

      if (device.fcmToken && config.useFCMWhenAvailable) {
        console.log(`Device ${deviceId} prefers FCM delivery, rejecting WebSocket connection`) 
        return null
      }

      // Update last seen
      device.lastSeen = new Date()
      await device.save()

      return device
    } catch (error) {
      console.error(`Error authenticating device ${deviceId}:`, error)
      return null
    }
  }

  private handleMessage(deviceId: string, data: Buffer): void {
    try {
      const message = JSON.parse(data.toString()) as LocalGatewayMessage
      console.log(`Received message from device ${deviceId}:`, message)

      const connection = this.connections.get(deviceId)
      if (!connection) {
        console.log(`Message received for unknown device ${deviceId}`)
        return
      }

      connection.lastSeen = new Date()
      connection.subscriptions.add(message.id)

      // Process different message types
      switch (message.type) {
        case 'ACK':
          // Acknowledge receipt of SEND_SMS
          this.handleAcknowledgement(deviceId, message)
          break
        case 'RESULT':
          // Process delivery result
          this.handleDeliveryResult(deviceId, message)
          break
        default:
          console.log(`Unknown message type from device ${deviceId}: ${message.type}`)
      }
    } catch (error) {
      console.error(`Error parsing message from device ${deviceId}:`, error)
    }
  }

  private handleAcknowledgement(deviceId: string, message: LocalGatewayMessage): void {
    console.log(`Message ${message.id} acknowledged by device ${deviceId}`)
    // Update delivery status in database if needed
    // This confirms that the device received and processed the SEND_SMS
  }

  private handleDeliveryResult(deviceId: string, message: LocalGatewayMessage): void {
    if (!message.status) {
      console.log(`Delivery result missing status for message ${message.id} from device ${deviceId}`)
      return
    }

    console.log(`Delivery result for message ${message.id} from device ${deviceId}: ${message.status}`)
    // Update delivery status in database
    // This tracks successful/failed message delivery
  }

  sendMessage(deviceId: string, message: LocalGatewayMessage): boolean {
    const connection = this.connections.get(deviceId)
    if (!connection || !connection.socket.readyState || connection.socket.readyState !== WebSocket.OPEN) {
      console.log(`Cannot send message to device ${deviceId}: not connected`) 
      return false
    }

    try {
      const messageString = JSON.stringify(message)
      connection.socket.send(messageString)
      console.log(`Sent message ${message.id} to device ${deviceId}`)
      return true
    } catch (error) {
      console.error(`Failed to send message to device ${deviceId}:`, error)
      return false
    }
  }

  isDeviceConnected(deviceId: string): boolean {
    const connection = this.connections.get(deviceId)
    return connection !== undefined && connection.socket.readyState === WebSocket.OPEN
  }

  getConnectionState(deviceId: string): {
    connected: boolean
    connecting: boolean
    lastSeen?: Date
    lastError?: string
  } {
    const connection = this.connections.get(deviceId)
    if (!connection) {
      return { connected: false, connecting: false }
    }

    return {
      connected: connection.socket.readyState === WebSocket.OPEN,
      connecting: connection.socket.readyState === WebSocket.CONNECTING,
      lastSeen: connection.lastSeen,
    }
  }

  async disconnectDevice(deviceId: string): Promise<void> {
    const connection = this.connections.get(deviceId)
    if (connection) {
      connection.socket.close(1000, 'Server initiated disconnect')
      this.connections.delete(deviceId)
      console.log(`Disconnected device ${deviceId} from WebSocket")
    }
  }

  broadcastToAllDevices(message: LocalGatewayMessage): void {
    for (const [deviceId, connection] of this.connections) {
      if (connection.socket.readyState === WebSocket.OPEN) {
        this.sendMessage(deviceId, message)
      }
    }
  }

  getConnectedDevices(): string[] {
    return Array.from(this.connections.keys())
  }

  async cleanup(): Promise<void> {
    for (const [deviceId, connection] of this.connections) {
      connection.socket.close(1000, 'Server shutdown')
    }
    this.connections.clear()
    console.log('LocalGateway cleanup completed')
  }

  getServerStatus(): {
    port: number
    host: string
    connectedDevices: number
    totalConnections: number
    uptime: Date
  } {
    return {
      port: this.wss.options.port,
      host: this.wss.options.host || 'localhost',
      connectedDevices: this.connections.size,
      totalConnections: this.connections.size, // TODO: Track total connections over time
      uptime: this.wss.options?.uptime || new Date(),
    }
  }
}

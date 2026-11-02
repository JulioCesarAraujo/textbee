// local-websocket-channel.ts
import WebSocket from 'ws';
import { DeliveryChannel, ConnectionState, SMSResult } from './delivery-channel';
import { deliveryChannelConfig as config } from '../delivery-channel.config';

export class LocalWebSocketChannel implements DeliveryChannel {
  private connections = new Map<string, LocalWebSocketConnection>();
  private messageQueue = new Map<string, any[]>(); // deviceId -> queued messages
  private connectionStates = new Map<string, ConnectionState>();

  constructor() {
    // Initialize connection states
    for (const deviceId of config.supportedDevices) {
      this.connectionStates.set(deviceId, {
        connected: false,
        connecting: false,
        messageCount: 0,
      });
    }
  }

  async connect(deviceId: string): Promise<void> {
    if (!config.supportedDevices.includes(deviceId)) {
      throw new Error(`Device ${deviceId} is not in the supported devices list`);
    }

    // Check if already connected
    const existingConnection = this.connections.get(deviceId);
    if (existingConnection?.socket && !existingConnection.socket.closed) {
      return; // Already connected
    }

    const state = this.connectionStates.get(deviceId)!;
    if (state.connecting) {
      return; // Already in progress
    }

    state.connecting = true;
    this.connectionStates.set(deviceId, state);

    try {
      // Create WebSocket connection
      const serverUrl = config.localServerUrl;
      const socket = new WebSocket(`${serverUrl}?deviceId=${deviceId}`);

      const connection = new LocalWebSocketConnection(socket, deviceId, this);
      this.connections.set(deviceId, connection);

      await connection.connect();

      state.connected = true;
      state.connecting = false;
      state.lastConnectedAt = new Date();
      this.connectionStates.set(deviceId, state);
    } catch (error) {
      state.connecting = false;
      state.lastError = error instanceof Error ? error.message : 'Connection failed';
      this.connectionStates.set(deviceId, state);
      throw error;
    }
  }

  async disconnect(deviceId: string): Promise<void> {
    const connection = this.connections.get(deviceId);
    if (connection) {
      connection.disconnect();
      this.connections.delete(deviceId);
    }

    const state = this.connectionStates.get(deviceId);
    if (state) {
      state.connected = false;
      state.connecting = false;
      this.connectionStates.set(deviceId, state);
    }
  }

  isConnected(deviceId: string): boolean {
    const state = this.connectionStates.get(deviceId);
    return state?.connected ?? false;
  }

  getConnectionState(deviceId: string): ConnectionState {
    return this.connectionStates.get(deviceId) || { connected: false, connecting: false, messageCount: 0 };
  }

  async sendSMS(deviceId: string, message: string, recipient: string, simSubscriptionId?: number): Promise<SMSResult> {
    const connection = this.connections.get(deviceId);
    
    if (!connection) {
      // Queue message for later delivery if disconnected
      return this.queueMessage(deviceId, {
        type: 'SEND_SMS',
        id: generateMessageId(),
        to: recipient,
        message,
        simSubscriptionId,
        timestamp: new Date(),
      });
    }

    if (!connection.isConnected()) {
      // Try to connect first
      await this.connect(deviceId);
      
      if (!this.isConnected(deviceId)) {
        // Still not connected, queue the message
        return this.queueMessage(deviceId, {
          type: 'SEND_SSE',
          id: generateMessageId(),
          to: recipient,
          message,
          simSubscriptionId,
          timestamp: new Date(),
        });
      }
    }

    // Send the message immediately
    return this.sendViaWebSocket(connection, {
      type: 'SEND_SMS',
      id: generateMessageId(),
      to: recipient,
      message,
      simSubscriptionId,
      timestamp: new Date(),
    });
  }

  private async queueMessage(deviceId: string, message: any): Promise<SMSResult> {
    const queue = this.messageQueue.get(deviceId) || [];
    queue.push(message);
    this.messageQueue.set(deviceId, queue);

    // Store in database for persistence
    await this.persistQueuedMessage(deviceId, message);

    return {
      success: false,
      errorCode: 'QUEUE_INSERTED',
      errorMessage: 'Message queued dueluent connection',
      timestamp: new Date(),
    };
  }

  private async persistQueuedMessage(deviceId: string, message: any): Promise<void> {
    // Implementation depends on your database setup
    // This should persist the message for later delivery
    console.log(`Persisting queued message for device ${deviceId}:`, message);
  }

  private async sendViaWebSocket(connection: LocalWebSocketConnection, message: any): Promise<SMSResult> {
    try {
      connection.sendMessage(message);
      return {
        success: true,
        messageId: message.id,
        timestamp: new Date(),
      };
    } catch (error) {
      return {
        success: false,
        errorCode: 'WEBSOCKET_SEND_ERROR',
        errorMessage: error instanceof Error ? error.message : 'WebSocket send failed',
        timestamp: new Date(),
      };
    }
  }

  // Process incoming WebSocket messages
  handleWebSocketMessage(deviceId: string, data: any): void {
    console.log(`Received WebSocket message for device ${deviceId}:`, data);
    
    // Update message count
    const state = this.connectionStates.get(deviceId);
    if (state) {
      state.messageCount += 1;
      this.connectionStates.set(deviceId, state);
    }

    // Handle different message types
    switch (data.type) {
      case 'ACK':
        // Acknowledge message delivery
        console.log(`Message ${data.id} acknowledged for device ${deviceId}`);
        break;
      case 'RESULT':
        // Handle delivery result
        console.log(`Message ${data.id} result for device ${deviceId}:`, data.status);
        break;
    }
  }

  // Process queued messages when connection is established
  async processQueuedMessages(deviceId: string): Promise<void> {
    const connection = this.connections.get(deviceId);
    const queue = this.messageQueue.get(deviceId) || [];

    if (!connection || queue.length === 0) {
      return;
    }

    console.log(`Processing ${queue.length} queued messages for device ${deviceId}`);

    for (const message of queue.slice()) {
      try {
        await this.sendViaWebSocket(connection, message);
        // Remove from queue after successful send
        queue.splice(queue.indexOf(message), 1);
        this.messageQueue.set(deviceId, queue);
      } catch (error) {
        console.error(`Failed to send queued message for device ${deviceId}:`, error);
        break; // Stop processing on first error
      }
    }

    this.messageQueue.set(deviceId, queue);
  }
}

class LocalWebSocketConnection {
  private socket: WebSocket;
  private deviceId: string;
  private channel: LocalWebSocketChannel;
  private reconnectTimeout?: NodeJS.Timeout;

  constructor(socket: WebSocket, deviceId: string, channel: LocalWebSocketChannel) {
    this.socket = socket;
    this.deviceId = deviceId;
    this.channel = channel;

    this.setupEventHandlers();
  }

  async connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      this.socket.onopen = () => {
        console.log(`WebSocket connected for device ${this.deviceId}`);
        resolve();
        // Process queued messages
        this.channel.processQueuedMessages(this.deviceId);
      };

      this.socket.onerror = (error) => {
        console.error(`WebSocket error for device ${this.deviceId}:`, error);
        reject(error);
      };

      this.socket.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          this.channel.handleWebSocketMessage(this.deviceId, data);
        } catch (error) {
          console.error(`Failed to parse WebSocket message for device ${this.deviceId}:`, error);
        }
      };

      this.socket.onclose = (event) => {
        console.log(`WebSocket closed for device ${this.deviceId}: ${event.code} ${event.reason}`);
        // Schedule reconnection if not manually closed
        if (event.code !== 1000) {
          this.scheduleReconnect();
        }
      };
    });
  }

  sendMessage(message: any): boolean {
    if (this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(message));
      return true;
    } else {
      console.error(`WebSocket not open for device ${this.deviceId}`);
      return false;
    }
  }

  disconnect(): void {
    if (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING) {
      this.socket.close(1000, 'User initiated disconnect');
    }
    this.clearReconnectTimeout();n  }

  isConnected(): boolean {
    return this.socket.readyState === WebSocket.OPEN;
  }

  private scheduleReconnect(): void {
    // Exponential backoff for reconnection
    const baseDelay = 1000; // 1 second
    const maxDelay = 30000; // 30 seconds
    const delay = Math.min(baseDelay * Math.pow(2, Math.floor(Math.random() * 4)), maxDelay);

    console.log(`Scheduling reconnection for device ${this.deviceId} in ${delay}ms`);

    this.reconnectTimeout = setTimeout(() => {
      console.log(`Attempting reconnection for device ${this.deviceId}`);
      this.connect().catch(error => {
        console.error(`Reconnection failed for device ${this.deviceId}:`, error);
      });
    }, delay);
  }

  private clearReconnectTimeout(): void {
    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
      this.reconnectTimeout = undefined;
    }
  }
}

function generateMessageId(): string {
  return `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
}
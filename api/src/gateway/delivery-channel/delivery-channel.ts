// delivery-channel.ts
export abstract class DeliveryChannel {
  abstract async sendSMS(deviceId: string, message: string, recipient: string, simSubscriptionId?: number): Promise<SMSResult>;
  abstract async connect(deviceId: string): Promise<void>;
  abstract async disconnect(deviceId: string): Promise<void>;
  abstract boolean isConnected(deviceId: string): boolean;
  abstract ConnectionState getConnectionState(deviceId: string): ConnectionState;
}

export interface ConnectionState {
  connected: boolean;
  connecting: boolean;
  lastError?: string;
  lastConnectedAt?: Date;
  messageCount: number;
}

export interface SMSResult {
  success: boolean;
  messageId?: string;
  errorCode?: string;
  errorMessage?: string;
  timestamp: Date;
}
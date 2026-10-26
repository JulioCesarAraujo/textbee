// fcm-channel.ts
import * as firebaseAdmin from 'firebase-admin';
import { BatchResponse, Message } from 'firebase-admin/messaging';

export class FcmChannel implements DeliveryChannel {
  private connectionStates = new Map<string, ConnectionState>();
  private deviceTokens = new Map<string, string>();

  constructor() {
    // Initialize Firebase Admin if not already done
    if (!firebaseAdmin.apps.length) {
      // Firebase initialization should be handled in main.ts
      // This is just the channel implementation
    }
  }

  async connect(deviceId: string, fcmToken: string): Promise<void> {
    this.deviceTokens.set(deviceId, fcmToken);
    const state = this.connectionStates.get(deviceId) || { connected: false, connecting: false, messageCount: 0 };
    state.connecting = true;
    this.connectionStates.set(deviceId, state);

    try {
      // Initialize FCM if needed
      const messaging = firebaseAdmin.messaging();
      // Verify token is valid by sending a test message
      await messaging.send({ token: fcmToken, data: { test: 'connection' } });
      
      state.connected = true;
      state.connecting = false;
      state.lastConnectedAt = new Date();
      this.connectionStates.set(deviceId, state);
    } catch (error) {
      state.connected = false;
      state.connecting = false;
      state.lastError = error instanceof Error ? error.message : 'Unknown error';
      this.connectionStates.set(deviceId, state);
      throw error;
    }
  }

  async disconnect(deviceId: string): Promise<void> {
    const state = this.connectionStates.get(deviceId);
    if (state) {
      state.connected = false;
      state.connecting = false;
      this.connectionStates.set(deviceId, state);
    }
    this.deviceTokens.delete(deviceId);
  }

  isConnected(deviceId: string): boolean {
    const state = this.connectionStates.get(deviceId);
    return state?.connected ?? false;
  }

  getConnectionState(deviceId: string): ConnectionState {
    return this.connectionStates.get(deviceId) || { connected: false, connecting: false, messageCount: 0 };
  }

  async sendSMS(deviceId: string, message: string, recipient: string, simSubscriptionId?: number): Promise<SMSResult> {
    const fcmToken = this.deviceTokens.get(deviceId);
    if (!fcmToken) {
      throw new Error(`No FCM token found for device ${deviceId}`);
    }

    const messaging = firebaseAdmin.messaging();
    
    // Prepare SMS data
    const smsData = {
      smsId: generateSmsId(),
      deviceId,
      recipient,
      message,
      simSubscriptionId,
      sentAt: new Date(),
    };

    const fcmMessage: Message = {
      token: fcmToken,
      data: {
        smsData: JSON.stringify(smsData),
        mode: 'fcm',
      },
      android: {
        priority: 'high',
        ttl: 72 * 3600 * 1000, // 72 hours
      },
    };

    try {
      const response = await messaging.sendAll([fcmMessage]);
      const result: SMSResult = {
        success: response.successCount > 0,
        messageId: response.responses[0]?.messageId,
        timestamp: new Date(),
      };

      if (response.failureCount > 0) {
        result.errorCode = 'FCM_SEND_ERROR';
        result.errorMessage = response.responses[0]?.error?.message || 'Unknown error';
      }

      const state = this.connectionStates.get(deviceId);
      if (state) {
        state.messageCount += 1;
        this.connectionStates.set(deviceId, state);
      }

      return result;
    } catch (error) {
      return {
        success: false,
        errorCode: 'FCM_SEND_ERROR',
        errorMessage: error instanceof Error ? error.message : 'Unknown error',
        timestamp: new Date(),
      };
    }
  }
}

function generateSmsId(): string {
  return `sms_${Date.now()}_${Math.random().toString(36).substr( Stakes, 9)}`;
}
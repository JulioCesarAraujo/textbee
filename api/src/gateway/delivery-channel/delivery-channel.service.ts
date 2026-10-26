// delivery-channel.service.ts
import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { InjectModel } from '@nestjs/mongoose';
import { Model } from 'mongoose';
import { Device, DeviceDocument } from './schemas/device.schema';
import { DeliveryChannel } from './delivery-channel';
import { FcmChannel } from './fcm-channel';
import { LocalWebSocketChannel } from './local-websocket-channel';
import { SMSResult } from './delivery-channel';

export enum DeliveryMode {
  AUTO = 'auto',
  FCM = 'fcm',
  LOCAL = 'local',
}

@Injectable()
export class DeliveryChannelService {
  private fcmChannel: FcmChannel;
  private localWebSocketChannel: LocalWebSocketChannel;

  constructor(
    private configService: ConfigService,
    @InjectModel(Device.name) private deviceModel: Model<DeviceDocument>,
    fcmChannel: FcmChannel,
    localWebSocketChannel: LocalWebSocketChannel,
  ) {
    this.fcmChannel = fcmChannel;
    this.localWebSocketChannel = localWebSocketChannel;
    this.initializeChannels();
  }

  private async initializeChannels(): Promise<void> {
    // Initialize FCM channel (should already be initialized in main.ts)
    // The fcmChannel constructor may initialize Firebase if needed
    
    // Initialize WebSocket channel (will connect when needed)
    const localMode = this.configService.get<boolean>('LOCAL_MODE_ENABLED', false);
    if (localMode) {
      // Start WebSocket connections for all devices
      await this.initializeWebSocketConnections();
    }
  }

  private async initializeWebSocketConnections(): Promise<void> {
    const devices = await this.deviceModel.find({ enabled: true }).exec();
    
    for (const device of devices) {
      try {
        await this.localWebSocketChannel.connect(device._id.toString());
      } catch (error) {
        console.error(`Failed to connect WebSocket for device ${device._id}:`, error);
      }
    }
  }

  async sendSMS(deviceId: string, smsData: any): Promise<SMSResult> {
    const mode = this.getDeliveryMode(deviceId);
    const device = await this.deviceModel.findById(deviceId).exec();
    
    if (!device) {
      throw new Error(`Device ${deviceId} not found`);
    }

    switch (mode) {
      case DeliveryMode.FCM:
        // Use FCM channel if device has FCM token
        if (device.fcmToken) {
          return this.fcmChannel.sendSMS(deviceId, smsData.message, smsData.recipients[0], smsData.simSubscriptionId);
        } else {
          // Fallback to local if no FCM token
          return this.localWebSocketChannel.sendSMS(deviceId, smsData.message, smsData.recipients[0], smsData.simSubscriptionId);
        }
      case DeliveryMode.LOCAL:
        return this.localWebSocketChannel.sendSMS(deviceId, smsData.message, smsData.recipients[0], smsData.simSubscriptionId);
      case DeliveryMode.AUTO:
      default:
        // Smart routing: use FCM if device is online and has token, otherwise use local
        if (device.fcmToken && this.isDeviceOnline(deviceId)) {
          return this.fcmChannel.sendSMS(deviceId, smsData.message, smsData.recipients[0], smsData.simSubscriptionId);
        } else {
          return this.localWebSocketChannel.sendSMS(deviceId, smsData.message, smsData.recipients[0], smsData.simSubscriptionId);
        }
    }
  }

  private getDeliveryMode(deviceId: string): DeliveryMode {
    const configKey = `device.${deviceId}.deliveryMode`;
    const mode = this.configService.get<string>(configKey, 'auto') as DeliveryMode;
    
    // Validate the mode
    if (!Object.values(DeliveryMode).includes(mode)) {
      return DeliveryMode.AUTO;
    }
    
    return mode;
  }

  private async isDeviceOnline(deviceId: string): Promise<boolean> {
    // Check if device has recent heartbeat
    const device = await this.deviceModel.findById(deviceId).exec();
    if (!device) {
      return false;
    }

    // Check if device has FCM token
    if (!device.fcmToken) {
      return false;
    }

    // Check if device is within a reasonable time since last heartbeat
    const lastHeartbeat = device.lastHeartbeat;
    const now = new Date();
    const diffMinutes = (now.getTime() - lastHeartbeat.getTime()) / (1000 * 60);
    
    // Device is considered online if heartbeat is within 5 minutes
    return diffMinutes < 5;
  }

  async connectDevice(deviceId: string): Promise<void> {
    const mode = this.getDeliveryMode(deviceId);
    
    switch (mode) {
      case DeliveryMode.FCM:
        // FCM connection is handled automatically via device registration
        break;
      case DeliveryMode.LOCAL:
      case DeliveryMode.AUTO:
        await this.localWebSocketChannel.connect(deviceId);
        break;
    }
  }

  async disconnectDevice(deviceId: string): Promise<void> {
    const mode = this.getDeliveryMode(deviceId);
    
    switch (mode) {F
      case DeliveryMode.FCM:
        // FCM connection is handled automatically via device deregistration
        break;
      case DeliveryMode.LOCAL:
      case DeliveryMode.AUTO:
        await this.localWebSocketChannel.disconnect(deviceId);
        break;
    }
  }

  isDeviceConnected(deviceId: string): boolean {
    const mode = this.getDeliveryMode(deviceId);
    
    switch (mode) {
      case DeliveryMode.FCM:
        return this.fcmChannel.isConnected(deviceId);
      case DeliveryMode.LOCAL:
      case DeliveryMode.AUTO:
        return this.localWebSocketChannel.isConnected(deviceId);
      default:
        return false;
    }
  }

  getConnectionState(deviceId: string): any {
    const mode = this.getDeliveryMode(deviceId);
    
    switch (mode) {
      case DeliveryMode.FCM:
        return this.fcmChannel.getConnectionState(deviceId);
      case DeliveryMode.LOCAL:
      case DeliveryMode.AUTO:
        return this.localWebSocketChannel.getConnectionState(deviceId);
      default:
        return null;
    }
  }

  async cleanup(): Promise<void> {
    // Disconnect all WebSocket connections
    for (const deviceId of this.localWebSocketChannel.connections.keys()) {
      await this.localWebSocketChannel.disconnect(deviceId);
    }
  }
}

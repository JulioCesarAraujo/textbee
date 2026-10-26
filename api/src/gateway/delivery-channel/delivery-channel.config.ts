// delivery-channel.config.ts
import { registerAs } from '@nestjs/config';
import * as Joi from 'joi';

export const deliveryChannelConfig = registerAs('deliveryChannel', () => ({
  // Enable offline mode for WebSocket delivery
  localModeEnabled: process.env.LOCAL_MODE_ENABLED === 'true' || false,
  
  // Local WebSocket server configuration
  localServerUrl: process.env.LOCAL_SERVER_URL || 'http://localhost:8080',
  
  // Delivery mode preference
  deliveryMode: process.env.DELIVERY_MODE || 'auto', // 'auto', 'fcm', or 'local'
  
  // Reconnection settings
  reconnectAttempts: parseInt(process.env.LOCAL_RECONNECT_ATTEMPTS || '5'),
  reconnectDelay: parseInt(process.env.LOCAL_RECONNECT_DELAY || '1000'),
  
  // Message queue settings
  maxMessageQueueSize: parseInt(process.env.LOCAL_MAX_QUEUE_SIZE || '1000'),
  
  // Battery optimization
  batteryOptimizationEnabled: process.env.LOCAL_BATTERY_OPTIMIZATION !== 'false',
  
  // Network quality thresholds
  networkQualityThresholds: {
    minLatency: parseInt(process.env.LOCAL_MIN_LATENCY || '100'),
    maxPacketLoss: parseInt(process.env.LOCAL_MAX_PACKET_LOSS || '5'),
    minSignalStrength: parseInt(process.env.LOCAL_MIN_SIGNAL_STRENGTH || '-80'),
  },
  
  // Supported devices for WebSocket (empty means all devices)
  supportedDevices: process.env.LOCAL_SUPPORTED_DEVICES?.split(',') || [],
}));

export const deliveryChannelConfigSchema = Joi.object({
  LOCAL_MODE_ENABLED: Joi.boolean().default(false),
  LOCAL_SERVER_URL: Joi.string().uri().default('http://localhost:8080'),
  DELIVERY_MODE: Joi.string().valid('auto', 'fcm', 'local').default('auto'),
  LOCAL_RECONNECT_ATTEMPTS: Joi.number().integer().min(0).default(5),
  LOCAL_RECONNECT_DELAY: Joi.number().integer().min(100).default(1000),
  LOCAL_MAX_QUEUE_SIZE: Joi.number().integer().min(10).default(1000),
  LOCAL_BATTERY_OPTIMIZATION: Joi.boolean().default(true),
  LOCAL_MIN_LATENCY: Joi.number().integer().min(1).default(100),
  LOCAL_MAX_PACKET_LOSS: Joi.number().integer().min(0).max(100).default(5),
  LOCAL_MIN_SIGNAL_STRENGTH: Joi.number().integer().default(-80),
  LOCAL_SUPPORTED_DEVICES: Joi.string(),
});

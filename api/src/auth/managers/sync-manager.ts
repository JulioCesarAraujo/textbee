import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { InjectModel } from '@nestjs/mongoose';
import { Model } from 'mongoose';
import { Device, DeviceDocument } from './schemas/device.schema';
import { Operation, OperationDocument } from './schemas/operation.schema';
import { ExternalApiService } from './external-api.service';
import { DeliveryChannelService } from '../gateway/delivery-channel/delivery-channel.service';

export interface SyncResult {
  success: boolean;
  processedCount: number;
  message: string;
  syncInProgress?: boolean;
}

export enum SyncStatus {
  INACTIVE = 'inactive',
  PENDING = 'pending',
  SYNCING = 'syncing',
  COMPLETED = 'completed',
  FAILED = 'failed'
}

@Injectable()
export class SyncManager {
  private syncStatusMap = new Map<string, SyncStatus>();
  private syncLocks = new Map<string, Promise<void>>();

  constructor(
    private configService: ConfigService,
    @InjectModel(Device.name) private deviceModel: Model<DeviceDocument>,
    @InjectModel(Operation.name) private operationModel: Model<OperationDocument>,
    private externalApiService: ExternalApiService,
    private deliveryChannelService: DeliveryChannelService,
  ) {}

  async syncPendingOperations(deviceId: string, force: boolean = false): Promise<SyncResult> {
    const lockKey = `sync_${deviceId}`;

    if (this.syncLocks.has(lockKey)) {
      return { success: false, processedCount: 0, message: 'Sync already in progress', syncInProgress: true };
    }

    const lock = this.createSyncLock(lockKey);
    this.syncLocks.set(lockKey, lock);

    try {
      const status = this.syncStatusMap.get(deviceId) || SyncStatus.INACTIVE;

      if (!force && status === SyncStatus.SYNCING) {
        return { success: false, processedCount: 0, message: 'Sync already in progress', syncInProgress: true };
      }

      this.syncStatusMap.set(deviceId, SyncStatus.SYNCING);

      try {
        const device = await this.deviceModel.findById(deviceId).exec();
        if (!device) {
          return { success: false, processedCount: 0, message: 'Device not found' };
        }

        const deliveryMode = this.getDeliveryMode(deviceId);
        if (deliveryMode !== 'local') {
          return { success: false, processedCount: 0, message: 'Manual sync only supported in local mode' };
        }

        const pendingOps = await this.operationModel
          .find({ deviceId, status: 'pending', syncStatus: 'pending' })
          .sort({ createdAt: 1 })
          .exec();

        if (pendingOps.length === 0) {
          return { success: true, processedCount: 0, message: 'No pending operations' };
        }

        let successfulCount = 0;
        for (const operation of pendingOps) {
          const success = await this.processOperation(operation);
          if (success) {
            successfulCount++;
            await this.updateOperationStatus(operation._id.toString(), 'sent', 'synced');
          } else {
            break;
          }
        }

        return {
          success: true,
          processedCount: successfulCount,
          message: `Sync completed: ${successfulCount} operations processed`
        };

      } catch (error) {
        return { success: false, processedCount: 0, message: `Sync error: ${(error as Error).message}` };
      } finally {
        this.syncStatusMap.set(deviceId, SyncStatus.COMPLETED);
      }
    } finally {
      this.syncLocks.delete(lockKey);
    }
  }

  private createSyncLock(lockKey: string): Promise<void> {
    return new Promise((resolve) => {
      setTimeout(() => resolve(), 0);
    });
  }

  private async processOperation(operation: OperationDocument): Promise<boolean> {
    try {
      if (operation.alreadyProcessed) {
        return true;
      }

      if (operation.externalId) {
        return await this.verifyExternalOperation(operation.externalId);
      }

      await this.sendOperationToExternal(operation);
      return true;
    } catch (error) {
      return false;
    }
  }

  private async verifyExternalOperation(externalId: string): Promise<boolean> {
    return await this.externalApiService.operationExists(externalId);
  }

  private async sendOperationToExternal(operation: OperationDocument): Promise<void> {
    const result = await this.externalApiService.sendOperation(operation.payload);
    operation.externalId = result.id;
    operation.alreadyProcessed = true;
    await operation.save();
  }

  public getSyncStatus(deviceId: string): SyncStatus {
    return this.syncStatusMap.get(deviceId) || SyncStatus.INACTIVE;
  }

  public isSyncInProgress(deviceId: string): boolean {
    return this.syncStatusMap.get(deviceId) === SyncStatus.SYNCING;
  }

  private getDeliveryMode(deviceId: string): string {
    const configKey = `device.${deviceId}.deliveryMode`;
    return this.configService.get<string>(configKey, 'auto') || 'auto';
  }

  public async updateOperationStatus(operationId: string, status: string, syncStatus: string): Promise<void> {
    await this.operationModel.findByIdAndUpdate(operationId, {
      status,
      syncStatus,
      sentAt: new Date(),
      syncTimestamp: new Date(),
    }).exec();
  }
}
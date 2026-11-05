import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { InjectModel } from '@nestjs/mongoose';
import { Model } from 'mongoose';
import { Device, DeviceDocument } from './schemas/device.schema';
import { Operation, OperationDocument } from './schemas/operation.schema';

export enum SyncStatus {
  INACTIVE = 'inactive',
  PENDING = 'pending',
  SYNCING = 'syncing',
  COMPLETED = 'completed',
  FAILED = 'failed'
}

@Injectable()
export class ManualOfflineModeManager {
  private syncStatusMap = new Map<string, SyncStatus>();

  constructor(
    private configService: ConfigService,
    @InjectModel(Device.name) private deviceModel: Model<DeviceDocument>,
    @InjectModel(Operation.name) private operationModel: Model<OperationDocument>,
  ) {}

  async isManualOfflineModeEnabled(deviceId: string): Promise<boolean> {
    const configKey = `device.${deviceId}.manualOfflineMode`;
    return this.configService.get<boolean>(configKey, false) || false;
  }

  async setManualOfflineModeEnabled(deviceId: string, enabled: boolean): Promise<void> {
    const configKey = `device.${deviceId}.manualOfflineMode`;

    await this.deviceModel.findByIdAndUpdate(deviceId, {
      [configKey]: enabled,
      manualModeEnabledAt: enabled ? new Date() : null,
      manualModeDisabledAt: !enabled ? new Date() : null,
    }).exec();

    if (enabled) {
      await this.onManualModeEnabled(deviceId);
    } else {
      await this.onManualModeDisabled(deviceId);
    }
  }

  async hasPendingOperations(deviceId: string): Promise<boolean> {
    const count = await this.operationModel
      .countDocuments({
        deviceId,
        status: 'pending',
        syncStatus: 'pending',
      })
      .exec();

    return count > 0;
  }

  async getPendingOperations(deviceId: string): Promise<OperationDocument[]> {
    return await this.operationModel
      .find({ deviceId, status: 'pending', syncStatus: 'pending' })
      .sort({ createdAt: 1 })
      .exec();
  }

  async onManualModeEnabled(deviceId: string): Promise<void> {
    await this.updateDeliveryMode(deviceId, 'local');

    if (await this.hasPendingOperations(deviceId)) {
      await this.startSyncProcess(deviceId);
    }
  }

  async onManualModeDisabled(deviceId: string): Promise<void> {
    await this.updateDeliveryMode(deviceId, 'auto');

    await this.stopSyncProcess(deviceId);
  }

  private async updateDeliveryMode(deviceId: string, mode: string): Promise<void> {
    const configKey = `device.${deviceId}.deliveryMode`;
    await this.deviceModel.findByIdAndUpdate(deviceId, {
      [configKey]: mode,
    }).exec();
  }

  private async startSyncProcess(deviceId: string): Promise<void> {
    if (this.isSyncInProgress(deviceId)) {
      return;
    }

    this.syncStatusMap.set(deviceId, SyncStatus.SYNCING);

    try {
      const result = await this.syncManager.syncPendingOperations(deviceId);

      if (result.success) {
        this.syncStatusMap.set(deviceId, SyncStatus.COMPLETED);
      } else {
        this.syncStatusMap.set(deviceId, SyncStatus.FAILED);
      }
    } catch (error) {
      this.syncStatusMap.set(deviceId, SyncStatus.FAILED);
    }
  }

  private async stopSyncProcess(deviceId: string): Promise<void> {
    this.syncStatusMap.set(deviceId, SyncStatus.INACTIVE);
  }

  public getSyncStatus(deviceId: string): SyncStatus {
    return this.syncStatusMap.get(deviceId) || SyncStatus.INACTIVE;
  }

  public isSyncInProgress(deviceId: string): boolean {
    return this.syncStatusMap.get(deviceId) === SyncStatus.SYNCING;
  }

  set syncManager(manager: any) {
    this._syncManager = manager;
  }

  private _syncManager: any;
}
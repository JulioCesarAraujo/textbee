import {
  Body,
  Controller,
  Get,
  HttpCode,
  HttpException,
  HttpStatus,
  Post,
  Request,
  UseGuards,
} from '@nestjs/common'
import { ApiOperation, ApiResponse, ApiTags } from '@nestjs/swagger'
import { AuthGuard } from '../../auth/guards/auth.guard'
import { LocalGateway } from './local-gateway'
import { offlineConfig } from './offline.config'
import { MAX_SYNC_BATCH, SyncManager } from './sync-manager'

@ApiTags('gateway')
@Controller('gateway')
export class OfflineController {
  constructor(
    private readonly syncManager: SyncManager,
    private readonly gateway: LocalGateway,
  ) {}

  @ApiOperation({
    summary: 'Offline mode status of this server',
    description:
      'Public. The app calls it to test a server address before switching to it, and reads the WebSocket path from it.',
  })
  @ApiResponse({ status: 200, description: 'Offline mode settings.' })
  @Get('/offline/status')
  getOfflineStatus() {
    const config = offlineConfig()
    return {
      data: {
        offlineModeEnabled: config.enabled,
        apiUrl: config.apiUrl || null,
        deliveryMode: config.deliveryMode,
        localWsFallback: config.localWsFallback,
        skipEmailVerification: config.enabled && config.skipEmailVerification,
        localGateway: {
          enabled: config.localGatewayEnabled,
          path: config.localGatewayPath,
          connectedDevices: this.gateway.connectedDevices().length,
        },
        sync: {
          configured: this.syncManager.isConfigured(),
          running: this.syncManager.isSyncing(),
        },
      },
    }
  }

  @UseGuards(AuthGuard)
  @ApiOperation({
    summary: 'Sync operations to the cloud now',
    description:
      'Local server only. Sends the operations the cloud has not confirmed yet, oldest first. Joins a sync that is already running instead of starting a second one.',
  })
  @ApiResponse({ status: 200, description: 'Sync result.' })
  @HttpCode(HttpStatus.OK)
  @Post('/offline/sync')
  async triggerSync() {
    const data = await this.syncManager.sync()
    return { data }
  }

  @UseGuards(AuthGuard)
  @ApiOperation({
    summary: 'Receive operations from a local server',
    description:
      'Cloud side of the offline sync. Each operation id is a UUID and is stored once: a repeat answers already_processed, which the local server counts as success.',
  })
  @ApiResponse({ status: 200, description: 'One result per operation, in input order.' })
  @HttpCode(HttpStatus.OK)
  @Post('/operations/sync')
  async receiveOperations(@Body() body: { operations?: unknown[] }, @Request() req) {
    const operations = body?.operations
    if (!Array.isArray(operations) || operations.length > MAX_SYNC_BATCH) {
      throw new HttpException(
        `operations must be an array of at most ${MAX_SYNC_BATCH} items`,
        HttpStatus.BAD_REQUEST,
      )
    }
    const results = await this.syncManager.receive(String(req.user?._id), operations)
    return { data: { results } }
  }
}

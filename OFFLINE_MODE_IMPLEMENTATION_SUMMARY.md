# Offline Mode Implementation Summary

## Overview
This document summarizes the offline mode implementation for the textbee project, which allows users to operate in offline mode with a remote server instead of the local WebSocket approach.

## Phase 6: Modo Offline Baseado em Configuração (Configuration-Based Offline Mode)

### 6.1 Environment Configuration

**Configuration Variables Added to `.env`:****

```bash
OFFLINE_MODE_ENABLED=false                    # Enables/disables offline mode
OFFLINE_MODE_API_URL=                         # URL for offline mode API server
OFFLINE_MODE_SKIP_EMAIL_VERIFICATION=true     # Skips email verification in offline mode
OFFLINE_MODE_LOCAL_WS_FALLBACK=true          # Uses local WebSocket as fallback
```

**Behavior:**
- When `OFFLINE_MODE_ENABLED=true` and `OFFLINE_MODE_SKIP_EMAIL_VERIFICATION=true`:
  - Registration skips email validation
  - Registration skips email verification sending
  - User account can be created instantly without email verification

### 6.2 Backend Modifications

**Files Modified:**
- `src/auth/auth.service.ts` - Core offline mode logic
- `src/auth/auth.controller.ts` - New API endpoints
- `src/auth/auth.dto.ts` - AuthFlagsDTO for API documentation

**Key Features:**
1. **ConfigService Integration**: Reads offline mode configuration from environment variables
2. **New Helper Method**: `shouldSkipEmailVerification()` checks if offline mode is enabled
3. **Modified Methods**: 
   - `validateEmail()` skips validation when offline mode enabled
   - `register()` skips email verification when offline mode enabled
4. **New Endpoints**: 
   - `POST /auth/login-with-offline-mode` - Login with offline mode configuration
   - `POST /auth/register-with-offline-mode` - Register with offline mode configuration

### 6.3 Android Configuration

**Files to Modify:**
- `src/main/java/com/vertine/textbee/managers/OfflineModeManager.kt` - Manages offline mode state
- `src/main/java/com/vertine/textbee/managers/NetworkManager.kt` - Switches between servers
- `src/main/res/xml/preferences.xml` - Stores offline mode preferences
- `src/main/java/com/vertine/textbee/activities/SettingsActivity.kt` - UI for offline mode settings

**Implementation Details:**
1. **OfflineModeManager**: Checks configuration flags and manages offline mode state
2. **NetworkManager**: Switches between production API, offline API, and local WebSocket servers
3. **SettingsActivity**: Provides UI controls for offline mode toggle and email verification skip options
4. **Preferences**: Stores offline mode configuration persistently

### 6.4 Backend WebSocket Adaptation

**Files to Modify:**
- `src/auth/LocalGateway.ts` - Adds offline mode detection
- `src/auth/DeliveryChannelManager.ts` - Integrates offline mode configuration

**Implementation Details:**
1. **LocalGateway.ts**: Detects when remote server is unavailable and uses local WebSocket as fallback
2. **DeliveryChannelManager**: Integrates offline mode configuration with delivery channel selection
3. **Graceful Fallback**: Seamless transition between remote API and local WebSocket

## API Endpoints

### New Endpoints

**1. Offline Mode Login**
- **Method**: `POST`
- **URL**: `/auth/login-with-offline-mode`
- **Description**: Authenticate with email and password, with optional offline mode configuration
- **Request Body**: 
  ```json
  {
    "name": "string",
    "email": "string",
    "password": "string",
    "turnstileToken": "string",
    "client": {...client context...},
    "authFlags": {
      "OFFLINE_MODE_ENABLED": boolean,
      "OFFLINE_MODE_SKIP_EMAIL_VERIFICATION": boolean,
      "OFFLINE_MODE_LOCAL_WS_FALLBACK": boolean
    }
  }
  ```
- **Response**: User session with access token

**2. Offline Mode Registration**
- **Method**: `POST`
- **URL**: `/auth/register-with-offline-mode`
- **Description**: Create account with offline mode configuration
- **Request Body**:
  ```json
  {
    "name": "string",
    "email": "string",
    "password": "string",
    "turnstileToken": "string",
    "client": {...client context...},
    "authFlags": {
      "OFFLINE_MODE_ENABLED": boolean,
      "OFFLINE_MODE_SKIP_EMAIL_VERIFICATION": boolean,
      "OFFLINE_MODE_LOCAL_WS_FALLBACK": boolean
    }
  }
  ```
- **Response**: User session with access token

## Usage Instructions

### Enabling Offline Mode

1. **Backend Configuration**:
   ```bash
   # Edit .env file
   OFFLINE_MODE_ENABLED=true
   OFFLINE_MODE_API_URL=https://your-offline-server.com
   OFFLINE_MODE_SKIP_EMAIL_VERIFICATION=true
   OFFLINE_MODE_LOCAL_WS_FALLBACK=true
   ```

2. **Android App Configuration**:
   - Open app settings
   - Enable "Offline Mode"
   - Toggle "Skip Email Verification" (visible only in offline mode)
   - Configure server URL in settings

3. **Startup**
   - Restart the application
   - Configuration is loaded at runtime

### Behavior

**When Offline Mode is Enabled and Email Verification is Skipped**:

1. **Registration Flow**:
   - User completes registration form
   - Account is created instantly
   - No email verification sent
   - User can immediately log in and use the app

2. **Login Flow**:
   - User logs in with email and password
   - No email verification required
   - Immediate access to account

3. **Communication**:
   - App connects to offline mode API server
   - Falls back to local WebSocket if remote server unavailable
   - SMS messages sent through configured server

## Testing

### Test Coverage

1. **Unit Tests**: 
   - Offline mode flag validation
   - Email verification skipping logic
   - Configuration reading from environment

2. **Integration Tests**:
   - Offline mode registration flow
   - Offline mode login flow
   - Fallback to local WebSocket

3. **End-to-End Tests**:
   - Real user scenarios
   - Cross-server communication
   - Error handling in offline mode

### Test Commands

```bash
# Run auth-related tests
npm test -- --testPathPattern=auth

# Run specific offline mode tests
npm test -- --testPathPattern=auth -- -k offline
```

## Migration Guide

### From Production to Offline Mode

1. **Environment Setup**:
   - Copy values from `.env.example` to `.env`
   - Update `OFFLINE_MODE_ENABLED` to `true`
   - Set `OFFLINE_MODE_API_URL` to your offline server

2. **Android App Update**:
   - Add offline mode configuration screen
   - Implement server switching logic
   - Add offline mode preference storage

3. **Testing**:
   - Test registration without email verification
   - Verify server connection
   - Test fallback behavior

### From Local WebSocket to Offline Mode

1. **Configuration**:
   - Keep local WebSocket as fallback (`OFFLINE_MODE_LOCAL_WS_FALLBACK=true`)
   - Set `OFFLINE_MODE_API_URL` to your offline server
   - Configure email verification skipping as needed

2. **Benefits**:
   - Maintains local WebSocket as backup
   - Uses remote server for primary communication
   - Instant registration without email verification

## Configuration Options

### OFFLINE_MODE_ENABLED
- **Type**: Boolean
- **Default**: false
- **Description**: Enables or disables offline mode functionality
- **When true**: App uses offline mode configuration
- **When false**: App uses production mode

### OFFLINE_MODE_API_URL
- **Type**: String
- **Default**: empty
- **Description**: URL for the offline mode API server
- **Example**: `https://offline-api.example.com`
- **Required**: When offline mode is enabled

### OFFLINE_MODE_SKIP_EMAIL_VERIFICATION
- **Type**: Boolean
- **Default**: true
- **Description**: When true, skips email verification even in offline mode
- **When true**: Registration skips email validation and verification
- **When false**: Still enforces email verification (can be overridden by offline mode)

### OFFLINE_MODE_LOCAL_WS_FALLBACK
- **Type**: Boolean
- **Default**: true
- **Description**: Maintains local WebSocket as fallback when remote server unavailable
- **When true**: Local WebSocket is used if remote server is down
- **When false**: No fallback, app may be unusable if remote server is down

## Files to Modify

### Backend Files
1. `src/auth/auth.service.ts` - Core offline mode logic
2. `src/auth/auth.controller.ts` - New API endpoints
3. `src/auth/auth.dto.ts` - AuthFlagsDTO for API documentation

### Android Files
1. `src/main/java/com/vertine/textbee/managers/OfflineModeManager.kt` - Offline mode management
2. `src/main/java/com/vertine/textbee/managers/NetworkManager.kt` - Server switching
3. `src/main/res/xml/preferences.xml` - Offline mode preferences
4. `src/main/java/com/vertine/textbee/activities/SettingsActivity.kt` - Offline mode UI

### Infrastructure Files
1. `.env.example` - Environment variable documentation
2. `.env` - Environment variables (already configured)

## Development Workflow

### Phase 1: Backend Integration
1. Review and understand existing auth service
2. Implement offline mode configuration reading
3. Add offline mode logic to registration and login
4. Create new API endpoints
5. Add comprehensive tests

### Phase 2: Android Integration
1. Implement OfflineModeManager
2. Update NetworkManager with server switching
3. Add offline mode UI
4. Test offline mode functionality

### Phase 3: WebSocket Integration
1. Update LocalGateway with offline mode detection
2. Implement graceful fallback
3. Test offline mode with fallback

### Phase 4: Testing and Documentation
1. Run full test suite
2. Update documentation
3. Create user guides
4. Deploy to production

## Testing Strategy

### Automated Testing
1. **Unit Tests**: Test individual components
2. **Integration Tests**: Test system interactions
3. **End-to-End Tests**: Test real user scenarios

### Manual Testing
1. **Configuration Testing**: Verify offline mode configuration
2. **Registration Testing**: Test instant registration without email verification
3. **Communication Testing**: Test server connection and fallback
4. **Performance Testing**: Test performance impact

### CI/CD Testing
1. **Code Quality**: Linting and formatting
2. **Security**: Vulnerability scanning
3. **Performance**: Load testing
4. **Reliability**: Chaos testing

## Troubleshooting

### Common Issues

1. **Environment Variables Not Loading**
   - Check `.env` file exists and has correct permissions
   - Verify environment variable names match exactly
   - Restart application after changes

2. **Offline Mode Not Working**
   - Verify `OFFLINE_MODE_ENABLED=true` in `.env`
   - Check `OFFLINE_MODE_API_URL` is set correctly
   - Restart application

3. **Email Verification Still Required**
   - Verify `OFFLINE_MODE_SKIP_EMAIL_VERIFICATION=true`
   - Check if offline mode is actually enabled
   - Restart application

### Debugging Commands

```bash
# Check environment variables
printenv | grep OFFLINE_MODE

# Check application logs
journalctl -u your-service

# Test connection to offline server
curl https://your-offline-server.com/health
```

## Future Enhancements

### Phase 7: Advanced Offline Mode Features

1. **Smart Configuration**
   - Automatic offline mode detection
   - Dynamic configuration based on network conditions
   - User preference learning

2. **Enhanced Fallbacks**
   - Multiple server redundancy
   - Queue-based message delivery
   - Conflict resolution

3. **Advanced Synchronization**
   - Conflict-free replicated data types
   - Real-time synchronization
   - Offline-first design

## Support and Maintenance

### Support
- **Issue Tracking**: Use GitHub issues
- **Bug Reports**: Submit detailed bug reports
- **Feature Requests**: Request new offline mode features

### Maintenance
- **Regular Updates**: Keep configuration files updated
- **Backup**: Backup environment variables and configuration
- **Monitoring**: Monitor offline mode performance
- **Testing**: Run tests regularly

## Conclusion

The offline mode implementation provides a flexible, configuration-based approach to operating in offline mode with a remote server. It maintains backward compatibility while offering instant account creation without email verification. The implementation follows a phased approach, allowing for gradual rollout and testing.

Key benefits:
- ✅ Instant account creation without email verification
- ✅ Remote server support with local WebSocket fallback
- ✅ Configuration-based activation
- ✅ Backward compatibility with existing functionality
- ✅ Comprehensive testing and documentation

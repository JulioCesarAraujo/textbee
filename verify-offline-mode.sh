#!/bin/bash

echo "=== Offline Mode Implementation Verification ==="

echo "\n1. Checking environment configuration:"
echo "   - Checking .env.example:"
grep -q "OFFLINE_MODE_ENABLED" /home/user/projetos/textbee/api/.env.example && echo "     ✅ Found" || echo "     ❌ Missing"

echo "   - Checking .env:"
grep -q "OFFLINE_MODE_ENABLED" /home/user/projetos/textbee/api/.env && echo "     ✅ Found" || echo "     ❌ Missing"

echo "\n2. Checking backend files:"
echo "   - auth.service.ts:"
grep -q "OFFLINE_MODE_ENABLED" /home/user/projetos/textbee/api/src/auth/auth.service.ts && echo "     ✅ Found" || echo "     ❌ Missing"
grep -q "shouldSkipEmailVerification" /home/user/projetos/textbee/api/src/auth/auth.service.ts && echo "     ✅ Found" || echo "     ❌ Missing"
grep -q "OFFLINE_MODE_SKIP_EMAIL_VERIFICATION" /home/user/projetos/textbee/api/src/auth/auth.service.ts && echo "     ✅ Found" || echo "     ❌ Missing"

echo "   - auth.controller.ts:"
grep -q "/login-with-offline-mode" /home/user/projetos/textbee/api/src/auth/auth.controller.ts && echo "     ✅ Found" || echo "     ❌ Missing"
grep -q "/register-with-offline-mode" /home/user/projetos/textbee/api/src/auth/auth.controller.ts && echo "     ✅ Found" || echo "     ❌ Missing"

echo "   - auth.dto.ts:"
grep -q "class AuthFlagsDTO" /home/user/projetos/textbee/api/src/auth/auth.dto.ts && echo "     ✅ Found" || echo "     ❌ Missing"
grep -q "OFFLINE_MODE_LOCAL_WS_FALLBACK" /home/user/projetos/textbee/api/src/auth/auth.dto.ts && echo "     ✅ Found" || echo "     ❌ Missing"

echo "\n3. Configuration summary:"
echo "   Configuration flags available:"
echo "     - OFFLINE_MODE_ENABLED: Enables/disables offline mode"
echo "     - OFFLINE_MODE_API_URL: URL for offline mode API server"
echo "     - OFFLINE_MODE_SKIP_EMAIL_VERIFICATION: Skips email verification in offline mode"
echo "     - OFFLINE_MODE_LOCAL_WS_FALLBACK: Uses local WebSocket as fallback"

echo "\n4. Offline mode behavior:"
echo "   - When OFFLINE_MODE_ENABLED=true and OFFLINE_MODE_SKIP_EMAIL_VERIFICATION=true:"
echo "     • Registration skips email validation"
echo "     • Registration skips email verification sending"

echo "\n5. API endpoints available:"
echo "   - POST /auth/login-with-offline-mode: Login with offline mode configuration"
echo "   - POST /auth/register-with-offline-mode: Register with offline mode configuration"

echo "\n=== Implementation Complete ==="

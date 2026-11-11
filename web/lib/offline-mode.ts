'use client'

import { useEffect, useState } from 'react'
import httpBrowserClient from '@/lib/httpBrowserClient'
import { ApiEndpoints } from '@/config/api'

// What the login and register forms need from GET /gateway/offline/status
export type OfflineModeStatus = {
  // The server runs with OFFLINE_MODE_ENABLED=true: email verification can be skipped
  offlineModeEnabled: boolean
  // The server skips email verification for every account (OFFLINE_MODE_SKIP_EMAIL_VERIFICATION)
  skipEmailVerification: boolean
}

const OFFLINE_OFF: OfflineModeStatus = {
  offlineModeEnabled: false,
  skipEmailVerification: false,
}

// Read once per page. Any failure counts as "not in offline mode", so the
// normal verification flow is the fallback.
export function useOfflineModeStatus(): OfflineModeStatus {
  const [status, setStatus] = useState<OfflineModeStatus>(OFFLINE_OFF)

  useEffect(() => {
    let cancelled = false
    httpBrowserClient
      .get(ApiEndpoints.gateway.offlineStatus())
      .then((res) => {
        const data = res.data?.data
        if (cancelled || !data) return
        setStatus({
          offlineModeEnabled: data.offlineModeEnabled === true,
          skipEmailVerification: data.skipEmailVerification === true,
        })
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [])

  return status
}

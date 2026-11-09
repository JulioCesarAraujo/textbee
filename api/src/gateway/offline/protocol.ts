// WebSocket protocol between the server and the app (plan phase 1.3).
//
// server -> app  { type: "SEND_SMS", id: UUID, to, message, ... }
// app -> server  { type: "ACK", id, status: "received" | "duplicate" }
// app -> server  { type: "RESULT", id, status: "SENT" | "FAILED", error? }
// server -> app  { type: "RESULT_ACK", id }   the RESULT is stored; the app
//                                             may mark the operation SYNCED

export interface SendSmsFrame {
  type: 'SEND_SMS'
  id: string
  to: string
  message: string
  smsId?: string
  smsBatchId?: string
  simSubscriptionId?: number
  createdAt: string
}

export interface AckFrame {
  type: 'ACK'
  id: string
  status?: string
}

export type ResultStatus = 'SENT' | 'FAILED'

export interface ResultFrame {
  type: 'RESULT'
  id: string
  status: ResultStatus
  error?: string
}

export interface ResultAckFrame {
  type: 'RESULT_ACK'
  id: string
}

export type ClientFrame = AckFrame | ResultFrame

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export function isUuid(value: unknown): value is string {
  return typeof value === 'string' && UUID_PATTERN.test(value)
}

// Returns null for anything that is not a well formed ACK or RESULT frame
export function parseClientFrame(raw: string): ClientFrame | null {
  let parsed: any
  try {
    parsed = JSON.parse(raw)
  } catch {
    return null
  }
  if (!parsed || typeof parsed !== 'object' || !isUuid(parsed.id)) return null

  if (parsed.type === 'ACK') {
    return {
      type: 'ACK',
      id: parsed.id,
      ...(typeof parsed.status === 'string' && { status: parsed.status }),
    }
  }
  if (
    parsed.type === 'RESULT' &&
    (parsed.status === 'SENT' || parsed.status === 'FAILED')
  ) {
    return {
      type: 'RESULT',
      id: parsed.id,
      status: parsed.status,
      ...(typeof parsed.error === 'string' && {
        error: parsed.error.slice(0, 500),
      }),
    }
  }
  return null
}

import type {
  AskResponse, ChunkDto, ChunkMutationResult, DocumentDto,
  FeedbackDto, FeedbackResult, PageResult
} from '~/types'

function base(): string {
  const cfg = useRuntimeConfig()
  return (cfg.public.apiBase || '').replace(/\/$/, '')
}

async function handle<T>(p: Promise<T>): Promise<T> {
  try {
    return await p
  } catch (e: any) {
    const msg = e?.data?.message || e?.message || '请求失败，请检查后端服务'
    throw new Error(msg)
  }
}

export interface AskStreamHandlers {
  onStatus?: (payload: any) => void
  onDelta?: (text: string) => void
  onDone?: (res: AskResponse) => void
  onError?: (err: Error) => void
}

/**
 * SSE 流式问答：POST /api/chat/ask 返回 text/event-stream。
 * 事件：status(阶段) / delta(增量文本) / done(最终 AskResponse) / error(错误)。
 */
export async function askChatStream(
  question: string,
  userId: string,
  handlers: AskStreamHandlers
): Promise<void> {
  const resp = await fetch(`${base()}/api/chat/ask`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify({ question, userId })
  })
  if (!resp.ok || !resp.body) {
    const bodyText = await resp.text().catch(() => '')
    throw new Error(`问答请求失败(${resp.status})：${bodyText.slice(0, 200)}`)
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let eventName = 'message'
  let dataLines: string[] = []

  function dispatch() {
    if (dataLines.length) {
      const data = dataLines.join('\n')
      dataLines = []
      try {
        const payload = JSON.parse(data)
        if (eventName === 'status') handlers.onStatus?.(payload)
        else if (eventName === 'delta') handlers.onDelta?.(String(payload.text || ''))
        else if (eventName === 'done') handlers.onDone?.(payload as AskResponse)
        else if (eventName === 'error') throw new Error(payload?.message || '未知错误')
      } catch (e: any) {
        handlers.onError?.(e instanceof Error ? e : new Error(String(e?.message || e)))
      }
    }
    eventName = 'message'
  }

  function handleFrame(frame: string) {
    for (const rawLine of frame.split('\n')) {
      const line = rawLine.trim()
      if (!line || line.startsWith(':')) continue
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trimStart())
      }
    }
  }

  try {
    for (;;) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      let idx: number
      while ((idx = buffer.indexOf('\n\n')) >= 0) {
        const frame = buffer.slice(0, idx)
        buffer = buffer.slice(idx + 2)
        handleFrame(frame)
        dispatch()
      }
    }
    if (buffer.trim()) {
      handleFrame(buffer)
      dispatch()
    }
  } catch (e: any) {
    handlers.onError?.(e instanceof Error ? e : new Error(String(e)))
  }
}

export async function uploadDocument(file: File): Promise<DocumentDto> {
  const fd = new FormData()
  fd.append('file', file)
  return handle($fetch<DocumentDto>(`${base()}/api/documents/upload`, {
    method: 'POST',
    body: fd
  }))
}

export async function listDocuments(): Promise<DocumentDto[]> {
  return handle($fetch<DocumentDto[]>(`${base()}/api/documents`))
}

export async function getDocument(docId: string): Promise<DocumentDto> {
  return handle($fetch<DocumentDto>(`${base()}/api/documents/${docId}`))
}

export async function reprocessDocument(docId: string): Promise<DocumentDto> {
  return handle($fetch<DocumentDto>(`${base()}/api/documents/${docId}/reprocess`, { method: 'POST' }))
}

export async function deleteDocument(docId: string): Promise<void> {
  return handle($fetch(`${base()}/api/documents/${docId}`, { method: 'DELETE' }))
}

export async function listChunks(params: {
  docId?: string
  status?: string
  page?: number
  size?: number
}): Promise<PageResult<ChunkDto>> {
  const q: Record<string, string> = {}
  if (params.docId) q.docId = params.docId
  if (params.status) q.status = params.status
  q.page = String(params.page ?? 0)
  q.size = String(params.size ?? 50)
  const qs = new URLSearchParams(q).toString()
  return handle($fetch<PageResult<ChunkDto>>(`${base()}/api/chunks?${qs}`))
}

export async function editChunk(chunkId: string, chunkText: string): Promise<ChunkMutationResult> {
  return handle($fetch<ChunkMutationResult>(`${base()}/api/chunks/${chunkId}`, {
    method: 'PUT',
    body: { chunkText }
  }))
}

export async function mergeChunks(chunkIds: string[], mergedText?: string): Promise<ChunkMutationResult> {
  return handle($fetch<ChunkMutationResult>(`${base()}/api/chunks/merge`, {
    method: 'POST',
    body: { chunkIds, mergedText: mergedText || '' }
  }))
}

export async function splitChunk(chunkId: string, pieces: string[]): Promise<ChunkMutationResult> {
  return handle($fetch<ChunkMutationResult>(`${base()}/api/chunks/split`, {
    method: 'POST',
    body: { chunkId, pieces }
  }))
}

export async function submitFeedback(
  chunkId: string,
  payload: { feedbackType: string; userComment?: string; correctedText?: string; userId?: string }
): Promise<FeedbackResult> {
  return handle($fetch<FeedbackResult>(`${base()}/api/chunks/${chunkId}/feedback`, {
    method: 'POST',
    body: payload
  }))
}

export async function listFeedbacks(page = 0, size = 50): Promise<PageResult<FeedbackDto>> {
  return handle($fetch<PageResult<FeedbackDto>>(`${base()}/api/chunks/feedbacks?page=${page}&size=${size}`))
}

export function fileUrl(docId: string): string {
  return `${base()}/api/documents/${encodeURIComponent(docId)}/file`
}

export interface ApiError {
  success: boolean
  code: number
  message: string
}

export interface DocumentDto {
  docId: string
  docName: string
  filePath?: string
  fileSize: number
  totalPages: number
  status: 'processing' | 'done' | 'failed'
  uploadTime: string
}

export interface Citation {
  chunkId: string
  docId: string
  docName: string
  pageNum: number
  chapterTitle?: string
  text: string
}

export interface AskResponse {
  answer: string
  citations: Citation[]
  hasAnswer: boolean
  retrievalScore: number | null
  notice: string | null
}

export interface ChunkDto {
  chunkId: string
  docId: string
  docName: string
  pageNum: number
  chapterTitle?: string
  chunkText: string
  rawContent?: string
  charStart: number
  charEnd: number
  splitMethod: 'LLM' | 'RULE'
  llmModel?: string
  status: 'active' | 'merged' | 'split' | 'edited'
  mergedIntoChunkId?: string
  parentChunkId?: string
  editedVersionOf?: string
  qualityScore?: number
  createdAt: string
  updatedAt: string
}

export interface ChunkMutationResult {
  chunk: ChunkDto | null
  replacedChunks: ChunkDto[]
  notice: string | null
}

export interface FeedbackResult {
  feedbackId: number
  status: string
  notice: string | null
  newChunk: ChunkDto | null
}

export interface FeedbackDto {
  feedbackId: number
  chunkId: string
  docId: string
  docName: string
  pageNum: number
  feedbackType: string
  userComment?: string
  correctedText?: string
  oldChunkText?: string
  userId?: string
  createdAt: string
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export interface ChatItem {
  id: string
  role: 'user' | 'assistant'
  question?: string
  answer?: string
  citations?: Citation[]
  hasAnswer?: boolean
  loading?: boolean
  streaming?: boolean
  error?: string
}

export type ChatHistory = ChatItem[]

export interface ConversationDto {
  conversationId: string
  userId: string
  title: string
  lastMessageTime: string | null
  createdAt: string | null
}

export interface MessageDto {
  messageId: number
  role: 'user' | 'assistant'
  content: string
  citations: Citation[] | null
  createdAt: string | null
}

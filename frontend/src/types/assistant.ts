export type AiActionType = 'NAVIGATE' | 'OPEN_MODAL' | 'PREFILL_FORM' | 'HIGHLIGHT_SECTION'

export interface AiAction {
  type: AiActionType
  target: string | null
  payload: Record<string, string>
}

export interface AiConversationItem {
  role: 'user' | 'assistant' | 'system'
  message: string
}

export interface AiChatRequest {
  query: string
  currentPage: string
  conversation: AiConversationItem[]
}

export interface AiChatResponse {
  responseId: string
  message: string
  action: AiAction | null
  suggestions: string[]
}

export interface AiMessage {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  createdAt: number
  action?: AiAction | null
}

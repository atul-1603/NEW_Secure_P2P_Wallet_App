import { apiClient } from './apiClient'
import type { AiChatRequest, AiChatResponse } from '../types/assistant'

export const assistantService = {
  async chat(payload: AiChatRequest): Promise<AiChatResponse> {
    const response = await apiClient.post<AiChatResponse>('/ai/chat', payload)
    return response.data
  },
}

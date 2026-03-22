import { create } from 'zustand'
import type { AiAction, AiMessage } from '../types/assistant'

type AssistantState = {
  isOpen: boolean
  isTyping: boolean
  currentPage: string
  messages: AiMessage[]
  pendingAction: AiAction | null
  setOpen: (open: boolean) => void
  toggle: () => void
  setTyping: (typing: boolean) => void
  setCurrentPage: (page: string) => void
  addMessage: (message: AiMessage) => void
  clearSession: () => void
  setPendingAction: (action: AiAction | null) => void
}

const MAX_MESSAGES = 40

export const useAssistantStore = create<AssistantState>((set) => ({
  isOpen: false,
  isTyping: false,
  currentPage: '/',
  messages: [
    {
      id: 'assistant-welcome',
      role: 'assistant',
      content: 'Hi, I can help with balance checks, transactions, spending insights, and guided wallet actions.',
      createdAt: Date.now(),
    },
  ],
  pendingAction: null,
  setOpen: (open) => set({ isOpen: open }),
  toggle: () => set((state) => ({ isOpen: !state.isOpen })),
  setTyping: (typing) => set({ isTyping: typing }),
  setCurrentPage: (page) => set({ currentPage: page }),
  addMessage: (message) =>
    set((state) => ({
      messages: [...state.messages, message].slice(-MAX_MESSAGES),
    })),
  clearSession: () =>
    set({
      messages: [
        {
          id: 'assistant-welcome',
          role: 'assistant',
          content: 'Session cleared. Ask anything about your wallet activity or app features.',
          createdAt: Date.now(),
        },
      ],
      pendingAction: null,
    }),
  setPendingAction: (action) => set({ pendingAction: action }),
}))

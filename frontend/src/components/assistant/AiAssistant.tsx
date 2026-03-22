import { AnimatePresence, motion } from 'framer-motion'
import { Bot, Loader2, MessageCircle, SendHorizonal, Sparkles, Trash2, X } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Button } from '../ui/button'
import { Input } from '../ui/input'
import { assistantService } from '../../services/assistant.service'
import { useAssistantStore } from '../../stores/assistant.store'
import type { AiAction, AiConversationItem, AiMessage } from '../../types/assistant'

const AI_ACTION_EVENT = 'wallet-ai-action'

function buildSuggestionsForPath(pathname: string): string[] {
  if (pathname.startsWith('/dashboard')) {
    return ['Check my balance', 'Show recent transactions', 'Summarize weekly spending']
  }

  if (pathname.startsWith('/wallet')) {
    return ['What is my balance?', 'Open add money', 'How can I receive money?']
  }

  if (pathname.startsWith('/withdraw')) {
    return ['How does withdrawal work?', 'Prefill withdraw 2000', 'Open add bank account']
  }

  if (pathname.startsWith('/transactions')) {
    return ['Show my recent transactions', 'Any failed transactions?', 'Open analytics']
  }

  if (pathname.startsWith('/send-money')) {
    return ['Send INR 500 to Rahul', 'Open contacts', 'How to scan recipient QR?']
  }

  return ['Check balance', 'Show recent transactions', 'Open send money']
}

function toConversation(messages: AiMessage[]): AiConversationItem[] {
  return messages
    .slice(-10)
    .map((item) => ({
      role: item.role,
      message: item.content,
    }))
}

function createId(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
}

function buildPrefillSearch(payload: Record<string, string>): string {
  const params = new URLSearchParams()

  Object.entries(payload).forEach(([key, value]) => {
    if (!value) {
      return
    }
    params.set(`ai_${key}`, value)
  })

  return params.toString()
}

export function AiAssistant() {
  const navigate = useNavigate()
  const location = useLocation()

  const {
    isOpen,
    isTyping,
    currentPage,
    messages,
    setOpen,
    toggle,
    setTyping,
    setCurrentPage,
    addMessage,
    clearSession,
    setPendingAction,
  } = useAssistantStore()

  const [input, setInput] = useState('')

  useEffect(() => {
    setCurrentPage(location.pathname)
  }, [location.pathname, setCurrentPage])

  const contextualSuggestions = useMemo(() => buildSuggestionsForPath(location.pathname), [location.pathname])

  async function sendQuery(query: string) {
    const trimmed = query.trim()
    if (!trimmed) {
      return
    }

    const userMessage: AiMessage = {
      id: createId('user'),
      role: 'user',
      content: trimmed,
      createdAt: Date.now(),
    }

    addMessage(userMessage)
    setInput('')
    setTyping(true)

    try {
      const response = await assistantService.chat({
        query: trimmed,
        currentPage,
        conversation: toConversation([...messages, userMessage]),
      })

      addMessage({
        id: response.responseId || createId('assistant'),
        role: 'assistant',
        content: response.message,
        createdAt: Date.now(),
        action: response.action,
      })

      if (response.action) {
        executeAction(response.action)
      }
    } catch {
      addMessage({
        id: createId('assistant-error'),
        role: 'assistant',
        content: 'I could not process that request right now. Try asking: Check balance, Recent transactions, or Open send money.',
        createdAt: Date.now(),
      })
    } finally {
      setTyping(false)
    }
  }

  function executeAction(action: AiAction) {
    setPendingAction(action)

    if (action.type === 'NAVIGATE' && action.target) {
      navigate(action.target)
      return
    }

    if (action.type === 'PREFILL_FORM') {
      if (action.target) {
        const search = buildPrefillSearch(action.payload)
        navigate({
          pathname: action.target,
          search: search ? `?${search}` : '',
        })
      }

      window.dispatchEvent(new CustomEvent(AI_ACTION_EVENT, { detail: action }))
      return
    }

    if (action.target && action.target.startsWith('/')) {
      navigate(action.target)
      window.setTimeout(() => {
        window.dispatchEvent(new CustomEvent(AI_ACTION_EVENT, { detail: action }))
      }, 150)
      return
    }

    window.dispatchEvent(new CustomEvent(AI_ACTION_EVENT, { detail: action }))
  }

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void sendQuery(input)
  }

  return (
    <>
      <motion.button
        type="button"
        className="fixed bottom-6 right-6 z-50 flex h-14 w-14 items-center justify-center rounded-full bg-gradient-to-br from-cyan-500 to-blue-600 text-white shadow-xl"
        whileHover={{ scale: 1.06 }}
        whileTap={{ scale: 0.95 }}
        animate={{ boxShadow: ['0 0 0 0 rgba(6,182,212,0.45)', '0 0 0 14px rgba(6,182,212,0)', '0 0 0 0 rgba(6,182,212,0)'] }}
        transition={{ duration: 2.4, repeat: Infinity }}
        onClick={toggle}
        aria-label="Open AI Assistant"
      >
        <MessageCircle className="h-6 w-6" />
      </motion.button>

      <AnimatePresence>
        {isOpen ? (
          <motion.aside
            className="fixed bottom-24 right-4 z-50 flex h-[70vh] w-[min(430px,95vw)] flex-col overflow-hidden rounded-2xl border bg-card shadow-2xl"
            initial={{ opacity: 0, y: 20, scale: 0.96 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 16, scale: 0.98 }}
            transition={{ duration: 0.2 }}
          >
            <div className="flex items-center justify-between border-b bg-gradient-to-r from-cyan-500/10 via-sky-500/10 to-blue-500/10 px-4 py-3">
              <div className="flex items-center gap-2">
                <div className="flex h-8 w-8 items-center justify-center rounded-full border bg-background">
                  <Bot className="h-4 w-4 text-primary" />
                </div>
                <div>
                  <p className="text-sm font-semibold">AI Super Assistant</p>
                  <p className="text-xs text-muted-foreground">Secure wallet guidance and insights</p>
                </div>
              </div>
              <div className="flex items-center gap-1">
                <Button variant="ghost" size="icon" onClick={clearSession} aria-label="Clear session">
                  <Trash2 className="h-4 w-4" />
                </Button>
                <Button variant="ghost" size="icon" onClick={() => setOpen(false)} aria-label="Close assistant">
                  <X className="h-4 w-4" />
                </Button>
              </div>
            </div>

            <div className="flex-1 space-y-3 overflow-y-auto bg-muted/10 p-4">
              {messages.map((item) => {
                const assistant = item.role === 'assistant'
                return (
                  <div key={item.id} className={`flex ${assistant ? 'justify-start' : 'justify-end'}`}>
                    <div className={`max-w-[85%] rounded-2xl px-3 py-2 text-sm ${assistant ? 'border bg-background text-foreground' : 'bg-primary text-primary-foreground'}`}>
                      {item.content}
                    </div>
                  </div>
                )
              })}

              {isTyping ? (
                <div className="flex items-center gap-2 text-xs text-muted-foreground">
                  <Loader2 className="h-3.5 w-3.5 animate-spin" /> Thinking...
                </div>
              ) : null}
            </div>

            <div className="border-t bg-background/80 p-3">
              <div className="mb-2 flex flex-wrap gap-2">
                {contextualSuggestions.map((suggestion) => (
                  <button
                    key={suggestion}
                    type="button"
                    className="rounded-full border bg-background px-2.5 py-1 text-xs hover:bg-muted"
                    onClick={() => {
                      void sendQuery(suggestion)
                    }}
                  >
                    <span className="inline-flex items-center gap-1">
                      <Sparkles className="h-3 w-3 text-primary" /> {suggestion}
                    </span>
                  </button>
                ))}
              </div>

              <form className="flex items-center gap-2" onSubmit={onSubmit}>
                <Input
                  value={input}
                  onChange={(event) => setInput(event.target.value)}
                  placeholder="Ask about balance, transactions, or app actions"
                  maxLength={500}
                />
                <Button type="submit" size="icon" disabled={isTyping || !input.trim()}>
                  <SendHorizonal className="h-4 w-4" />
                </Button>
              </form>
            </div>
          </motion.aside>
        ) : null}
      </AnimatePresence>
    </>
  )
}

export { AI_ACTION_EVENT }

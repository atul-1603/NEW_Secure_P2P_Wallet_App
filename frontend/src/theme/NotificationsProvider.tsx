import { Client } from '@stomp/stompjs'
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import SockJS from 'sockjs-client'
import { useAuth } from '../auth/AuthContext'
import { notificationService } from '../services/notification.service'
import { tokenStore } from '../services/tokenStore'
import type { NotificationResponse, NotificationSocketEvent } from '../types/api'
import { useToast } from '../components/ui/toast'

type NotificationsContextValue = {
  notifications: NotificationResponse[]
  unreadCount: number
  loading: boolean
  markAsRead: (id: string) => Promise<void>
  markAllAsRead: () => Promise<void>
  deleteOne: (id: string) => Promise<void>
  refresh: () => Promise<void>
}

const NotificationsContext = createContext<NotificationsContextValue | undefined>(undefined)

export function NotificationsProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated, user } = useAuth()
  const { showError } = useToast()
  const [notifications, setNotifications] = useState<NotificationResponse[]>([])
  const [loading, setLoading] = useState(false)
  const stompClientRef = useRef<Client | null>(null)

  const unreadCount = useMemo(
    () => notifications.filter((item) => !item.isRead).length,
    [notifications],
  )

  const playNotificationTone = useCallback(() => {
    if (typeof window === 'undefined') {
      return
    }

    try {
      const audioContext = new window.AudioContext()
      const oscillator = audioContext.createOscillator()
      const gainNode = audioContext.createGain()

      oscillator.type = 'sine'
      oscillator.frequency.setValueAtTime(880, audioContext.currentTime)
      gainNode.gain.setValueAtTime(0.03, audioContext.currentTime)

      oscillator.connect(gainNode)
      gainNode.connect(audioContext.destination)
      oscillator.start()
      oscillator.stop(audioContext.currentTime + 0.12)
    } catch {
      // Ignore audio failures to keep notification flow non-blocking.
    }
  }, [])

  const mergeNotification = useCallback((notification: NotificationResponse) => {
    setNotifications((previous) => {
      const filtered = previous.filter((item) => item.id !== notification.id)
      return [notification, ...filtered].sort((a, b) => +new Date(b.createdAt) - +new Date(a.createdAt))
    })
  }, [])

  const removeNotification = useCallback((id: string) => {
    setNotifications((previous) => previous.filter((item) => item.id !== id))
  }, [])

  const refresh = useCallback(async () => {
    if (!isAuthenticated) {
      setNotifications([])
      return
    }

    setLoading(true)
    try {
      const response = await notificationService.getAll()
      setNotifications(response.notifications)
    } catch (error) {
      showError(error instanceof Error ? error.message : 'Failed to load notifications')
    } finally {
      setLoading(false)
    }
  }, [isAuthenticated, showError])

  const markAsRead = useCallback(async (id: string) => {
    setNotifications((previous) => previous.map((item) => (item.id === id ? { ...item, isRead: true } : item)))
    try {
      await notificationService.markAsRead(id)
    } catch (error) {
      showError(error instanceof Error ? error.message : 'Failed to mark notification as read')
      await refresh()
    }
  }, [refresh, showError])

  const markAllAsRead = useCallback(async () => {
    setNotifications((previous) => previous.map((item) => ({ ...item, isRead: true })))
    try {
      await notificationService.markAllAsRead()
    } catch (error) {
      showError(error instanceof Error ? error.message : 'Failed to mark all notifications as read')
      await refresh()
    }
  }, [refresh, showError])

  const deleteOne = useCallback(async (id: string) => {
    setNotifications((previous) => previous.filter((item) => item.id !== id))
    try {
      await notificationService.deleteOne(id)
    } catch (error) {
      showError(error instanceof Error ? error.message : 'Failed to delete notification')
      await refresh()
    }
  }, [refresh, showError])

  useEffect(() => {
    void refresh()
  }, [refresh])

  useEffect(() => {
    if (!isAuthenticated || !user?.userId) {
      stompClientRef.current?.deactivate()
      stompClientRef.current = null
      return
    }

    const token = tokenStore.getAccessToken()
    if (!token) {
      return
    }

    const client = new Client({
      webSocketFactory: () => new SockJS('/api/ws'),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        client.subscribe(`/topic/notifications/${user.userId}`, (message) => {
          const event = JSON.parse(message.body) as NotificationSocketEvent

          if (event.action === 'UPSERT' && event.notification) {
            mergeNotification(event.notification)
            if (!event.notification.isRead) {
              playNotificationTone()
            }
            return
          }

          if (event.action === 'DELETE' && event.notificationId) {
            removeNotification(event.notificationId)
            return
          }

          if (event.action === 'ALL_READ') {
            setNotifications((previous) => previous.map((item) => ({ ...item, isRead: true })))
          }
        })
      },
      onStompError: () => {
        showError('Realtime notification connection error')
      },
      onWebSocketError: () => {
        showError('Realtime notification websocket unavailable')
      },
    })

    client.activate()
    stompClientRef.current = client

    return () => {
      client.deactivate()
      stompClientRef.current = null
    }
  }, [isAuthenticated, mergeNotification, playNotificationTone, removeNotification, showError, user?.userId])

  const value = useMemo<NotificationsContextValue>(() => ({
    notifications,
    unreadCount,
    loading,
    markAsRead,
    markAllAsRead,
    deleteOne,
    refresh,
  }), [deleteOne, loading, markAllAsRead, markAsRead, notifications, refresh, unreadCount])

  return <NotificationsContext.Provider value={value}>{children}</NotificationsContext.Provider>
}

export function useNotifications() {
  const context = useContext(NotificationsContext)
  if (!context) {
    throw new Error('useNotifications must be used within NotificationsProvider')
  }
  return context
}

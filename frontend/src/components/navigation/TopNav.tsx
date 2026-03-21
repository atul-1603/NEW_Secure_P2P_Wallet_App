import { AnimatePresence, motion } from 'framer-motion'
import { Bell, LogOut, Menu, Trash2 } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { useNotifications } from '../../theme/NotificationsProvider'
import { formatDateTime } from '../../utils/format'
import { Button } from '../ui/button'
import { ThemeToggle } from '../ui/theme-toggle'

type TopNavProps = {
  username?: string
  onLogout: () => void
  onToggleSidebar: () => void
}

const routeTitleMap: Record<string, string> = {
  '/dashboard': 'Dashboard',
  '/wallet': 'Wallet',
  '/send-money': 'Send Money',
  '/contacts': 'Contacts',
  '/add-money': 'Add Money',
  '/receive': 'Receive Money',
  '/receive-money': 'Receive Money',
  '/withdraw': 'Withdraw',
  '/notifications': 'Notifications',
  '/transactions': 'Transactions',
  '/analytics': 'Analytics',
  '/profile': 'Profile',
  '/security': 'Security',
  '/settings': 'Settings',
}

export function TopNav({ username, onLogout, onToggleSidebar }: TopNavProps) {
  const location = useLocation()
  const [open, setOpen] = useState(false)
  const { notifications, unreadCount, loading, markAsRead, markAllAsRead, deleteOne } = useNotifications()
  const pageTitle = routeTitleMap[location.pathname] ?? 'Wallet Console'
  const latest = useMemo(() => notifications.slice(0, 8), [notifications])

  return (
    <header className="sticky top-0 z-20 flex h-16 items-center justify-between border-b bg-background/90 px-4 backdrop-blur lg:px-8">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="icon" className="lg:hidden" aria-label="Menu" onClick={onToggleSidebar}>
          <Menu className="h-5 w-5" />
        </Button>
        <div>
          <p className="text-xs uppercase tracking-wide text-muted-foreground">Wallet</p>
          <h2 className="text-sm font-semibold sm:text-base">{pageTitle}</h2>
        </div>
      </div>

      <div className="flex items-center gap-2 sm:gap-4">
        <ThemeToggle variant="ghost" />
        <div className="relative">
          <Button variant="ghost" size="icon" aria-label="Notifications" onClick={() => setOpen((value) => !value)}>
            <Bell className="h-4 w-4" />
            {unreadCount > 0 ? (
              <span className="absolute -right-1 -top-1 flex min-h-4 min-w-4 items-center justify-center rounded-full bg-rose-500 px-1 text-[10px] font-semibold text-white">
                {unreadCount > 9 ? '9+' : unreadCount}
              </span>
            ) : null}
          </Button>

          <AnimatePresence>
            {open ? (
              <motion.div
                className="absolute right-0 z-50 mt-2 w-[360px] rounded-2xl border bg-card p-3 shadow-fintech"
                initial={{ opacity: 0, y: 8, scale: 0.98 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: 6, scale: 0.98 }}
              >
                <div className="mb-2 flex items-center justify-between">
                  <p className="text-sm font-semibold">Notifications</p>
                  <Button variant="ghost" size="sm" disabled={unreadCount === 0} onClick={() => void markAllAsRead()}>
                    Mark all read
                  </Button>
                </div>

                {loading ? (
                  <div className="space-y-2 py-1">
                    <div className="h-14 animate-pulse rounded-xl bg-muted/60" />
                    <div className="h-14 animate-pulse rounded-xl bg-muted/60" />
                    <div className="h-14 animate-pulse rounded-xl bg-muted/60" />
                  </div>
                ) : latest.length === 0 ? (
                  <div className="rounded-xl border border-dashed p-4 text-center text-xs text-muted-foreground">
                    No notifications yet.
                  </div>
                ) : (
                  <div className="max-h-[420px] space-y-2 overflow-auto pr-1">
                    {latest.map((item) => (
                      <div
                        key={item.id}
                        role="button"
                        tabIndex={0}
                        className={`w-full rounded-xl border p-3 text-left transition hover:border-primary/40 hover:bg-muted/40 ${
                          item.isRead ? 'bg-background text-muted-foreground' : 'bg-blue-50/50 font-medium text-foreground'
                        }`}
                        onClick={() => void markAsRead(item.id)}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter' || event.key === ' ') {
                            event.preventDefault()
                            void markAsRead(item.id)
                          }
                        }}
                      >
                        <div className="flex items-start justify-between gap-2">
                          <div>
                            <p className="text-sm">{item.title}</p>
                            <p className="text-xs">{item.message}</p>
                            <p className="pt-1 text-[11px] text-muted-foreground">{formatDateTime(item.createdAt)}</p>
                          </div>
                          <button
                            type="button"
                            className="rounded-md p-1 text-muted-foreground hover:bg-muted"
                            onClick={(event) => {
                              event.stopPropagation()
                              void deleteOne(item.id)
                            }}
                            aria-label="Delete notification"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </motion.div>
            ) : null}
          </AnimatePresence>
        </div>
        <div className="hidden text-right sm:block">
          <p className="text-xs text-muted-foreground">Signed in as</p>
          <p className="text-sm font-medium">{username ?? 'User'}</p>
        </div>
        <Button variant="outline" size="sm" onClick={onLogout}>
          <LogOut className="mr-1 h-4 w-4" />
          Logout
        </Button>
      </div>
    </header>
  )
}

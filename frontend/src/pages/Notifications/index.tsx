import { motion } from 'framer-motion'
import { BellRing, Trash2 } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Badge } from '../../components/ui/badge'
import { Button } from '../../components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../../components/ui/card'
import { EmptyState } from '../../components/ui/page-state'
import { useNotifications } from '../../theme/NotificationsProvider'
import { formatDateTime } from '../../utils/format'

type FilterType = 'ALL' | 'TRANSACTIONS' | 'SECURITY'

export default function NotificationsPage() {
  const { notifications, unreadCount, loading, markAsRead, markAllAsRead, deleteOne } = useNotifications()
  const [filter, setFilter] = useState<FilterType>('ALL')

  const filtered = useMemo(() => {
    if (filter === 'ALL') {
      return notifications
    }

    if (filter === 'TRANSACTIONS') {
      return notifications.filter((item) => ['CREDIT', 'DEBIT', 'WITHDRAWAL'].includes(item.type.toUpperCase()))
    }

    return notifications.filter((item) => ['LOGIN', 'SECURITY'].includes(item.type.toUpperCase()))
  }, [filter, notifications])

  return (
    <motion.div
      className="space-y-6"
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25 }}
    >
      <section className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Notifications</h1>
          <p className="text-sm text-muted-foreground">Realtime alert center with persisted history.</p>
        </div>
        <div className="flex items-center gap-2">
          <Badge variant="outline">Unread: {unreadCount}</Badge>
          <Button variant="outline" onClick={() => void markAllAsRead()} disabled={unreadCount === 0}>
            Mark all read
          </Button>
        </div>
      </section>

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between gap-2">
            <CardTitle className="text-base">Filters</CardTitle>
            <BellRing className="h-4 w-4 text-primary" />
          </div>
          <CardDescription>View alerts by category.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-wrap gap-2">
          {(['ALL', 'TRANSACTIONS', 'SECURITY'] as const).map((value) => (
            <Button
              key={value}
              variant={filter === value ? 'default' : 'outline'}
              size="sm"
              onClick={() => setFilter(value)}
            >
              {value}
            </Button>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">History</CardTitle>
          <CardDescription>Latest notifications first, synced from realtime channel and database.</CardDescription>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-2">
              <div className="h-16 animate-pulse rounded-xl bg-muted/60" />
              <div className="h-16 animate-pulse rounded-xl bg-muted/60" />
              <div className="h-16 animate-pulse rounded-xl bg-muted/60" />
            </div>
          ) : filtered.length === 0 ? (
            <EmptyState title="No notifications" description="Alerts will appear here as account events happen." />
          ) : (
            <div className="space-y-2">
              {filtered.map((item) => (
                <div
                  key={item.id}
                  className={`rounded-xl border p-3 transition ${item.isRead ? 'bg-background text-muted-foreground' : 'bg-blue-50/50 text-foreground'}`}
                  role="button"
                  tabIndex={0}
                  onClick={() => void markAsRead(item.id)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault()
                      void markAsRead(item.id)
                    }
                  }}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <div className="flex items-center gap-2">
                        <p className="text-sm font-medium">{item.title}</p>
                        {!item.isRead ? <Badge className="bg-emerald-100 text-emerald-700">New</Badge> : null}
                      </div>
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
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </motion.div>
  )
}

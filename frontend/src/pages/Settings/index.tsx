import { motion } from 'framer-motion'
import { BellRing, Palette, Save, SlidersHorizontal } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Alert } from '../../components/ui/alert'
import { Button } from '../../components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../../components/ui/card'
import { Input } from '../../components/ui/input'
import { useToast } from '../../components/ui/toast'
import { useProfileQuery, useUpdateProfileMutation } from '../../hooks/useProfileData'
import { preferencesService } from '../../services/preferences.service'
import { getApiErrorMessage } from '../../utils/error'

type PreferenceState = {
  displayName: string
  emailNotifications: boolean
  transactionNotifications: boolean
  securityAlerts: boolean
}

export default function SettingsPage() {
  const { showError, showSuccess } = useToast()
  const profileQuery = useProfileQuery()
  const updateProfileMutation = useUpdateProfileMutation()

  const [preferences, setPreferences] = useState<PreferenceState>({
    displayName: 'Wallet User',
    emailNotifications: true,
    transactionNotifications: true,
    securityAlerts: true,
  })

  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [savedMessage, setSavedMessage] = useState<string | null>(null)

  useEffect(() => {
    let mounted = true

    async function loadData() {
      if (!profileQuery.data) {
        return
      }

      setLoading(true)
      try {
        const settings = await preferencesService.getPreferences()
        if (!mounted) {
          return
        }

        setPreferences({
          displayName: profileQuery.data.fullName || 'Wallet User',
          emailNotifications: settings.emailNotifications,
          transactionNotifications: settings.transactionNotifications,
          securityAlerts: settings.securityAlerts,
        })
      } catch (error) {
        showError(getApiErrorMessage(error, 'Unable to load settings'))
      } finally {
        if (mounted) {
          setLoading(false)
        }
      }
    }

    void loadData()
    return () => {
      mounted = false
    }
  }, [profileQuery.data, showError])

  async function savePreferences() {
    setSaving(true)
    setSavedMessage(null)

    try {
      await Promise.all([
        updateProfileMutation.mutateAsync({
          fullName: preferences.displayName,
        }),
        preferencesService.updatePreferences({
          emailNotifications: preferences.emailNotifications,
          transactionNotifications: preferences.transactionNotifications,
          securityAlerts: preferences.securityAlerts,
        }),
      ])

      setSavedMessage('Preferences saved successfully.')
      showSuccess('Settings updated')
    } catch (error) {
      showError(getApiErrorMessage(error, 'Failed to save preferences'))
    } finally {
      setSaving(false)
    }
  }

  if (loading || profileQuery.isLoading) {
    return (
      <div className="space-y-4">
        <div className="h-8 w-40 animate-pulse rounded bg-muted/60" />
        <div className="h-28 animate-pulse rounded-xl bg-muted/60" />
        <div className="h-28 animate-pulse rounded-xl bg-muted/60" />
      </div>
    )
  }

  return (
    <motion.div
      className="space-y-6"
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25 }}
    >
      <section>
        <h1 className="text-2xl font-semibold">Settings</h1>
        <p className="text-sm text-muted-foreground">Manage interface preferences and operational alerts.</p>
      </section>

      {savedMessage ? <Alert className="border-emerald-200 bg-emerald-50 text-emerald-700">{savedMessage}</Alert> : null}

      <section className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle className="text-base">Profile Preferences</CardTitle>
              <SlidersHorizontal className="h-4 w-4 text-primary" />
            </div>
            <CardDescription>Control naming and notification defaults.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-1">
              <label className="text-sm font-medium">Display Name</label>
              <Input
                value={preferences.displayName}
                onChange={(event) => setPreferences((value) => ({ ...value, displayName: event.target.value }))}
              />
            </div>

            <div className="rounded-xl border bg-background p-3 text-xs text-muted-foreground">
              Settlement currency is standardized platform-wide to INR.
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle className="text-base">Notification Controls</CardTitle>
              <BellRing className="h-4 w-4 text-primary" />
            </div>
            <CardDescription>Choose which events trigger notifications.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <label className="flex items-center justify-between rounded-xl border bg-background p-3 text-sm">
              <span>Email alerts</span>
              <input
                type="checkbox"
                checked={preferences.emailNotifications}
                onChange={(event) =>
                  setPreferences((value) => ({
                    ...value,
                    emailNotifications: event.target.checked,
                  }))
                }
              />
            </label>

            <label className="flex items-center justify-between rounded-xl border bg-background p-3 text-sm">
              <span>Transfer notifications</span>
              <input
                type="checkbox"
                checked={preferences.transactionNotifications}
                onChange={(event) =>
                  setPreferences((value) => ({
                    ...value,
                    transactionNotifications: event.target.checked,
                  }))
                }
              />
            </label>

            <label className="flex items-center justify-between rounded-xl border bg-background p-3 text-sm">
              <span>Security alerts</span>
              <input
                type="checkbox"
                checked={preferences.securityAlerts}
                onChange={(event) =>
                  setPreferences((value) => ({
                    ...value,
                    securityAlerts: event.target.checked,
                  }))
                }
              />
            </label>

            <div className="rounded-xl border bg-background p-3 text-xs text-muted-foreground">
              Notification settings are currently client-side and can be connected to backend preferences when available.
            </div>
          </CardContent>
        </Card>
      </section>

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="text-base">Appearance</CardTitle>
            <Palette className="h-4 w-4 text-primary" />
          </div>
          <CardDescription>Modern card-based UI is active using Tailwind and shared design tokens.</CardDescription>
        </CardHeader>
        <CardContent className="flex justify-end">
          <Button onClick={() => void savePreferences()} disabled={saving}>
            <Save className="mr-1 h-4 w-4" />
            {saving ? 'Saving…' : 'Save Preferences'}
          </Button>
        </CardContent>
      </Card>
    </motion.div>
  )
}

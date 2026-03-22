import { motion } from 'framer-motion'
import { ArrowRightLeft, Search, UserCircle2 } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { CameraQrScanner } from '../../components/wallet/CameraQrScanner'
import { Alert } from '../../components/ui/alert'
import { Button } from '../../components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../../components/ui/card'
import { Input } from '../../components/ui/input'
import { PageError, PageLoading } from '../../components/ui/page-state'
import { useToast } from '../../components/ui/toast'
import { useContactsQuery } from '../../hooks/useContactsData'
import { useHistoryQuery, useTransferMutation, useWalletQuery } from '../../hooks/useDashboardData'
import { userService } from '../../services/user.service'
import type { ContactResponse, UserSearchItemResponse } from '../../types/api'
import { getApiErrorMessage } from '../../utils/error'
import { formatCurrency, formatDateTime } from '../../utils/format'

const walletUuidRegex = /[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/i

function extractWalletId(value: string): string | null {
  const normalizedValue = value.trim()

  if (!normalizedValue) {
    return null
  }

  if (walletUuidRegex.test(normalizedValue)) {
    return normalizedValue.match(walletUuidRegex)?.[0] ?? null
  }

  return null
}

export default function SendMoneyPage() {
  const walletQuery = useWalletQuery()
  const historyQuery = useHistoryQuery()
  const contactsQuery = useContactsQuery('')
  const transferMutation = useTransferMutation()
  const [searchParams, setSearchParams] = useSearchParams()

  const [receiverEmail, setReceiverEmail] = useState('')
  const [receiverName, setReceiverName] = useState('')
  const [contactFilter, setContactFilter] = useState('')
  const [suggestions, setSuggestions] = useState<UserSearchItemResponse[]>([])
  const [advancedWalletId, setAdvancedWalletId] = useState(searchParams.get('to')?.trim() || '')

  const [amount, setAmount] = useState<number>(0)
  const [reference, setReference] = useState('')
  const [note, setNote] = useState('')

  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [scanMessage, setScanMessage] = useState<string | null>(null)
  const [aiHintMessage, setAiHintMessage] = useState<string | null>(null)
  const [searchLoading, setSearchLoading] = useState(false)

  const { showError } = useToast()

  const wallet = walletQuery.data ?? null
  const contacts = contactsQuery.data ?? []
  const history = historyQuery.data ?? []

  const loading = walletQuery.isLoading || contactsQuery.isLoading
  const error = [walletQuery.error, contactsQuery.error, transferMutation.error]
    .filter(Boolean)
    .map((item) => getApiErrorMessage(item, 'Unable to process transfer'))[0]

  const quickContacts = useMemo(() => contacts.slice(0, 4), [contacts])

  const filteredContacts = useMemo(() => {
    if (!contactFilter.trim()) {
      return contacts
    }

    const normalized = contactFilter.trim().toLowerCase()
    return contacts.filter((contact) =>
      contact.contactName.toLowerCase().includes(normalized)
      || contact.contactEmail.toLowerCase().includes(normalized),
    )
  }, [contactFilter, contacts])

  useEffect(() => {
    const aiReceiverEmail = searchParams.get('ai_receiverEmail')?.trim()
    const aiReceiverName = searchParams.get('ai_receiverName')?.trim()
    const aiAmount = searchParams.get('ai_amount')?.trim()
    const aiNote = searchParams.get('ai_note')?.trim()
    const aiReference = searchParams.get('ai_reference')?.trim()
    const aiWalletId = searchParams.get('ai_toWalletId')?.trim()

    let touched = false

    if (aiReceiverEmail) {
      setReceiverEmail(aiReceiverEmail)
      touched = true
    }

    if (aiReceiverName) {
      setReceiverName(aiReceiverName)
      touched = true
    }

    if (aiAmount) {
      const parsedAmount = Number(aiAmount)
      if (Number.isFinite(parsedAmount) && parsedAmount > 0) {
        setAmount(parsedAmount)
        touched = true
      }
    }

    if (aiNote) {
      setNote(aiNote)
      touched = true
    }

    if (aiReference) {
      setReference(aiReference)
      touched = true
    }

    if (aiWalletId) {
      setAdvancedWalletId(aiWalletId)
      touched = true
    }

    if (touched) {
      setAiHintMessage('Assistant prefilled the transfer form. Please review and submit manually.')
      const nextParams = new URLSearchParams(searchParams)
      nextParams.delete('ai_receiverEmail')
      nextParams.delete('ai_receiverName')
      nextParams.delete('ai_amount')
      nextParams.delete('ai_note')
      nextParams.delete('ai_reference')
      nextParams.delete('ai_toWalletId')
      setSearchParams(nextParams, { replace: true })
    }
  }, [searchParams, setSearchParams])

  useEffect(() => {
    const normalized = receiverEmail.trim()

    if (normalized.length < 3) {
      setSuggestions([])
      return
    }

    const timeoutId = window.setTimeout(async () => {
      setSearchLoading(true)
      try {
        const result = await userService.searchUsers(normalized)
        setSuggestions(result)
      } catch {
        setSuggestions([])
      } finally {
        setSearchLoading(false)
      }
    }, 250)

    return () => window.clearTimeout(timeoutId)
  }, [receiverEmail])

  function selectContact(contact: ContactResponse) {
    setReceiverEmail(contact.contactEmail)
    setReceiverName(contact.contactName)
  }

  function selectSuggestedUser(user: UserSearchItemResponse) {
    setReceiverEmail(user.email)
    setReceiverName(user.fullName)
    setSuggestions([])
  }

  function handleScannedWalletId(value: string) {
    const normalizedValue = extractWalletId(value)

    if (!normalizedValue) {
      showError('Scanned QR does not contain a valid wallet ID.')
      return
    }

    const nextParams = new URLSearchParams(searchParams)
    nextParams.set('to', normalizedValue)
    setSearchParams(nextParams, { replace: true })

    setAdvancedWalletId(normalizedValue)
    setScanMessage(`Wallet ID prefilled from QR: ${normalizedValue}`)
  }

  async function submitTransfer() {
    setSuccessMessage(null)

    if (!wallet) {
      showError('Create a wallet before transferring funds.')
      return
    }

    const normalizedEmail = receiverEmail.trim().toLowerCase()
    const normalizedWalletId = advancedWalletId.trim()

    if (!normalizedEmail && !normalizedWalletId) {
      showError('Select a contact, enter recipient email, or provide wallet ID.')
      return
    }

    if (!Number.isFinite(amount) || amount <= 0) {
      showError('Amount must be greater than zero.')
      return
    }

    try {
      await transferMutation.mutateAsync({
        receiverEmail: normalizedEmail || undefined,
        toWalletId: normalizedWalletId || undefined,
        amount,
        reference: reference.trim() || undefined,
        note: note.trim() || undefined,
      })

      setSuccessMessage(`Transfer completed successfully for ${formatCurrency(amount)}.`)
      setAmount(0)
      setReference('')
      setNote('')
    } catch (mutationError) {
      showError(getApiErrorMessage(mutationError, 'Transfer failed. Verify recipient and balance.'))
    }
  }

  if (loading) {
    return <PageLoading title="Loading transfer workspace…" />
  }

  if (error && !wallet) {
    return <PageError message={error} onRetry={() => {
      void walletQuery.refetch()
      void contactsQuery.refetch()
    }} />
  }

  return (
    <motion.div
      className="space-y-6"
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25 }}
    >
      <section className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Send Money</h1>
          <p className="text-sm text-muted-foreground">
            Select from contacts or enter recipient email for secure payments.
          </p>
        </div>
        <Button variant="outline" onClick={() => {
          void walletQuery.refetch()
          void contactsQuery.refetch()
        }}>
          Refresh Data
        </Button>
      </section>

      {successMessage ? <Alert className="border-emerald-200 bg-emerald-50 text-emerald-700">{successMessage}</Alert> : null}
      {error && wallet ? <Alert>{error}</Alert> : null}
      {scanMessage ? <Alert className="border-blue-200 bg-blue-50 text-blue-700">{scanMessage}</Alert> : null}
      {aiHintMessage ? <Alert className="border-cyan-200 bg-cyan-50 text-cyan-700">{aiHintMessage}</Alert> : null}

      {!wallet ? (
        <Card>
          <CardHeader>
            <div className="flex items-center gap-2">
              <ArrowRightLeft className="h-4 w-4 text-primary" />
              <CardTitle className="text-base">Wallet Required</CardTitle>
            </div>
            <CardDescription>Create a wallet before initiating transfers.</CardDescription>
          </CardHeader>
          <CardContent>
            <Button asChild>
              <Link to="/wallet">Go to Wallet Setup</Link>
            </Button>
          </CardContent>
        </Card>
      ) : (
        <section className="grid gap-6 xl:grid-cols-3">
          <div className="space-y-6 xl:col-span-2">
            <Card>
              <CardHeader>
                <CardTitle className="text-base">Quick Send</CardTitle>
                <CardDescription>Frequent contacts for one-tap recipient selection.</CardDescription>
              </CardHeader>
              <CardContent className="flex flex-wrap gap-2">
                {quickContacts.length === 0 ? (
                  <Alert>
                    No contacts found. Add contacts from the Contacts page for faster payments.
                  </Alert>
                ) : (
                  quickContacts.map((contact) => (
                    <Button
                      key={contact.id}
                      variant="outline"
                      size="sm"
                      onClick={() => selectContact(contact)}
                    >
                      {contact.contactName}
                    </Button>
                  ))
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-base">Recipient</CardTitle>
                <CardDescription>Pick from contacts or enter recipient email manually.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-1">
                  <label className="text-sm font-medium">Search Contacts</label>
                  <Input
                    placeholder="Search contact by name or email"
                    value={contactFilter}
                    onChange={(event) => setContactFilter(event.target.value)}
                  />
                  <div className="max-h-40 space-y-2 overflow-auto rounded-xl border bg-background p-2">
                    {filteredContacts.length === 0 ? (
                      <p className="text-xs text-muted-foreground">No matching contacts.</p>
                    ) : (
                      filteredContacts.slice(0, 8).map((contact) => (
                        <button
                          key={contact.id}
                          type="button"
                          className="flex w-full items-center justify-between rounded-lg px-2 py-1.5 text-left text-sm hover:bg-muted"
                          onClick={() => selectContact(contact)}
                        >
                          <span className="font-medium">{contact.contactName}</span>
                          <span className="text-xs text-muted-foreground">{contact.contactEmail}</span>
                        </button>
                      ))
                    )}
                  </div>
                </div>

                <div className="space-y-1">
                  <label className="text-sm font-medium">Recipient Email</label>
                  <Input
                    type="email"
                    placeholder="recipient@example.com"
                    value={receiverEmail}
                    onChange={(event) => setReceiverEmail(event.target.value)}
                  />
                  <div className="rounded-xl border bg-background p-2">
                    {searchLoading ? (
                      <p className="text-xs text-muted-foreground">Searching users…</p>
                    ) : suggestions.length > 0 ? (
                      <div className="space-y-1">
                        {suggestions.slice(0, 6).map((user) => (
                          <button
                            key={user.userId}
                            type="button"
                            className="flex w-full items-center justify-between rounded-lg px-2 py-1.5 text-left text-sm hover:bg-muted"
                            onClick={() => selectSuggestedUser(user)}
                          >
                            <span className="font-medium">{user.fullName}</span>
                            <span className="text-xs text-muted-foreground">{user.email}</span>
                          </button>
                        ))}
                      </div>
                    ) : (
                      <p className="text-xs text-muted-foreground">Search by email or name for autofill.</p>
                    )}
                  </div>
                </div>

                {(receiverEmail || receiverName) ? (
                  <div className="flex items-center gap-3 rounded-xl border bg-background p-3">
                    <div className="flex h-10 w-10 items-center justify-center rounded-full border bg-muted">
                      <UserCircle2 className="h-6 w-6 text-muted-foreground" />
                    </div>
                    <div>
                      <p className="text-sm font-medium">{receiverName || 'Recipient'}</p>
                      <p className="text-xs text-muted-foreground">{receiverEmail}</p>
                    </div>
                  </div>
                ) : null}

                <div className="space-y-1">
                  <label className="text-sm font-medium">Amount (INR)</label>
                  <Input
                    type="number"
                    min="0"
                    step="0.01"
                    value={amount || ''}
                    onChange={(event) => setAmount(Number(event.target.value))}
                  />
                </div>

                <div className="space-y-1">
                  <label className="text-sm font-medium">Reference (optional)</label>
                  <Input
                    placeholder="Invoice # / context"
                    value={reference}
                    onChange={(event) => setReference(event.target.value)}
                  />
                </div>

                <div className="space-y-1">
                  <label className="text-sm font-medium">Note (optional)</label>
                  <Input
                    placeholder="Add a note"
                    value={note}
                    onChange={(event) => setNote(event.target.value)}
                  />
                </div>

                <div className="space-y-1 rounded-xl border bg-muted/30 p-3">
                  <label className="text-xs font-medium text-muted-foreground">Advanced: Receiver Wallet ID (optional)</label>
                  <Input
                    placeholder="Use only if email lookup is unavailable"
                    value={advancedWalletId}
                    onChange={(event) => setAdvancedWalletId(event.target.value)}
                  />
                </div>

                <Button className="w-full" onClick={() => void submitTransfer()} disabled={transferMutation.isPending}>
                  {transferMutation.isPending ? 'Sending…' : 'Send Money'}
                </Button>
              </CardContent>
            </Card>
          </div>

          <div className="space-y-6">
            <CameraQrScanner onScan={handleScannedWalletId} />

            <Card>
              <CardHeader>
                <div className="flex items-center gap-2">
                  <Search className="h-4 w-4 text-primary" />
                  <CardTitle className="text-base">Transfer Context</CardTitle>
                </div>
                <CardDescription>Realtime wallet and recent activity context.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3 text-sm">
                <div className="rounded-xl border bg-background p-3">
                  <p className="text-xs text-muted-foreground">Current Balance</p>
                  <p className="text-lg font-semibold">{formatCurrency(wallet.balance)}</p>
                </div>
                <div className="rounded-xl border bg-background p-3">
                  <p className="text-xs text-muted-foreground">Recent Transfers</p>
                  <p className="text-sm font-medium">{history.length} total entries</p>
                  {history[0] ? (
                    <p className="mt-1 text-xs text-muted-foreground">Latest: {formatDateTime(history[0].createdAt)}</p>
                  ) : null}
                </div>
                <Button asChild variant="outline" className="w-full">
                  <Link to="/contacts">Manage Contacts</Link>
                </Button>
              </CardContent>
            </Card>
          </div>
        </section>
      )}
    </motion.div>
  )
}

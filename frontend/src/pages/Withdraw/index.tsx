import { zodResolver } from '@hookform/resolvers/zod'
import { AnimatePresence, motion } from 'framer-motion'
import { Landmark, Loader2, Plus, RefreshCcw, SendHorizontal } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useSearchParams } from 'react-router-dom'
import { z } from 'zod'
import { Alert } from '../../components/ui/alert'
import { Badge } from '../../components/ui/badge'
import { Button } from '../../components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../../components/ui/card'
import { Input } from '../../components/ui/input'
import { EmptyState, PageError, PageLoading } from '../../components/ui/page-state'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../../components/ui/table'
import { useToast } from '../../components/ui/toast'
import { useWalletQuery } from '../../hooks/useDashboardData'
import {
  useAddBankAccountMutation,
  useBankAccountsQuery,
  useCreateWithdrawalMutation,
  useWithdrawalHistoryQuery,
} from '../../hooks/useWithdrawData'
import { getApiErrorMessage } from '../../utils/error'
import { formatCurrency, formatDateTime } from '../../utils/format'

const addBankSchema = z.object({
  accountHolderName: z.string().min(2, 'Account holder name is required').max(120),
  accountNumber: z.string().regex(/^[0-9]{9,18}$/, 'Account number must be 9 to 18 digits'),
  ifscCode: z.string().regex(/^[A-Za-z]{4}0[A-Za-z0-9]{6}$/, 'Enter a valid IFSC code'),
  bankName: z.string().min(2, 'Bank name is required').max(120),
})

const withdrawSchema = z.object({
  bankAccountId: z.string().uuid('Select a valid bank account'),
  amount: z.number().positive('Amount must be greater than zero').max(1000000, 'Amount is too large'),
})

type AddBankValues = z.infer<typeof addBankSchema>
type WithdrawValues = z.infer<typeof withdrawSchema>

const AI_ACTION_EVENT = 'wallet-ai-action'

function getStatusClasses(status: string) {
  const normalized = status.toUpperCase()

  if (normalized === 'PENDING') {
    return 'border-amber-200 bg-amber-50 text-amber-700'
  }

  if (normalized === 'PROCESSING') {
    return 'border-sky-200 bg-sky-50 text-sky-700'
  }

  if (normalized === 'SUCCESS') {
    return 'border-emerald-200 bg-emerald-50 text-emerald-700'
  }

  if (normalized === 'FAILED') {
    return 'border-rose-200 bg-rose-50 text-rose-700'
  }

  return 'border-muted bg-muted/20 text-muted-foreground'
}

export default function WithdrawPage() {
  const walletQuery = useWalletQuery()
  const bankAccountsQuery = useBankAccountsQuery()
  const withdrawalHistoryQuery = useWithdrawalHistoryQuery()
  const addBankAccountMutation = useAddBankAccountMutation()
  const createWithdrawalMutation = useCreateWithdrawalMutation()
  const { showError, showSuccess } = useToast()
  const [addBankModalOpen, setAddBankModalOpen] = useState(false)
  const [searchParams, setSearchParams] = useSearchParams()

  const wallet = walletQuery.data
  const bankAccounts = bankAccountsQuery.data ?? []
  const withdrawalHistory = withdrawalHistoryQuery.data ?? []

  const loading = walletQuery.isLoading || bankAccountsQuery.isLoading || withdrawalHistoryQuery.isLoading
  const error = [walletQuery.error, bankAccountsQuery.error, withdrawalHistoryQuery.error]
    .filter(Boolean)
    .map((item) => getApiErrorMessage(item, 'Unable to load withdrawal page'))[0]

  const pendingOrProcessingCount = useMemo(() => {
    return withdrawalHistory.filter((item) => {
      const status = item.status.toUpperCase()
      return status === 'PENDING' || status === 'PROCESSING'
    }).length
  }, [withdrawalHistory])

  const {
    register: registerAddBank,
    handleSubmit: handleSubmitAddBank,
    formState: { errors: addBankErrors, isSubmitting: isAddingBankSubmitting },
    reset: resetAddBank,
  } = useForm<AddBankValues>({
    resolver: zodResolver(addBankSchema),
    defaultValues: {
      accountHolderName: '',
      accountNumber: '',
      ifscCode: '',
      bankName: '',
    },
  })

  const {
    register: registerWithdraw,
    handleSubmit: handleSubmitWithdraw,
    watch,
    formState: { errors: withdrawErrors, isSubmitting: isWithdrawSubmitting },
    reset: resetWithdraw,
  } = useForm<WithdrawValues>({
    resolver: zodResolver(withdrawSchema),
    defaultValues: {
      bankAccountId: '',
      amount: 0,
    },
  })

  const selectedAmount = watch('amount')

  useEffect(() => {
    const aiAmount = searchParams.get('ai_amount')?.trim()
    const aiOpenModal = searchParams.get('ai_openModal')?.trim()

    let touched = false

    if (aiAmount) {
      const parsedAmount = Number(aiAmount)
      if (Number.isFinite(parsedAmount) && parsedAmount > 0) {
        resetWithdraw((current) => ({
          bankAccountId: current?.bankAccountId || '',
          amount: parsedAmount,
        }))
        touched = true
      }
    }

    if (aiOpenModal && aiOpenModal.toLowerCase() === 'add-bank') {
      setAddBankModalOpen(true)
      touched = true
    }

    if (touched) {
      const nextParams = new URLSearchParams(searchParams)
      nextParams.delete('ai_amount')
      nextParams.delete('ai_openModal')
      setSearchParams(nextParams, { replace: true })
      showSuccess('Assistant prepared withdrawal details. Please review before submitting.')
    }
  }, [resetWithdraw, searchParams, setSearchParams, showSuccess])

  const hasInsufficientBalance = !!wallet && Number.isFinite(selectedAmount) && selectedAmount > wallet.balance
  const cannotWithdraw =
    !wallet ||
    wallet.balance <= 0 ||
    bankAccounts.length === 0 ||
    createWithdrawalMutation.isPending ||
    isWithdrawSubmitting ||
    hasInsufficientBalance

  useEffect(() => {
    function onAiAction(event: Event) {
      const customEvent = event as CustomEvent<{ type?: string; payload?: Record<string, string> }>
      const detail = customEvent.detail
      if (!detail || detail.type !== 'OPEN_MODAL') {
        return
      }

      const modalTarget = detail.payload?.modal ?? ''
      if (modalTarget === 'add-bank') {
        setAddBankModalOpen(true)
      }
    }

    window.addEventListener(AI_ACTION_EVENT, onAiAction)
    return () => window.removeEventListener(AI_ACTION_EVENT, onAiAction)
  }, [])

  async function onSubmitAddBank(values: AddBankValues) {
    try {
      await addBankAccountMutation.mutateAsync({
        accountHolderName: values.accountHolderName.trim(),
        accountNumber: values.accountNumber.trim(),
        ifscCode: values.ifscCode.trim().toUpperCase(),
        bankName: values.bankName.trim(),
      })
      showSuccess('Bank account added successfully')
      setAddBankModalOpen(false)
      resetAddBank()
    } catch (submitError) {
      showError(getApiErrorMessage(submitError, 'Unable to add bank account'))
    }
  }

  async function onSubmitWithdraw(values: WithdrawValues) {
    try {
      await createWithdrawalMutation.mutateAsync({
        bankAccountId: values.bankAccountId,
        amount: values.amount,
      })
      showSuccess('Withdrawal submitted. Processing will complete shortly.')
      resetWithdraw({
        bankAccountId: values.bankAccountId,
        amount: 0,
      })
    } catch (submitError) {
      showError(getApiErrorMessage(submitError, 'Withdrawal request failed'))
    }
  }

  if (loading) {
    return <PageLoading title="Loading withdrawal center..." />
  }

  if (error) {
    return (
      <PageError
        message={error}
        onRetry={() => {
          void walletQuery.refetch()
          void bankAccountsQuery.refetch()
          void withdrawalHistoryQuery.refetch()
        }}
      />
    )
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
          <h1 className="text-2xl font-semibold">Withdraw to Bank</h1>
          <p className="text-sm text-muted-foreground">
            Move funds from wallet to your linked bank account with secure processing simulation.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button type="button" variant="outline" onClick={() => setAddBankModalOpen(true)}>
            <Plus className="mr-2 h-4 w-4" /> Add Bank Account
          </Button>
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              void walletQuery.refetch()
              void bankAccountsQuery.refetch()
              void withdrawalHistoryQuery.refetch()
            }}
          >
            <RefreshCcw className="mr-2 h-4 w-4" /> Refresh
          </Button>
        </div>
      </section>

      <section className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <div className="flex items-center justify-between gap-2">
              <CardTitle className="text-base">Withdraw Form</CardTitle>
              <SendHorizontal className="h-4 w-4 text-primary" />
            </div>
            <CardDescription>Select a bank account and submit a withdrawal request.</CardDescription>
          </CardHeader>
          <CardContent>
            <form className="space-y-4" onSubmit={handleSubmitWithdraw(onSubmitWithdraw)}>
              <div className="space-y-1">
                <label className="text-sm font-medium">Bank Account</label>
                <select
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                  {...registerWithdraw('bankAccountId')}
                >
                  <option value="">Select bank account</option>
                  {bankAccounts.map((account) => (
                    <option key={account.id} value={account.id}>
                      {account.bankName} - {account.maskedAccountNumber} ({account.accountHolderName})
                    </option>
                  ))}
                </select>
                {withdrawErrors.bankAccountId ? <p className="text-xs text-destructive">{withdrawErrors.bankAccountId.message}</p> : null}
              </div>

              <div className="space-y-1">
                <label className="text-sm font-medium">Amount (INR)</label>
                <Input type="number" min="0" step="0.01" {...registerWithdraw('amount', { valueAsNumber: true })} />
                {withdrawErrors.amount ? <p className="text-xs text-destructive">{withdrawErrors.amount.message}</p> : null}
              </div>

              <div className="rounded-xl border bg-background p-3">
                <p className="text-xs text-muted-foreground">Available Balance</p>
                <p className="text-lg font-semibold">{formatCurrency(wallet?.balance ?? 0)}</p>
              </div>

              {hasInsufficientBalance ? (
                <Alert className="border-rose-200 bg-rose-50 text-rose-700">
                  Withdrawal amount exceeds available balance.
                </Alert>
              ) : null}

              <Button type="submit" className="w-full" disabled={cannotWithdraw}>
                {createWithdrawalMutation.isPending ? 'Submitting...' : 'Submit Withdrawal'}
              </Button>

              {!wallet || wallet.balance <= 0 ? (
                <p className="text-xs text-muted-foreground">Your wallet has insufficient balance for withdrawal.</p>
              ) : null}
            </form>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <div className="flex items-center justify-between gap-2">
              <CardTitle className="text-base">Linked Bank Accounts</CardTitle>
              <Landmark className="h-4 w-4 text-primary" />
            </div>
            <CardDescription>Accounts available for withdrawal.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2">
            {bankAccounts.length === 0 ? (
              <EmptyState
                title="No bank accounts"
                description="Add a bank account to start withdrawing funds."
              />
            ) : (
              bankAccounts.map((account) => (
                <div key={account.id} className="rounded-xl border bg-background p-3">
                  <p className="text-sm font-medium">{account.bankName}</p>
                  <p className="text-xs text-muted-foreground">{account.accountHolderName}</p>
                  <p className="text-xs text-muted-foreground">{account.maskedAccountNumber} | {account.ifscCode}</p>
                </div>
              ))
            )}
          </CardContent>
        </Card>
      </section>

      <Card>
        <CardHeader>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <CardTitle className="text-base">Withdrawal History</CardTitle>
            <div className="flex items-center gap-2">
              {pendingOrProcessingCount > 0 ? (
                <motion.div
                  className="inline-flex items-center gap-1 rounded-full border border-sky-200 bg-sky-50 px-2 py-1 text-xs text-sky-700"
                  animate={{ opacity: [1, 0.5, 1] }}
                  transition={{ duration: 1.2, repeat: Infinity }}
                >
                  <Loader2 className="h-3 w-3 animate-spin" /> Processing...
                </motion.div>
              ) : null}
              <Badge variant="outline">{withdrawalHistory.length} records</Badge>
            </div>
          </div>
          <CardDescription>Track request status and payout references.</CardDescription>
        </CardHeader>
        <CardContent>
          {withdrawalHistory.length === 0 ? (
            <EmptyState
              title="No withdrawals yet"
              description="Your submitted withdrawals will appear here."
            />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Date</TableHead>
                  <TableHead>Amount</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Bank</TableHead>
                  <TableHead>Reference ID</TableHead>
                  <TableHead>ETA</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {withdrawalHistory.map((item) => {
                  const isInProgress = ['PENDING', 'PROCESSING'].includes(item.status.toUpperCase())

                  return (
                    <TableRow key={item.id}>
                      <TableCell className="whitespace-nowrap text-muted-foreground">{formatDateTime(item.createdAt)}</TableCell>
                      <TableCell className="text-rose-600">-{formatCurrency(item.amount)}</TableCell>
                      <TableCell>
                        <Badge className={getStatusClasses(item.status)}>{item.status}</Badge>
                      </TableCell>
                      <TableCell className="max-w-[220px] truncate">{item.bankName} {item.maskedAccountNumber}</TableCell>
                      <TableCell className="max-w-[260px] truncate">{item.referenceId}</TableCell>
                      <TableCell className="text-xs text-muted-foreground">
                        {isInProgress ? '2-10 sec' : item.processedAt ? 'Completed' : '-'}
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <AnimatePresence>
        {addBankModalOpen ? (
          <motion.div
            className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 p-4"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
          >
            <motion.div
              className="w-full max-w-lg rounded-2xl border bg-card p-6 shadow-fintech"
              initial={{ opacity: 0, y: 12, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 8, scale: 0.98 }}
            >
              <h2 className="text-base font-semibold">Add Bank Account</h2>
              <p className="mt-1 text-sm text-muted-foreground">Details are encrypted before storage.</p>

              <form className="mt-4 space-y-3" onSubmit={handleSubmitAddBank(onSubmitAddBank)}>
                <div className="space-y-1">
                  <label className="text-sm font-medium">Account Holder Name</label>
                  <Input {...registerAddBank('accountHolderName')} />
                  {addBankErrors.accountHolderName ? <p className="text-xs text-destructive">{addBankErrors.accountHolderName.message}</p> : null}
                </div>
                <div className="space-y-1">
                  <label className="text-sm font-medium">Account Number</label>
                  <Input {...registerAddBank('accountNumber')} />
                  {addBankErrors.accountNumber ? <p className="text-xs text-destructive">{addBankErrors.accountNumber.message}</p> : null}
                </div>
                <div className="space-y-1">
                  <label className="text-sm font-medium">IFSC Code</label>
                  <Input {...registerAddBank('ifscCode')} />
                  {addBankErrors.ifscCode ? <p className="text-xs text-destructive">{addBankErrors.ifscCode.message}</p> : null}
                </div>
                <div className="space-y-1">
                  <label className="text-sm font-medium">Bank Name</label>
                  <Input {...registerAddBank('bankName')} />
                  {addBankErrors.bankName ? <p className="text-xs text-destructive">{addBankErrors.bankName.message}</p> : null}
                </div>

                <div className="mt-4 flex justify-end gap-2">
                  <Button type="button" variant="outline" onClick={() => setAddBankModalOpen(false)}>
                    Cancel
                  </Button>
                  <Button type="submit" disabled={addBankAccountMutation.isPending || isAddingBankSubmitting}>
                    {addBankAccountMutation.isPending ? 'Saving...' : 'Save Account'}
                  </Button>
                </div>
              </form>
            </motion.div>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </motion.div>
  )
}

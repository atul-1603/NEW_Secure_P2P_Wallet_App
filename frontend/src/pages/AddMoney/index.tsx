import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import { CheckCircle2, CircleDollarSign, Sparkles } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useSearchParams } from 'react-router-dom'
import { z } from 'zod'
import { Alert } from '../../components/ui/alert'
import { Button } from '../../components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../../components/ui/card'
import { Input } from '../../components/ui/input'
import { PageError } from '../../components/ui/page-state'
import { Skeleton } from '../../components/ui/skeleton'
import { useToast } from '../../components/ui/toast'
import { useWalletQuery } from '../../hooks/useDashboardData'
import { paymentService } from '../../services/payment.service'
import type { CreatePaymentOrderResponse } from '../../types/api'
import { getApiErrorMessage } from '../../utils/error'
import { formatCurrency } from '../../utils/format'

const addMoneySchema = z.object({
  amount: z.number().positive('Amount must be greater than zero').max(1000000, 'Amount is too large'),
  note: z.string().max(120, 'Note is too long').optional(),
})

type AddMoneyValues = z.infer<typeof addMoneySchema>

type RazorpayCompletionPayload = {
  orderId: string
  paymentId: string
  signature: string
}

const quickAmounts = [100, 500, 1000] as const
const razorpayCheckoutScriptSrc = 'https://checkout.razorpay.com/v1/checkout.js'
let razorpayScriptPromise: Promise<void> | null = null

function loadRazorpayCheckoutScript(): Promise<void> {
  if (window.Razorpay) {
    return Promise.resolve()
  }

  if (razorpayScriptPromise) {
    return razorpayScriptPromise
  }

  razorpayScriptPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = razorpayCheckoutScriptSrc
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => {
      razorpayScriptPromise = null
      reject(new Error('Unable to load Razorpay checkout SDK'))
    }
    document.body.appendChild(script)
  })

  return razorpayScriptPromise
}

function openRazorpayCheckout(order: CreatePaymentOrderResponse, note?: string): Promise<RazorpayCompletionPayload> {
  return new Promise((resolve, reject) => {
    if (!window.Razorpay) {
      reject(new Error('Razorpay checkout is unavailable'))
      return
    }

    const checkout = new window.Razorpay({
      key: order.key,
      amount: Math.round(order.amount * 100),
      currency: order.currency,
      order_id: order.orderId,
      name: 'Secure P2P Wallet',
      description: 'Add money to wallet',
      notes: note ? { note } : undefined,
      handler: (response) => {
        resolve({
          orderId: response.razorpay_order_id,
          paymentId: response.razorpay_payment_id,
          signature: response.razorpay_signature,
        })
      },
      modal: {
        ondismiss: () => {
          reject(new Error('Payment was cancelled'))
        },
      },
    })

    checkout.on('payment.failed', (response) => {
      reject(new Error(response.error?.description ?? 'Payment failed'))
    })

    checkout.open()
  })
}

export default function AddMoneyPage() {
  const walletQuery = useWalletQuery()
  const queryClient = useQueryClient()
  const { showError, showSuccess } = useToast()
  const [confirmValues, setConfirmValues] = useState<AddMoneyValues | null>(null)
  const [successAmount, setSuccessAmount] = useState<number | null>(null)
  const [isProcessingPayment, setIsProcessingPayment] = useState(false)
  const [searchParams, setSearchParams] = useSearchParams()

  const wallet = walletQuery.data ?? null

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors, isSubmitting },
    reset,
    watch,
  } = useForm<AddMoneyValues>({
    resolver: zodResolver(addMoneySchema),
    defaultValues: {
      amount: 100,
      note: '',
    },
  })

  const amountValue = watch('amount')

  useEffect(() => {
    const aiAmount = searchParams.get('ai_amount')?.trim()
    const aiNote = searchParams.get('ai_note')?.trim()

    let touched = false

    if (aiAmount) {
      const parsed = Number(aiAmount)
      if (Number.isFinite(parsed) && parsed > 0) {
        setValue('amount', parsed, { shouldValidate: true })
        touched = true
      }
    }

    if (aiNote) {
      setValue('note', aiNote, { shouldValidate: true })
      touched = true
    }

    if (touched) {
      const nextParams = new URLSearchParams(searchParams)
      nextParams.delete('ai_amount')
      nextParams.delete('ai_note')
      setSearchParams(nextParams, { replace: true })
      showSuccess('Assistant prefilled Add Money details. Please confirm before payment.')
    }
  }, [searchParams, setSearchParams, setValue, showSuccess])

  const pageError = useMemo(() => {
    return [walletQuery.error]
      .filter(Boolean)
      .map((item) => getApiErrorMessage(item, 'Unable to open Add Money'))[0]
  }, [walletQuery.error])

  function openConfirmation(values: AddMoneyValues) {
    setConfirmValues(values)
    setSuccessAmount(null)
  }

  async function confirmDeposit() {
    if (!confirmValues) {
      return
    }

    setIsProcessingPayment(true)

    try {
      await loadRazorpayCheckoutScript()

      const order = await paymentService.createOrder({
        amount: confirmValues.amount,
      })

      const checkoutResponse = await openRazorpayCheckout(order, confirmValues.note?.trim() || undefined)

      const verificationResult = await paymentService.verifyPayment({
        orderId: checkoutResponse.orderId,
        paymentId: checkoutResponse.paymentId,
        signature: checkoutResponse.signature,
      })

      if (verificationResult.status !== 'SUCCESS') {
        throw new Error(verificationResult.message || 'Payment verification failed')
      }

      await queryClient.invalidateQueries({ queryKey: ['wallet', 'me'] })
      await queryClient.invalidateQueries({ queryKey: ['transactions', 'history'] })

      setSuccessAmount(confirmValues.amount)
      setConfirmValues(null)
      reset({ amount: 100, note: '' })
      showSuccess('Payment successful. Wallet credited securely.')
    } catch (error) {
      showError(getApiErrorMessage(error, 'Add money failed. Please try again.'))
    } finally {
      setIsProcessingPayment(false)
    }
  }

  if (walletQuery.isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-5 w-80" />
        <Card>
          <CardContent className="space-y-4 pt-6">
            <Skeleton className="h-11 w-full" />
            <div className="grid grid-cols-3 gap-2">
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
            <Skeleton className="h-11 w-full" />
          </CardContent>
        </Card>
      </div>
    )
  }

  if (pageError && !wallet) {
    return <PageError message={pageError} onRetry={() => void walletQuery.refetch()} />
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
          <h1 className="text-2xl font-semibold">Add Money</h1>
          <p className="text-sm text-muted-foreground">
            Deposit funds into your wallet to start instant P2P transfers.
          </p>
        </div>

        <Button variant="outline" onClick={() => void walletQuery.refetch()} disabled={isProcessingPayment}>
          Refresh Wallet
        </Button>
      </section>

      {pageError && wallet ? <Alert>{pageError}</Alert> : null}

      {!wallet ? (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Wallet Required</CardTitle>
            <CardDescription>Create your wallet first, then return here to add funds.</CardDescription>
          </CardHeader>
          <CardContent>
            <Button asChild>
              <Link to="/wallet">Go to Wallet Setup</Link>
            </Button>
          </CardContent>
        </Card>
      ) : (
        <section className="grid gap-6 lg:grid-cols-3">
          <Card className="lg:col-span-2">
            <CardHeader>
              <div className="flex items-center justify-between gap-2">
                <CardTitle className="text-base">Deposit Funds</CardTitle>
                <CircleDollarSign className="h-4 w-4 text-primary" />
              </div>
              <CardDescription>
                Choose amount, confirm transaction, and your wallet updates in real time.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <form className="space-y-4" onSubmit={handleSubmit(openConfirmation)}>
                <div className="space-y-1">
                  <label className="text-sm font-medium">Amount (INR)</label>
                  <Input type="number" min="0" step="0.01" {...register('amount', { valueAsNumber: true })} />
                  {errors.amount ? <p className="text-xs text-destructive">{errors.amount.message}</p> : null}
                </div>

                <div className="grid grid-cols-3 gap-2">
                  {quickAmounts.map((value) => (
                    <Button
                      key={value}
                      type="button"
                      variant={amountValue === value ? 'default' : 'outline'}
                      onClick={() => setValue('amount', value, { shouldValidate: true })}
                      disabled={isProcessingPayment}
                    >
                      {formatCurrency(value)}
                    </Button>
                  ))}
                </div>

                <div className="space-y-1">
                  <label className="text-sm font-medium">Note (optional)</label>
                  <Input placeholder="Monthly top-up" {...register('note')} disabled={isProcessingPayment} />
                  {errors.note ? <p className="text-xs text-destructive">{errors.note.message}</p> : null}
                </div>

                <Button type="submit" className="w-full" disabled={isSubmitting || isProcessingPayment}>
                  Continue to Confirm
                </Button>
              </form>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">Wallet Snapshot</CardTitle>
              <CardDescription>Current available amount before this deposit.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="rounded-xl border bg-background p-4">
                <p className="text-xs text-muted-foreground">Current Balance</p>
                <p className="text-2xl font-bold">{formatCurrency(wallet.balance)}</p>
              </div>
              <div className="rounded-xl border bg-background p-4 text-xs text-muted-foreground">
                Wallet ID
                <p className="truncate pt-1 text-sm font-medium text-foreground">{wallet.walletId}</p>
              </div>
            </CardContent>
          </Card>
        </section>
      )}

      <AnimatePresence>
        {successAmount !== null ? (
          <motion.div
            className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-emerald-700"
            initial={{ opacity: 0, y: 8, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -8, scale: 0.98 }}
          >
            <div className="flex items-center gap-2">
              <motion.div
                initial={{ scale: 0.7, rotate: -10 }}
                animate={{ scale: 1, rotate: 0 }}
                transition={{ type: 'spring', stiffness: 280, damping: 16 }}
              >
                <CheckCircle2 className="h-5 w-5" />
              </motion.div>
              <p className="font-medium">Deposit successful</p>
            </div>
            <p className="mt-1 text-sm">
              {formatCurrency(successAmount)} has been added to your wallet.
            </p>
          </motion.div>
        ) : null}
      </AnimatePresence>

      <AnimatePresence>
        {confirmValues ? (
          <motion.div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 p-4" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
            <motion.div
              className="w-full max-w-md rounded-2xl border bg-card p-6 shadow-fintech"
              initial={{ opacity: 0, y: 12, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 8, scale: 0.98 }}
            >
              <div className="mb-4 flex items-center gap-2">
                <Sparkles className="h-4 w-4 text-primary" />
                <h2 className="text-base font-semibold">Confirm Deposit</h2>
              </div>
              <p className="text-sm text-muted-foreground">
                You are about to add {formatCurrency(confirmValues.amount)} to your wallet.
              </p>

              {confirmValues.note ? (
                <p className="mt-3 rounded-lg border bg-background p-2 text-xs text-muted-foreground">
                  Note: {confirmValues.note}
                </p>
              ) : null}

              <div className="mt-5 flex justify-end gap-2">
                <Button type="button" variant="outline" onClick={() => setConfirmValues(null)} disabled={isProcessingPayment}>
                  Cancel
                </Button>
                <Button type="button" onClick={() => void confirmDeposit()} disabled={isProcessingPayment}>
                  {isProcessingPayment ? 'Processing…' : 'Pay Securely'}
                </Button>
              </div>
            </motion.div>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </motion.div>
  )
}

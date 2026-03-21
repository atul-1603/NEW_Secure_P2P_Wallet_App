import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { CreateBankAccountRequest, CreateWithdrawalRequest } from '../types/api'
import { withdrawalService } from '../services/withdrawal.service'

const queryKeys = {
  bankAccounts: ['bank-accounts'] as const,
  withdrawals: ['withdrawals', 'history'] as const,
  wallet: ['wallet', 'me'] as const,
  transactions: ['transactions', 'history'] as const,
}

export function useBankAccountsQuery() {
  return useQuery({
    queryKey: queryKeys.bankAccounts,
    queryFn: () => withdrawalService.getBankAccounts(),
  })
}

export function useWithdrawalHistoryQuery() {
  return useQuery({
    queryKey: queryKeys.withdrawals,
    queryFn: () => withdrawalService.getWithdrawalHistory(),
    refetchInterval: 5000,
  })
}

export function useAddBankAccountMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (payload: CreateBankAccountRequest) => withdrawalService.addBankAccount(payload),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.bankAccounts })
    },
  })
}

export function useCreateWithdrawalMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (payload: CreateWithdrawalRequest) => withdrawalService.createWithdrawal(payload),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.withdrawals })
      await queryClient.invalidateQueries({ queryKey: queryKeys.wallet })
      await queryClient.invalidateQueries({ queryKey: queryKeys.transactions })
    },
  })
}

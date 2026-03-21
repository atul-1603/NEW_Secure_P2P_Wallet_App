import type {
  BankAccountResponse,
  CreateBankAccountRequest,
  CreateWithdrawalRequest,
  WithdrawalHistoryItem,
} from '../types/api'
import { apiClient } from './apiClient'

export const withdrawalService = {
  async addBankAccount(payload: CreateBankAccountRequest): Promise<BankAccountResponse> {
    const response = await apiClient.post<BankAccountResponse>('/bank-accounts', payload)
    return response.data
  },

  async getBankAccounts(): Promise<BankAccountResponse[]> {
    const response = await apiClient.get<BankAccountResponse[]>('/bank-accounts')
    return response.data
  },

  async createWithdrawal(payload: CreateWithdrawalRequest): Promise<WithdrawalHistoryItem> {
    const response = await apiClient.post<WithdrawalHistoryItem>('/withdraw', payload)
    return response.data
  },

  async getWithdrawalHistory(): Promise<WithdrawalHistoryItem[]> {
    const response = await apiClient.get<WithdrawalHistoryItem[]>('/withdrawals')
    return response.data
  },
}

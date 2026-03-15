import type { TransactionHistoryItem, TransferRequest, TransferResponse } from '../types/api'
import { apiClient } from './apiClient'

export const transactionService = {
  async transfer(payload: TransferRequest): Promise<TransferResponse> {
    const requestPayload = {
      toWalletId: payload.toWalletId,
      amount: payload.amount,
      reference: payload.reference,
      note: payload.note,
    }

    const response = await apiClient.post<TransferResponse>('/transactions/transfer', requestPayload)
    return response.data
  },

  async getHistory(): Promise<TransactionHistoryItem[]> {
    const response = await apiClient.get<TransactionHistoryItem[]>('/transactions/history')
    return response.data
  },
}

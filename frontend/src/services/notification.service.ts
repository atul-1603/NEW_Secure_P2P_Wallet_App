import { apiClient } from './apiClient'
import type { NotificationsResponse } from '../types/api'

export const notificationService = {
  async getAll(): Promise<NotificationsResponse> {
    const response = await apiClient.get<NotificationsResponse>('/notifications')
    return response.data
  },

  async markAsRead(id: string): Promise<void> {
    await apiClient.post(`/notifications/${id}/read`)
  },

  async markAllAsRead(): Promise<void> {
    await apiClient.post('/notifications/read-all')
  },

  async deleteOne(id: string): Promise<void> {
    await apiClient.delete(`/notifications/${id}`)
  },
}

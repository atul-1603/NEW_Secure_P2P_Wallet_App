import { apiClient } from './apiClient'
import type { UpdateUserPreferencesRequest, UserPreferencesResponse } from '../types/api'

export const preferencesService = {
  async getPreferences(): Promise<UserPreferencesResponse> {
    const response = await apiClient.get<UserPreferencesResponse>('/preferences')
    return response.data
  },

  async updatePreferences(payload: UpdateUserPreferencesRequest): Promise<UserPreferencesResponse> {
    const response = await apiClient.put<UserPreferencesResponse>('/preferences', payload)
    return response.data
  },
}

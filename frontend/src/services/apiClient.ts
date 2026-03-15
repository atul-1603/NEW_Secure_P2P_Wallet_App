import axios from 'axios'
import { tokenStore } from './tokenStore'

const baseURL = import.meta.env.VITE_API_BASE_URL ?? '/api'

export const apiClient = axios.create({
  baseURL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 20000,
})

apiClient.interceptors.request.use((config) => {
  const accessToken = tokenStore.getAccessToken()

  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }

  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    // If we get a 401 Unauthorized, the session might have expired or token is invalid
    if (error.response?.status === 401) {
      console.warn('Session expired (401). Clearing tokens and redirecting to login...')
      tokenStore.clear()
      // Use window.location as we're outside of React component / React Router context
      window.location.assign('/login')
    }
    return Promise.reject(error)
  }
)

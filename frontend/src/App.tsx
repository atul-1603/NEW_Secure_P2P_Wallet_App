import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthLayout } from './layouts/AuthLayout'
import { AppLayout } from './layouts/AppLayout'
import { RequireAuth } from './auth/RequireAuth'
import DashboardPage from './pages/Dashboard'
import WalletPage from './pages/Wallet'
import SendMoneyPage from './pages/SendMoney'
import AddMoneyPage from './pages/AddMoney'
import ReceiveMoneyPage from './pages/ReceiveMoney'
import WithdrawPage from './pages/Withdraw'
import TransactionsPage from './pages/Transactions'
import AnalyticsPage from './pages/Analytics'
import SecurityPage from './pages/Security'
import SettingsPage from './pages/Settings'
import NotificationsPage from './pages/Notifications'
import ProfilePage from './pages/Profile'
import ContactsPage from './pages/Contacts'
import LoginPage from './pages/Login'
import RegisterPage from './pages/Register'
import VerifyOtpPage from './pages/VerifyOtp'
import HomePage from './pages/Home'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomePage />} />

        <Route element={<AuthLayout />}>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/verify-otp" element={<VerifyOtpPage />} />
        </Route>

        <Route
          element={
            <RequireAuth>
              <AppLayout />
            </RequireAuth>
          }
        >
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/wallet" element={<WalletPage />} />
          <Route path="/send-money" element={<SendMoneyPage />} />
          <Route path="/add-money" element={<AddMoneyPage />} />
          <Route path="/receive" element={<ReceiveMoneyPage />} />
          <Route path="/receive-money" element={<ReceiveMoneyPage />} />
          <Route path="/withdraw" element={<WithdrawPage />} />
          <Route path="/transactions" element={<TransactionsPage />} />
          <Route path="/analytics" element={<AnalyticsPage />} />
          <Route path="/contacts" element={<ContactsPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/security" element={<SecurityPage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/notifications" element={<NotificationsPage />} />
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App

export interface RegisterRequest {
  username: string
  email: string
  password: string
}

export interface AuthResponse {
  message: string
  userId: string
  username: string
  email: string
  status: string
}

export interface LoginRequest {
  usernameOrEmail: string
  password: string
}

export interface LoginResponse {
  message: string
  userId: string
  username: string
  email: string
  status: string
  otpRequired: boolean
  otpExpiresInSeconds: number
  tokenType: string | null
  accessToken: string | null
  accessTokenExpiresInMs: number
  refreshToken: string | null
  refreshTokenExpiresInMs: number
}

export interface VerifyEmailRequest {
  email: string
  otpCode: string
}

export interface VerifyOtpRequest {
  usernameOrEmail: string
  otpCode: string
}

export interface ResendEmailVerificationRequest {
  email: string
}

export interface MessageResponse {
  message: string
}

export interface CreateWalletRequest {
  currency?: string
}

export interface WalletResponse {
  walletId: string
  userId: string
  balance: number
  currency: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface DepositRequest {
  amount: number
  reference?: string
  note?: string
}

export interface CreatePaymentOrderRequest {
  amount: number
}

export interface CreatePaymentOrderResponse {
  orderId: string
  amount: number
  currency: string
  key: string
}

export interface VerifyPaymentRequest {
  orderId: string
  paymentId: string
  signature: string
}

export interface VerifyPaymentResponse {
  status: string
  message: string
  updatedBalance: number | null
}

export interface ProfilePreferencesResponse {
  emailAlerts: boolean
  transferAlerts: boolean
}

export interface UserProfileResponse {
  userId: string
  username: string
  fullName: string
  email: string
  emailVerified: boolean
  profileImageUrl: string | null
  accountStatus: string
  accountRestricted: boolean
  kycStatus: 'PENDING' | 'VERIFIED' | 'REJECTED' | string
  kycDocumentType: string | null
  kycDocumentUrl: string | null
  twoFactorEnabled: boolean
  createdAt: string
  preferences: ProfilePreferencesResponse
}

export interface ContactUpsertRequest {
  contactName: string
  contactEmail: string
}

export interface ContactResponse {
  id: string
  contactName: string
  contactEmail: string
  createdAt: string
}

export interface UserSearchItemResponse {
  userId: string
  fullName: string
  email: string
  profileImageUrl: string | null
}

export interface UpdateProfileRequest {
  fullName?: string
  emailAlerts?: boolean
  transferAlerts?: boolean
}

export interface UserPreferencesResponse {
  emailNotifications: boolean
  transactionNotifications: boolean
  securityAlerts: boolean
  updatedAt: string
}

export interface UpdateUserPreferencesRequest {
  emailNotifications: boolean
  transactionNotifications: boolean
  securityAlerts: boolean
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

export interface SecuritySettingsResponse {
  twoFactorEnabled: boolean
  emailVerified: boolean
  accountStatus: string
}

export interface UpdateSecuritySettingsRequest {
  twoFactorEnabled: boolean
}

export interface ActiveSessionItemResponse {
  sessionId: string
  userAgent: string
  ipAddress: string
  lastActiveAt: string
  current: boolean
}

export interface KycStatusResponse {
  status: 'PENDING' | 'VERIFIED' | 'REJECTED' | string
  documentType: string | null
  documentUrl: string | null
  updatedAt: string
}

export interface KycUploadResponse {
  status: 'PENDING' | 'VERIFIED' | 'REJECTED' | string
  documentType: string | null
  documentUrl: string | null
  updatedAt: string
  message: string
}

export interface KycReviewRequest {
  status: 'PENDING' | 'VERIFIED' | 'REJECTED'
}

export interface FileUploadResponse {
  fileName: string
  fileUrl: string
  contentType: string
  size: number
  uploadedAt: string
}

export interface DepositResponse {
  transactionId?: string
  walletId?: string
  amount: number
  currency?: string
  status?: string
  reference?: string
  completedAt?: string
  updatedBalance?: number
}

export interface TransferRequest {
  toWalletId?: string
  receiverEmail?: string
  amount: number
  reference?: string
  note?: string
}

export interface TransferResponse {
  transactionId: string
  fromWalletId: string
  toWalletId: string
  amount: number
  currency: string
  status: string
  reference: string
  senderBalance: number
  receiverBalance: number
  completedAt: string
}

export interface TransactionHistoryItem {
  transactionId: string
  fromWalletId: string
  toWalletId: string
  amount: number
  currency: string
  transactionType: string
  status: string
  reference: string
  note?: string
  createdAt: string
  completedAt?: string
}

export interface CreateBankAccountRequest {
  accountHolderName: string
  accountNumber: string
  ifscCode: string
  bankName: string
}

export interface BankAccountResponse {
  id: string
  accountHolderName: string
  maskedAccountNumber: string
  ifscCode: string
  bankName: string
  verified: boolean
  createdAt: string
}

export interface CreateWithdrawalRequest {
  bankAccountId: string
  amount: number
}

export interface WithdrawalHistoryItem {
  id: string
  amount: number
  status: 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED' | string
  bankAccountId: string
  bankName: string
  accountHolderName: string
  maskedAccountNumber: string
  referenceId: string
  createdAt: string
  processedAt?: string
}

export interface ApiErrorResponse {
  message?: string
  error?: string
  status?: number
}

export interface NotificationResponse {
  id: string
  type: 'CREDIT' | 'DEBIT' | 'LOGIN' | 'SECURITY' | 'WITHDRAWAL' | 'SYSTEM' | string
  title: string
  message: string
  isRead: boolean
  createdAt: string
}

export interface NotificationsResponse {
  unreadCount: number
  notifications: NotificationResponse[]
}

export interface NotificationSocketEvent {
  action: 'UPSERT' | 'DELETE' | 'ALL_READ' | string
  notification: NotificationResponse | null
  notificationId: string | null
}

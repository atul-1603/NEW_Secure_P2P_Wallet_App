import {
  ArrowRightLeft,
  ChartNoAxesCombined,
  CreditCard,
  Download,
  Landmark,
  LayoutDashboard,
  User,
  QrCode,
  Settings,
  Shield,
  ShieldCheck,
  Users,
  Wallet,
} from 'lucide-react'
import { NavLink } from 'react-router-dom'
import { cn } from '../../utils/cn'

const navItems = [
  { icon: LayoutDashboard, label: 'Dashboard', to: '/dashboard' },
  { icon: Wallet, label: 'Wallet', to: '/wallet' },
  { icon: ArrowRightLeft, label: 'Send Money', to: '/send-money' },
  { icon: Users, label: 'Contacts', to: '/contacts' },
  { icon: Download, label: 'Add Money', to: '/add-money' },
  { icon: Landmark, label: 'Withdraw', to: '/withdraw' },
  { icon: QrCode, label: 'Receive Money', to: '/receive' },
  { icon: CreditCard, label: 'Transactions', to: '/transactions' },
  { icon: ChartNoAxesCombined, label: 'Analytics', to: '/analytics' },
  { icon: User, label: 'Profile', to: '/profile' },
  { icon: Shield, label: 'Security', to: '/security' },
  { icon: Settings, label: 'Settings', to: '/settings' },
]

type SidebarProps = {
  mobileOpen: boolean
  onClose: () => void
}

export function Sidebar({ mobileOpen, onClose }: SidebarProps) {
  return (
    <>
      <div
        className={cn(
          'fixed inset-0 z-20 bg-black/30 backdrop-blur-sm transition-opacity lg:hidden',
          mobileOpen ? 'opacity-100' : 'pointer-events-none opacity-0',
        )}
        onClick={onClose}
      />

      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-30 w-72 border-r bg-white/90 px-4 py-6 backdrop-blur transition-transform lg:static lg:z-auto lg:block lg:w-64 lg:translate-x-0',
          mobileOpen ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className="mb-8 flex items-center gap-3 px-2">
          <div className="rounded-xl bg-gradient-to-r from-primary via-blue-500 to-cyan-500 p-2 text-primary-foreground shadow-fintech">
            <ShieldCheck className="h-5 w-5" />
          </div>
          <div>
            <p className="text-sm text-muted-foreground">Secure P2P</p>
            <h1 className="text-base font-semibold">Wallet Console</h1>
          </div>
        </div>

        <div className="mb-4 rounded-xl border bg-card p-3 text-xs text-muted-foreground">
          Banking-grade wallet UI for transfers, analytics, and account controls.
        </div>

        <nav className="space-y-1">
          {navItems.map((item) => (
            <NavLink
              key={item.label}
              to={item.to}
              onClick={onClose}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-gradient-to-r from-primary via-blue-500 to-cyan-500 text-primary-foreground shadow-fintech'
                    : 'text-muted-foreground hover:bg-muted hover:text-foreground',
                )
              }
            >
              <item.icon className="h-4 w-4" />
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>
      </aside>
    </>
  )
}

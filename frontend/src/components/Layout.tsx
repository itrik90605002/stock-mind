import { ReactNode, useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { dashboardApi } from '../api/endpoints';
import { useApi } from '../api/useApi';
import { useAuth } from '../auth/AuthContext';
import { num } from './ui';
import {
  BoxesIcon, ChartIcon, ClipboardIcon, DashboardIcon, FactoryIcon, LedgerIcon, LogOutIcon, MenuIcon, RefreshIcon,
  ShieldIcon, SparklesIcon, TruckIcon, UserIcon,
} from './icons';

const initials = (name: string) => name.split(/\s+/).map(p => p[0]).slice(0, 2).join('').toUpperCase();

function NavItem({ to, icon, children, also = [], end = false, count, urgent }: {
  to: string; icon: ReactNode; children: ReactNode; also?: string[]; end?: boolean; count?: number; urgent?: boolean;
}) {
  const { pathname } = useLocation();
  const inSection = also.some(prefix => pathname.startsWith(prefix));
  return (
    <NavLink to={to} end={end} className={({ isActive }) => (isActive || inSection ? 'active' : '')}>
      {icon}{children}
      {!!count && <span className={`nav-count ${urgent ? '' : 'nav-count-quiet'}`} title={urgent ? 'Some deliveries are late' : undefined}>{num(count)}</span>}
    </NavLink>
  );
}

export function Layout() {
  const { user, logout, hasRole, canManage } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [open, setOpen] = useState(false);

  useEffect(() => setOpen(false), [location.pathname]);
  // Re-counted on every page change, so a badge drops once its task is done elsewhere in the app.
  const counts = useApi(() => (user ? dashboardApi.counts() : Promise.resolve(null)), [location.pathname, user?.id]).data;

  if (!user) return null;
  return (
    <div className="shell">
      <aside className={`sidebar ${open ? 'open' : ''}`} aria-label="Main navigation">
        <NavLink to="/" className="brand"><span className="brand-mark"><BoxesIcon size={16} /></span>StockPilot</NavLink>
        <nav className="nav">
          <NavItem to="/" end icon={<DashboardIcon />}>Home</NavItem>
          <NavItem to="/assistant" icon={<SparklesIcon />}>Ask AI</NavItem>

          <NavItem to="/inventory" icon={<BoxesIcon />} also={['/products/']}>Stock</NavItem>

          <div className="nav-section">Buying</div>
          <NavItem to="/replenish" end icon={<RefreshIcon />} count={counts?.toReorder}>What to reorder</NavItem>
          <NavItem to="/replenish/orders" icon={<ClipboardIcon />} also={['/purchase-orders/']}
                   count={counts?.purchaseOrders} urgent={!!counts?.late}>Purchase orders</NavItem>
          <NavItem to="/replenish/suppliers" icon={<FactoryIcon />}>Suppliers</NavItem>

          <div className="nav-section">Moving stock</div>
          <NavItem to="/transfers" icon={<TruckIcon />} count={counts?.transfers}>Transfers</NavItem>
          <NavItem to="/movements" icon={<LedgerIcon />}>Movement history</NavItem>

          {canManage && <NavItem to="/reports" icon={<ChartIcon />}>Reports</NavItem>}
          {hasRole('ADMIN') && <>
            <div className="nav-section">Administration</div>
            <NavItem to="/admin" icon={<ShieldIcon />}>Users & warehouses</NavItem>
          </>}
        </nav>
        <div className="sidebar-user">
          <span className="avatar">{initials(user.fullName)}</span>
          <div className="sidebar-user-info">
            <div>{user.fullName}</div>
            <span>{user.role.toLowerCase()}{user.role !== 'ADMIN' && ` · ${user.warehouses.map(w => w.code).join(', ')}`}</span>
          </div>
          <NavLink to="/account" className="icon-btn" aria-label="My account" title="My account"><UserIcon size={16} /></NavLink>
          <button className="icon-btn" aria-label="Log out" title="Log out" onClick={() => { logout(); navigate('/login'); }}>
            <LogOutIcon size={16} />
          </button>
        </div>
      </aside>
      <div className="main">
        <header className="topbar">
          <button className="icon-btn" aria-label="Open menu" onClick={() => setOpen(true)}><MenuIcon /></button>
          <span className="brand"><span className="brand-mark"><BoxesIcon size={16} /></span>StockPilot</span>
        </header>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

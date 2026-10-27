import { ReactNode, useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { dashboardApi } from '../api/endpoints';
import { useApi } from '../api/useApi';
import { useAuth } from '../auth/AuthContext';
import { num } from './ui';
import {
  BoxesIcon, ChartIcon, DashboardIcon, LedgerIcon, LogOutIcon, MenuIcon, RefreshIcon, ShieldIcon, SparklesIcon,
  TruckIcon, UserIcon,
} from './icons';

const initials = (name: string) => name.split(/\s+/).map(p => p[0]).slice(0, 2).join('').toUpperCase();

/**
 * A sidebar entry for one section. {@code also} lists other path prefixes that belong to the section
 * (its detail pages), so the entry stays highlighted while drilling in.
 */
function NavItem({ to, icon, children, also = [], count, urgent }: {
  to: string; icon: ReactNode; children: ReactNode; also?: string[]; count?: number; urgent?: boolean;
}) {
  const { pathname } = useLocation();
  const inSection = also.some(prefix => pathname.startsWith(prefix));
  return (
    <NavLink to={to} end={to === '/'} className={({ isActive }) => (isActive || inSection ? 'active' : '')}>
      {icon}{children}
      {!!count && <span className={`nav-count ${urgent ? '' : 'nav-count-quiet'}`} title={urgent ? 'Some deliveries are late' : undefined}>{num(count)}</span>}
    </NavLink>
  );
}

/**
 * App shell. Navigation follows the stock journey: Home (what needs doing) → Inventory (what we have)
 * → Replenish (buy more) → Transfers (rebalance) → Movements (the audit trail).
 */
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
        <Link to="/assistant" className={`ask-launcher ${location.pathname === '/assistant' ? 'active' : ''}`}>
          <SparklesIcon size={16} />Ask about your stock…
        </Link>
        <nav className="nav">
          <NavItem to="/" icon={<DashboardIcon />}>Home</NavItem>
          <NavItem to="/inventory" icon={<BoxesIcon />} also={['/products/']}>Inventory</NavItem>
          <NavItem to="/replenish" icon={<RefreshIcon />} also={['/purchase-orders/']}
                   count={counts ? counts.toReorder + counts.purchaseOrders : 0} urgent={!!counts?.late}>Replenish</NavItem>
          <NavItem to="/transfers" icon={<TruckIcon />} count={counts?.transfers}>Transfers</NavItem>
          <NavItem to="/movements" icon={<LedgerIcon />}>Movements</NavItem>
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

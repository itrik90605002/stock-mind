import { ReactNode, useState } from 'react';
import { Link } from 'react-router-dom';
import { dashboardApi } from '../api/endpoints';
import type { Dashboard, Task, TaskGroup, TaskKind } from '../api/types';
import { useApi } from '../api/useApi';
import { useAuth } from '../auth/AuthContext';
import { ActivityChart } from '../components/ActivityChart';
import { BoxesIcon, ClipboardIcon, RefreshIcon, SparklesIcon, TruckIcon } from '../components/icons';
import {
  Async, Badge, Card, formatDate, money, num, PageHeader, Stat, WarehouseSelect,
} from '../components/ui';

const TASK_META: Record<TaskKind, { title: string; icon: ReactNode; href: (t: Task) => string; all: string }> = {
  PLACE_ORDER: {
    title: 'Draft orders to place', icon: <ClipboardIcon size={16} />,
    href: t => `/purchase-orders/${t.documentId}`, all: '/replenish/orders?view=draft',
  },
  RECEIVE_DELIVERY: {
    title: 'Deliveries to receive', icon: <BoxesIcon size={16} />,
    href: t => `/purchase-orders/${t.documentId}`, all: '/replenish/orders?view=open',
  },
  DISPATCH_TRANSFER: {
    title: 'Transfers to dispatch', icon: <TruckIcon size={16} />,
    href: t => `/transfers/${t.documentId}`, all: '/transfers',
  },
  RECEIVE_TRANSFER: {
    title: 'Transfers arriving', icon: <TruckIcon size={16} />,
    href: t => `/transfers/${t.documentId}`, all: '/transfers',
  },
};

const ITEMS_PER_CARD = 3;

const greeting = () => {
  const h = new Date().getHours();
  return h < 12 ? 'morning' : h < 18 ? 'afternoon' : 'evening';
};

const waitingFor = (since: string | null) => {
  if (!since) return '';
  const days = Math.floor((Date.now() - new Date(since).getTime()) / 86_400_000);
  return days <= 0 ? 'today' : days === 1 ? 'for 1 day' : `for ${days} days`;
};

export function HomePage() {
  const { user, myWarehouses, canManage } = useAuth();
  const [warehouseId, setWarehouseId] = useState<number | ''>('');
  const state = useApi(() => dashboardApi.get(warehouseId || undefined), [warehouseId]);

  return (
    <>
      <PageHeader
        title={`Good ${greeting()}, ${user?.fullName.split(' ')[0]}`}
        subtitle={state.data ? summary(state.data, canManage) : 'Loading what needs your attention…'}
        actions={<WarehouseSelect warehouses={myWarehouses} value={warehouseId} onChange={setWarehouseId} allLabel="All my warehouses" />}
      />
      <Async state={state}>{d => {
        const active = d.tasks.filter(g => g.count > 0);
        return (
          <>
            <div className="stats">
              <Stat label="Stock value" value={money(d.stats.stockValue)} hint={`${num(d.stats.stockUnits)} units`} />
              <Link to="/inventory/levels?low=1" className="stat-link">
                <Stat label="Below reorder point" value={num(d.stats.lowStockCount)} tone={d.stats.lowStockCount > 0 ? 'bad' : 'ok'} />
              </Link>
              <Link to="/replenish/orders" className="stat-link">
                <Stat label="Open purchase orders" value={num(d.stats.openPurchaseOrders)} />
              </Link>
              <Link to="/transfers" className="stat-link">
                <Stat label="Transfers in transit" value={num(d.stats.inTransitTransfers)} />
              </Link>
            </div>

            {(active.length > 0 || d.stats.reorderSuggestions > 0) && <>
              <h2 className="section-title">To do</h2>
              <div className="todo-grid">
                <ReorderCard d={d} canManage={canManage} />
                {active.map(g => <TaskCard key={g.kind} group={g} />)}
              </div>
            </>}

            <h2 className="section-title">Stock in and out · last 30 days</h2>
            <Card><ActivityChart points={d.trend} /></Card>
          </>
        );
      }}</Async>
    </>
  );
}

function summary(d: Dashboard, canManage: boolean) {
  const waiting = d.tasks.reduce((n, g) => n + g.count, 0) + (canManage ? d.stats.reorderSuggestions : 0);
  const late = d.tasks.reduce((n, g) => n + g.late, 0);
  if (waiting === 0) return 'You are all caught up. Nothing is waiting on you.';
  return `${num(waiting)} item${waiting === 1 ? '' : 's'} waiting on you${late ? `, ${num(late)} overdue` : ''}. Start at the top.`;
}

function ReorderCard({ d, canManage }: { d: Dashboard; canManage: boolean }) {
  const count = d.stats.reorderSuggestions;
  if (count === 0) return null;
  return (
    <section className="card todo todo-ai">
      <div className="todo-head">
        <span className="todo-icon"><RefreshIcon size={16} /></span>
        <div>
          <h3>{canManage ? 'Items to reorder' : 'Running low'}</h3>
          <span className="small muted">{canManage ? 'suggested from recent usage' : 'a manager orders these'}</span>
        </div>
        <span className="todo-count">{num(count)}</span>
      </div>
      <ul className="list">
        {d.urgentReorders.slice(0, ITEMS_PER_CARD).map(s => (
          <li key={s.id}>
            <div>
              <Link to={`/products/${s.product.id}`} className="primary small">{s.product.name}</Link>
              <div className="small muted">{s.warehouse.code} · {s.daysOfCover == null ? 'no recent usage' : `${s.daysOfCover} days of stock left`}</div>
            </div>
            <span className="nowrap small"><strong>{num(s.suggestedQty)}</strong> {s.product.unit}</span>
          </li>
        ))}
      </ul>
      <Link to="/replenish" className={`btn btn-sm todo-cta ${canManage ? 'btn-primary' : 'btn-ghost'}`}>
        {canManage ? <><SparklesIcon size={14} />Review & create orders</> : `See all ${num(count)}`}
      </Link>
    </section>
  );
}

function TaskCard({ group }: { group: TaskGroup }) {
  const meta = TASK_META[group.kind];
  return (
    <section className={`card todo ${group.late ? 'todo-late' : ''}`}>
      <div className="todo-head">
        <span className="todo-icon">{meta.icon}</span>
        <div>
          <h3>{meta.title}</h3>
          {group.late > 0 && <span className="small low">{num(group.late)} overdue</span>}
        </div>
        <span className="todo-count">{num(group.count)}</span>
      </div>
      <ul className="list">
        {group.items.slice(0, ITEMS_PER_CARD).map(t => (
          <li key={t.documentId}>
            <div>
              <Link to={meta.href(t)} className="primary small">
                {group.kind === 'PLACE_ORDER' || group.kind === 'RECEIVE_DELIVERY' ? `PO #${t.documentId} · ` : `#${t.documentId} · `}{t.title}
              </Link>
              <div className="small muted">
                {t.warehouseCode} · {num(t.units)} units ·{' '}
                {t.dueDate ? <>due {formatDate(t.dueDate)}</> : <>waiting {waitingFor(t.since)}</>}
                {t.late && <> <Badge value="LOW" label="late" /></>}
              </div>
            </div>
          </li>
        ))}
      </ul>
      {group.count > ITEMS_PER_CARD && <Link to={meta.all} className="small">All {num(group.count)} →</Link>}
    </section>
  );
}

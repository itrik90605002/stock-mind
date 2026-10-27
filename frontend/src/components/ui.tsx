import { FormEvent, ReactNode, useEffect, useRef, useState } from 'react';
import { NavLink } from 'react-router-dom';
import { productApi } from '../api/endpoints';
import type { Product, Warehouse } from '../api/types';
import { CheckIcon, CloseIcon } from './icons';

const numberFormat = new Intl.NumberFormat('en-IN');
const moneyFormat = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
const moneyExactFormat = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 });

export const num = (n: number | null | undefined) => (n == null ? '—' : numberFormat.format(n));
export const money = (n: number | null | undefined) => (n == null ? '—' : moneyFormat.format(n));
export const moneyExact = (n: number | null | undefined) => (n == null ? '—' : moneyExactFormat.format(n));

export const formatDate = (iso: string | null | undefined) =>
  iso ? new Date(iso.length === 10 ? `${iso}T00:00:00` : iso).toLocaleDateString(undefined, {
    day: 'numeric', month: 'short', year: 'numeric',
  }) : '—';

export const formatDateTime = (iso: string | null | undefined) =>
  iso ? new Date(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—';

export const humanize = (value: string) => value.replace(/_/g, ' ').toLowerCase();

export const todayIso = () => {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
};

export function Alert({ kind = 'error', children }: { kind?: 'error' | 'success' | 'info' | 'warn'; children: ReactNode }) {
  if (!children) return null;
  return <div className={`alert alert-${kind}`} role={kind === 'error' ? 'alert' : 'status'}>{children}</div>;
}

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return <div className="loading"><span className="spinner" aria-hidden />{label}</div>;
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>;
}

export function Async<T>({ state, children }: {
  state: { data: T | null; error: string | null; loading: boolean };
  children: (data: T) => ReactNode;
}) {
  if (state.error) return <Alert>{state.error}</Alert>;
  if (state.loading && state.data === null) return <Loading />;
  return state.data === null ? null : <>{children(state.data)}</>;
}

const TONE: Record<string, string> = {
  RECEIPT: 'ok', TRANSFER_IN: 'ok', ISSUE: 'info', TRANSFER_OUT: 'info', ADJUSTMENT: 'warn',
  DRAFT: 'muted', DISPATCHED: 'info', RECEIVED: 'ok', CANCELLED: 'muted',
  ORDERED: 'info', PARTIALLY_RECEIVED: 'warn',
  ANSWERED: 'ok', REFUSED: 'warn', INVALID_PLAN: 'warn', AI_UNAVAILABLE: 'muted', FAILED: 'bad',
  ADMIN: 'ai', MANAGER: 'info', CLERK: 'muted', ACTIVE: 'ok', INACTIVE: 'bad', LOW: 'bad', OK: 'ok',
  MANUAL: 'muted', PURCHASE_ORDER: 'info', TRANSFER: 'info', OPENING: 'muted',
};

export function Badge({ value, label }: { value: string; label?: string }) {
  return <span className={`badge badge-${TONE[value] ?? 'muted'}`}>{label ?? humanize(value)}</span>;
}

export function Delta({ value }: { value: number }) {
  return <span className={`delta ${value > 0 ? 'delta-in' : 'delta-out'}`}>{value > 0 ? `+${num(value)}` : num(value)}</span>;
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </div>
  );
}

export function Card({ title, actions, children, className = '' }: {
  title?: ReactNode; actions?: ReactNode; children: ReactNode; className?: string;
}) {
  return (
    <section className={`card ${className}`}>
      {(title || actions) && (
        <div className="card-head">
          {title && <h2>{title}</h2>}
          {actions && <div className="card-actions">{actions}</div>}
        </div>
      )}
      {children}
    </section>
  );
}

export function SectionTabs({ tabs }: { tabs: { to: string; label: string; end?: boolean }[] }) {
  return (
    <nav className="tabs" aria-label="Section">
      {tabs.map(t => (
        <NavLink key={t.to} to={t.to} end={t.end} className={({ isActive }) => `tab ${isActive ? 'active' : ''}`}>{t.label}</NavLink>
      ))}
    </nav>
  );
}

export function Lifecycle({ steps, current, stopped, next }: {
  steps: string[]; current: number; stopped?: string; next?: ReactNode;
}) {
  return (
    <div className="lifecycle card">
      <ol className="lifecycle-steps">
        {steps.map((s, i) => (
          <li key={s} className={i < current || (i === current && !next) ? 'done' : i === current ? 'current' : ''}
              aria-current={i === current ? 'step' : undefined}>
            <span className="lifecycle-dot">{i < current || (i === current && !next) ? <CheckIcon size={12} /> : i + 1}</span>{s}
          </li>
        ))}
        {stopped && <li className="stopped"><span className="lifecycle-dot"><CloseIcon size={12} /></span>{stopped}</li>}
      </ol>
      {next && <div className="lifecycle-next"><strong>Next:</strong> {next}</div>}
    </div>
  );
}

export function Stat({ label, value, hint, tone }: { label: string; value: ReactNode; hint?: ReactNode; tone?: 'bad' | 'ok' | 'warn' | 'ai' }) {
  return (
    <div className={`stat ${tone ? `stat-${tone}` : ''}`}>
      <div className="stat-label">{label}</div>
      <div className="stat-value">{value}</div>
      {hint && <div className="stat-hint">{hint}</div>}
    </div>
  );
}

export function Pagination({ page, totalPages, total, onChange }: {
  page: number; totalPages: number; total?: number; onChange: (page: number) => void;
}) {
  if (totalPages <= 1) return total != null ? <div className="pagination muted small">{num(total)} total</div> : null;
  return (
    <div className="pagination">
      <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => onChange(page - 1)}>← Previous</button>
      <span className="muted small">Page {page + 1} of {totalPages}{total != null && ` · ${num(total)} total`}</span>
      <button className="btn btn-ghost btn-sm" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Next →</button>
    </div>
  );
}

export function Modal({ title, onClose, children, wide }: { title: string; onClose: () => void; children: ReactNode; wide?: boolean }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    ref.current?.querySelector<HTMLElement>('input, select, textarea, button')?.focus();
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="modal-backdrop" onMouseDown={e => { if (e.target === e.currentTarget) onClose(); }}>
      <div className={`modal ${wide ? 'modal-wide' : ''}`} role="dialog" aria-modal="true" aria-label={title} ref={ref}>
        <div className="modal-head">
          <h2>{title}</h2>
          <button className="icon-btn" onClick={onClose} aria-label="Close"><CloseIcon /></button>
        </div>
        {children}
      </div>
    </div>
  );
}

export function Field({ label, error, hint, children, span }: {
  label: string; error?: string; hint?: ReactNode; children: ReactNode; span?: boolean;
}) {
  return (
    <label className={`field ${span ? 'span-2' : ''}`}>
      <span className="field-label">{label}</span>
      {children}
      {hint && !error && <span className="field-hint">{hint}</span>}
      {error && <span className="field-error">{error}</span>}
    </label>
  );
}

export function FormActions({ busy, error, submitLabel, onCancel }: {
  busy: boolean; error: string | null; submitLabel: string; onCancel: () => void;
}) {
  return (
    <>
      <Alert>{error}</Alert>
      <div className="form-actions">
        <button type="button" className="btn btn-ghost" onClick={onCancel}>Cancel</button>
        <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : submitLabel}</button>
      </div>
    </>
  );
}

export const preventDefault = (handler: () => void) => (e: FormEvent) => { e.preventDefault(); handler(); };

export function WarehouseSelect({ warehouses, value, onChange, allLabel, required, id }: {
  warehouses: Warehouse[]; value: number | ''; onChange: (id: number | '') => void; allLabel?: string;
  required?: boolean; id?: string;
}) {
  return (
    <select id={id} value={value} required={required} onChange={e => onChange(e.target.value ? Number(e.target.value) : '')}>
      {allLabel !== undefined ? <option value="">{allLabel}</option> : <option value="" disabled>Select warehouse…</option>}
      {warehouses.map(w => <option key={w.id} value={w.id}>{w.code} · {w.name}</option>)}
    </select>
  );
}

export type PickedProduct = Pick<Product, 'id' | 'sku' | 'name' | 'unit'> & Partial<Pick<Product, 'unitCost'>>;

export function ProductPicker({ value, onChange, placeholder = 'Search SKU or name…' }: {
  value: PickedProduct | null; onChange: (p: Product | null) => void; placeholder?: string;
}) {
  const [text, setText] = useState('');
  const [results, setResults] = useState<Product[]>([]);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!open) return;
    const handle = setTimeout(() => {
      productApi.search({ q: text, size: 8 }).then(r => setResults(r.content)).catch(() => setResults([]));
    }, 200);
    return () => clearTimeout(handle);
  }, [text, open]);

  if (value) {
    return (
      <div className="picked">
        <span><span className="mono">{value.sku}</span> {value.name}</span>
        <button type="button" className="icon-btn" aria-label="Clear product" onClick={() => onChange(null)}><CloseIcon size={14} /></button>
      </div>
    );
  }
  return (
    <div className="picker">
      <input value={text} placeholder={placeholder} onChange={e => setText(e.target.value)}
             onFocus={() => setOpen(true)} onBlur={() => setTimeout(() => setOpen(false), 150)} />
      {open && results.length > 0 && (
        <ul className="picker-menu" role="listbox">
          {results.map(p => (
            <li key={p.id}>
              <button type="button" onMouseDown={() => { onChange(p); setText(''); setOpen(false); }}>
                <span className="mono">{p.sku}</span> {p.name} <span className="muted small">· {p.unit}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

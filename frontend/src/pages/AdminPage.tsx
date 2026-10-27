import { useState } from 'react';
import { assistantApi, jobApi, userApi, warehouseApi } from '../api/endpoints';
import type { Role, User, Warehouse } from '../api/types';
import { useAction, useApi } from '../api/useApi';
import { useAuth } from '../auth/AuthContext';
import { PlusIcon } from '../components/icons';
import {
  Alert, Async, Badge, Card, Empty, Field, FormActions, formatDateTime, Modal, num, PageHeader, Pagination,
  preventDefault,
} from '../components/ui';

const TABS = ['Users', 'Warehouses', 'AI query log', 'System'] as const;
type Tab = typeof TABS[number];

export function AdminPage() {
  const [tab, setTab] = useState<Tab>('Users');
  return (
    <>
      <PageHeader title="Administration" subtitle="Users, warehouses, AI usage and maintenance." />
      <div className="tabs" role="tablist">
        {TABS.map(t => <button key={t} role="tab" aria-selected={tab === t} className={`tab ${tab === t ? 'active' : ''}`} onClick={() => setTab(t)}>{t}</button>)}
      </div>
      {tab === 'Users' && <UsersTab />}
      {tab === 'Warehouses' && <WarehousesTab />}
      {tab === 'AI query log' && <AiLogTab />}
      {tab === 'System' && <SystemTab />}
    </>
  );
}

// ------------------------------------------------------------------ users

function UsersTab() {
  const { user: me } = useAuth();
  const users = useApi(() => userApi.list(), []);
  const [editing, setEditing] = useState<User | 'new' | null>(null);
  const [deactivating, setDeactivating] = useState<User | null>(null);
  const action = useAction();

  return (
    <>
      <div className="toolbar"><span className="spacer" />
        <button className="btn btn-primary" onClick={() => setEditing('new')}><PlusIcon size={16} />New user</button>
      </div>
      <Alert>{action.error}</Alert>
      <Card className="flush">
        <Async state={users}>{list => (
          <div className="table-wrap"><table>
            <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Warehouses</th><th>Status</th><th /></tr></thead>
            <tbody>{list.map(u => (
              <tr key={u.id}>
                <td>{u.fullName}</td>
                <td>{u.email}</td>
                <td><Badge value={u.role} /></td>
                <td>{u.role === 'ADMIN' ? <span className="muted">all</span> : u.warehouses.map(w => w.code).join(', ')}</td>
                <td>{u.active ? <Badge value="ACTIVE" /> : <><Badge value="INACTIVE" label="deactivated" /><span className="sub">{u.statusReason}</span></>}</td>
                <td className="actions">
                  <button className="btn btn-ghost btn-sm" onClick={() => setEditing(u)}>Edit</button>{' '}
                  {u.id !== me?.id && (u.active
                    ? <button className="btn btn-ghost btn-sm" onClick={() => setDeactivating(u)}>Deactivate</button>
                    : <button className="btn btn-ghost btn-sm" disabled={action.busy}
                              onClick={() => action.run(async () => { await userApi.activate(u.id); await users.reload(); })}>Reactivate</button>)}
                </td>
              </tr>
            ))}</tbody>
          </table></div>
        )}</Async>
      </Card>
      {editing && <UserForm user={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)}
                            onSaved={() => { setEditing(null); void users.reload(); }} />}
      {deactivating && <DeactivateUser user={deactivating} onClose={() => setDeactivating(null)}
                                       onDone={() => { setDeactivating(null); void users.reload(); }} />}
    </>
  );
}

function UserForm({ user, onClose, onSaved }: { user?: User; onClose: () => void; onSaved: () => void }) {
  const { warehouses } = useAuth();
  const [email, setEmail] = useState(user?.email ?? '');
  const [fullName, setFullName] = useState(user?.fullName ?? '');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<Role>(user?.role ?? 'CLERK');
  const [warehouseIds, setWarehouseIds] = useState<number[]>(user?.warehouses.map(w => w.id) ?? []);
  const action = useAction();

  const toggle = (id: number) => setWarehouseIds(ids => ids.includes(id) ? ids.filter(i => i !== id) : [...ids, id]);
  const submit = () => action.run(async () => {
    if (user) await userApi.update(user.id, { fullName, role, warehouseIds });
    else await userApi.create({ email, fullName, password, role, warehouseIds });
    onSaved();
  });

  return (
    <Modal title={user ? `Edit ${user.fullName}` : 'New user'} onClose={onClose}>
      <form className="form-grid" onSubmit={preventDefault(submit)}>
        <Field label="Full name" span><input value={fullName} onChange={e => setFullName(e.target.value)} required maxLength={120} /></Field>
        <Field label="Email" span><input type="email" value={email} onChange={e => setEmail(e.target.value)} required disabled={!!user} /></Field>
        {!user && <Field label="Initial password" hint="8+ characters with a letter and a digit" span>
          <input type="password" value={password} onChange={e => setPassword(e.target.value)} required minLength={8} autoComplete="new-password" />
        </Field>}
        <Field label="Role" span>
          <select value={role} onChange={e => setRole(e.target.value as Role)}>
            <option value="CLERK">Clerk — records issues, adjustments and receipts</option>
            <option value="MANAGER">Manager — also products, transfers, purchase orders</option>
            <option value="ADMIN">Admin — everything, all warehouses</option>
          </select>
        </Field>
        {role !== 'ADMIN' && (
          <fieldset className="span-2" style={{ border: 0, padding: 0, margin: 0 }}>
            <legend className="field-label" style={{ marginBottom: '.4rem' }}>Warehouses</legend>
            <div className="row">{warehouses.filter(w => w.active).map(w => (
              <label key={w.id} className="check"><input type="checkbox" checked={warehouseIds.includes(w.id)} onChange={() => toggle(w.id)} />{w.code}</label>
            ))}</div>
          </fieldset>
        )}
        <div className="span-2"><FormActions busy={action.busy} error={action.error} submitLabel="Save" onCancel={onClose} /></div>
      </form>
    </Modal>
  );
}

function DeactivateUser({ user, onClose, onDone }: { user: User; onClose: () => void; onDone: () => void }) {
  const [reason, setReason] = useState('');
  const action = useAction();
  return (
    <Modal title={`Deactivate ${user.fullName}`} onClose={onClose}>
      <p className="muted small">They are signed out immediately and cannot log in until reactivated. Their history is kept.</p>
      <form onSubmit={preventDefault(() => action.run(async () => { await userApi.deactivate(user.id, reason); onDone(); }))}>
        <Field label="Reason"><input value={reason} onChange={e => setReason(e.target.value)} required maxLength={200} /></Field>
        <FormActions busy={action.busy} error={action.error} submitLabel="Deactivate" onCancel={onClose} />
      </form>
    </Modal>
  );
}

// ------------------------------------------------------------------ warehouses

function WarehousesTab() {
  const { warehouses, refreshWarehouses } = useAuth();
  const [editing, setEditing] = useState<Warehouse | 'new' | null>(null);
  const action = useAction();
  const toggle = (w: Warehouse) => action.run(async () => {
    if (w.active) await warehouseApi.deactivate(w.id); else await warehouseApi.activate(w.id);
    await refreshWarehouses();
  });
  return (
    <>
      <div className="toolbar"><span className="spacer" />
        <button className="btn btn-primary" onClick={() => setEditing('new')}><PlusIcon size={16} />New warehouse</button>
      </div>
      <Alert>{action.error}</Alert>
      <Card className="flush">
        {warehouses.length === 0 ? <Empty>No warehouses.</Empty> : (
          <div className="table-wrap"><table>
            <thead><tr><th>Code</th><th>Name</th><th>Address</th><th>Status</th><th /></tr></thead>
            <tbody>{warehouses.map(w => (
              <tr key={w.id}>
                <td className="mono">{w.code}</td><td>{w.name}</td><td className="small">{w.address ?? '—'}</td>
                <td>{w.active ? <Badge value="ACTIVE" /> : <Badge value="INACTIVE" label="deactivated" />}</td>
                <td className="actions">
                  <button className="btn btn-ghost btn-sm" onClick={() => setEditing(w)}>Edit</button>{' '}
                  <button className="btn btn-ghost btn-sm" onClick={() => toggle(w)} disabled={action.busy}>{w.active ? 'Deactivate' : 'Activate'}</button>
                </td>
              </tr>
            ))}</tbody>
          </table></div>
        )}
      </Card>
      <p className="small muted">A warehouse can be deactivated only when it holds no stock and has no open transfers or purchase orders.</p>
      {editing && <WarehouseForm warehouse={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)}
                                 onSaved={() => { setEditing(null); void refreshWarehouses(); }} />}
    </>
  );
}

function WarehouseForm({ warehouse, onClose, onSaved }: { warehouse?: Warehouse; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState({ code: warehouse?.code ?? '', name: warehouse?.name ?? '', address: warehouse?.address ?? '' });
  const action = useAction();
  const submit = () => action.run(async () => {
    if (warehouse) await warehouseApi.update(warehouse.id, form); else await warehouseApi.create(form);
    onSaved();
  });
  return (
    <Modal title={warehouse ? `Edit ${warehouse.code}` : 'New warehouse'} onClose={onClose}>
      <form className="form-grid" onSubmit={preventDefault(submit)}>
        <Field label="Code" hint={warehouse ? 'Cannot be changed' : 'e.g. HYD-05'}>
          <input value={form.code} onChange={e => setForm({ ...form, code: e.target.value })} required disabled={!!warehouse} pattern="[A-Za-z0-9-]{2,20}" />
        </Field>
        <Field label="Name"><input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} required maxLength={120} /></Field>
        <Field label="Address" span><input value={form.address} onChange={e => setForm({ ...form, address: e.target.value })} maxLength={255} /></Field>
        <div className="span-2"><FormActions busy={action.busy} error={action.error} submitLabel="Save" onCancel={onClose} /></div>
      </form>
    </Modal>
  );
}

// ------------------------------------------------------------------ AI log & system

function AiLogTab() {
  const [page, setPage] = useState(0);
  const logs = useApi(() => assistantApi.logs(page), [page]);
  const [open, setOpen] = useState<number | null>(null);
  return (
    <Card className="flush">
      <Async state={logs}>{data => data.content.length === 0 ? <Empty>No questions asked yet.</Empty> : (
        <>
          <div className="table-wrap"><table>
            <thead><tr><th>When</th><th>User</th><th>Question</th><th>Outcome</th><th className="num">Rows</th><th className="num">ms</th></tr></thead>
            <tbody>{data.content.map(l => (
              <tr key={l.id} onClick={() => setOpen(open === l.id ? null : l.id)} style={{ cursor: 'pointer' }}>
                <td className="small nowrap">{formatDateTime(l.createdAt)}</td>
                <td className="small">{l.userName}</td>
                <td>{l.question}
                  {l.message && <span className="sub">{l.message}</span>}
                  {open === l.id && l.planJson && <pre className="mono small" style={{ whiteSpace: 'pre-wrap', margin: '.4rem 0 0' }}>{l.planJson}</pre>}
                </td>
                <td><Badge value={l.outcome} /></td>
                <td className="num">{l.rowCount ?? '—'}</td>
                <td className="num">{num(l.durationMs)}</td>
              </tr>
            ))}</tbody>
          </table></div>
          <Pagination page={data.page} totalPages={data.totalPages} total={data.totalElements} onChange={setPage} />
        </>
      )}</Async>
    </Card>
  );
}

function SystemTab() {
  const action = useAction();
  const [result, setResult] = useState<string | null>(null);
  const run = () => action.run(async () => {
    const r = await jobApi.runDaily();
    setResult(`Analysed ${num(r.forecast.pairsAnalysed)} product/warehouse pairs in ${num(r.forecast.durationMs)} ms: `
      + `${num(r.forecast.suggestions)} reorder suggestions (${num(r.forecast.aiExplanations)} explained by AI). `
      + `Purged ${num(r.idempotencyRecordsPurged)} old idempotency records.`);
  });
  return (
    <Card title="Daily maintenance job">
      <p className="muted">Runs every night and at startup: recalculates usage statistics and reorder suggestions, and clears expired request keys.</p>
      <button className="btn btn-primary" onClick={run} disabled={action.busy}>{action.busy ? 'Running…' : 'Run now'}</button>
      <Alert>{action.error}</Alert>
      <Alert kind="success">{result}</Alert>
    </Card>
  );
}

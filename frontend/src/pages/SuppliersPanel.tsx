import { useState } from 'react';
import { Link } from 'react-router-dom';
import { supplierApi } from '../api/endpoints';
import type { Supplier } from '../api/types';
import { useAction, useApi } from '../api/useApi';
import { useAuth } from '../auth/AuthContext';
import { PlusIcon } from '../components/icons';
import { Async, Card, Empty, Field, FormActions, Modal, preventDefault } from '../components/ui';

/** Replenish → Suppliers: vendors and lead times. A supplier's name opens its purchase orders. */
export function SuppliersPanel() {
  const { canManage } = useAuth();
  const suppliers = useApi(() => supplierApi.list(), []);
  const [editing, setEditing] = useState<Supplier | 'new' | null>(null);

  return (
    <>
      <div className="toolbar">
        <span className="muted small">Vendors and their typical delivery lead times. Lead times feed the reorder suggestions.</span>
        {canManage && <button className="btn btn-primary toolbar-end" onClick={() => setEditing('new')}><PlusIcon size={16} />New supplier</button>}
      </div>
      <Card className="flush">
        <Async state={suppliers}>{list => list.length === 0 ? <Empty>No suppliers yet.</Empty> : (
          <div className="table-wrap"><table>
            <thead><tr><th>Name</th><th>Email</th><th>Phone</th><th className="num">Lead time</th><th /></tr></thead>
            <tbody>{list.map(s => (
              <tr key={s.id}>
                <td><Link to={`/replenish/orders?view=all&supplierId=${s.id}`}>{s.name}</Link></td>
                <td>{s.contactEmail ?? '—'}</td>
                <td className="nowrap">{s.phone ?? '—'}</td>
                <td className="num">{s.leadTimeDays} days</td>
                <td className="actions">{canManage && <button className="btn btn-ghost btn-sm" onClick={() => setEditing(s)}>Edit</button>}</td>
              </tr>
            ))}</tbody>
          </table></div>
        )}</Async>
      </Card>
      {editing && <SupplierForm supplier={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)}
                                onSaved={() => { setEditing(null); void suppliers.reload(); }} />}
    </>
  );
}

function SupplierForm({ supplier, onClose, onSaved }: { supplier?: Supplier; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState({
    name: supplier?.name ?? '', contactEmail: supplier?.contactEmail ?? '', phone: supplier?.phone ?? '',
    leadTimeDays: supplier?.leadTimeDays ?? 7,
  });
  const action = useAction();
  const submit = () => action.run(async () => {
    if (supplier) await supplierApi.update(supplier.id, form);
    else await supplierApi.create(form);
    onSaved();
  });
  return (
    <Modal title={supplier ? `Edit ${supplier.name}` : 'New supplier'} onClose={onClose}>
      <form className="form-grid" onSubmit={preventDefault(submit)}>
        <Field label="Name" span><input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} required maxLength={120} /></Field>
        <Field label="Contact email"><input type="email" value={form.contactEmail} onChange={e => setForm({ ...form, contactEmail: e.target.value })} /></Field>
        <Field label="Phone"><input value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })} maxLength={40} /></Field>
        <Field label="Lead time (days)" hint="Order to delivery">
          <input type="number" min={0} max={365} value={form.leadTimeDays} onChange={e => setForm({ ...form, leadTimeDays: Number(e.target.value) })} required />
        </Field>
        <div className="span-2"><FormActions busy={action.busy} error={action.error} submitLabel="Save" onCancel={onClose} /></div>
      </form>
    </Modal>
  );
}

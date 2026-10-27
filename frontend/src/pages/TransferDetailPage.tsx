import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { transferApi } from '../api/endpoints';
import type { Transfer } from '../api/types';
import { useAction, useApi, useIdempotencyKey } from '../api/useApi';
import { useAuth } from '../auth/AuthContext';
import {
  Alert, Async, Badge, Card, Field, FormActions, formatDateTime, Lifecycle, Modal, num, PageHeader, preventDefault,
} from '../components/ui';

const STEPS = ['Draft · stock reserved', 'Dispatched · in transit', 'Received'];

/** The transfer's position on the Draft → Dispatched → Received path, and what happens next. */
function lifecycle(t: Transfer) {
  switch (t.status) {
    case 'DRAFT':
      return { current: 0, next: `pick and dispatch from ${t.source.code}. The reserved stock leaves the source when you dispatch.` };
    case 'DISPATCHED':
      return { current: 1, next: `receive it at ${t.destination.code} and record what actually arrived.` };
    case 'RECEIVED':
      return { current: 2 };
    case 'CANCELLED':
      return { current: 0, stopped: 'Cancelled · reservation released' };
  }
}

export function TransferDetailPage() {
  const id = Number(useParams().id);
  const { user, canManage } = useAuth();
  const transfer = useApi(() => transferApi.get(id), [id]);
  const [dialog, setDialog] = useState<'receive' | 'cancel' | null>(null);
  const dispatch = useAction();
  const dispatchKey = useIdempotencyKey();

  const canAct = (warehouseId: number) => user?.role === 'ADMIN' || !!user?.warehouses.some(w => w.id === warehouseId);

  return (
    <Async state={transfer}>{t => (
      <>
        <Link to="/transfers" className="small">← Transfers</Link>
        <PageHeader title={`Transfer #${t.id}`}
                    subtitle={<>{t.source.code} {t.source.name} → {t.destination.code} {t.destination.name} · <Badge value={t.status} label={t.status === 'DISPATCHED' ? 'in transit' : undefined} /></>}
                    actions={<>
                      {t.status === 'DRAFT' && canAct(t.source.id) && (
                        <button className="btn btn-primary" disabled={dispatch.busy} onClick={() => dispatch.run(async () => transfer.setData(await transferApi.dispatch(id, dispatchKey.key)), 'Dispatched. Stock left the source warehouse.')}>
                          {dispatch.busy ? 'Dispatching…' : 'Dispatch'}</button>)}
                      {t.status === 'DISPATCHED' && canAct(t.destination.id) && <button className="btn btn-primary" onClick={() => setDialog('receive')}>Receive</button>}
                      {t.status === 'DRAFT' && canManage && canAct(t.source.id) && <button className="btn btn-ghost" onClick={() => setDialog('cancel')}>Cancel transfer</button>}
                    </>} />
        <Alert>{dispatch.error}</Alert>
        <Alert kind="success">{dispatch.message}</Alert>
        <Lifecycle steps={STEPS} {...lifecycle(t)} />
        <div className="grid-3">
          <Card title="Lines" className="flush">
            <div className="table-wrap"><table>
              <thead><tr><th>Product</th><th className="num">Quantity</th><th className="num">Received</th><th className="num">Lost in transit</th></tr></thead>
              <tbody>{t.lines.map(l => (
                <tr key={l.id}>
                  <td><Link to={`/products/${l.product.id}`}>{l.product.name}</Link><span className="sub mono">{l.product.sku}</span></td>
                  <td className="num">{num(l.quantity)} {l.product.unit}</td>
                  <td className="num">{l.receivedQuantity == null ? '—' : num(l.receivedQuantity)}</td>
                  <td className={`num ${l.shortfall > 0 ? 'low' : ''}`}>{l.shortfall > 0 ? num(l.shortfall) : '—'}</td>
                </tr>
              ))}</tbody>
            </table></div>
          </Card>
          <Card title="Timeline">
            <ul className="list">
              <li><span>Created by {t.createdBy}</span><span className="small muted">{formatDateTime(t.createdAt)}</span></li>
              {t.dispatchedAt && <li><span>Dispatched{t.dispatchedBy && ` by ${t.dispatchedBy}`}</span><span className="small muted">{formatDateTime(t.dispatchedAt)}</span></li>}
              {t.receivedAt && <li><span>Received{t.receivedBy && ` by ${t.receivedBy}`}</span><span className="small muted">{formatDateTime(t.receivedAt)}</span></li>}
              {t.cancelledAt && <li><span>Cancelled</span><span className="small muted">{formatDateTime(t.cancelledAt)}</span></li>}
            </ul>
            {t.note && <p className="small muted">{t.note}</p>}
          </Card>
        </div>
        {dialog === 'receive' && <ReceiveTransfer transfer={t} onClose={() => setDialog(null)} onDone={u => { transfer.setData(u); setDialog(null); }} />}
        {dialog === 'cancel' && <CancelTransfer transfer={t} onClose={() => setDialog(null)} onDone={u => { transfer.setData(u); setDialog(null); }} />}
      </>
    )}</Async>
  );
}

function ReceiveTransfer({ transfer, onClose, onDone }: { transfer: Transfer; onClose: () => void; onDone: (t: Transfer) => void }) {
  const [received, setReceived] = useState<Record<number, number>>(Object.fromEntries(transfer.lines.map(l => [l.id, l.quantity])));
  const action = useAction();
  const { key } = useIdempotencyKey();
  const submit = () => action.run(async () => onDone(await transferApi.receive(transfer.id,
    transfer.lines.map(l => ({ lineId: l.id, receivedQuantity: received[l.id] })), key)));
  return (
    <Modal title={`Receive transfer #${transfer.id}`} onClose={onClose} wide>
      <p className="muted small">Enter what actually arrived at {transfer.destination.code}. Any shortfall is recorded as lost in transit.</p>
      <form onSubmit={preventDefault(submit)}>
        <div className="table-wrap"><table>
          <thead><tr><th>Product</th><th className="num">Dispatched</th><th className="num">Received</th></tr></thead>
          <tbody>{transfer.lines.map(l => (
            <tr key={l.id}>
              <td>{l.product.name}<span className="sub mono">{l.product.sku}</span></td>
              <td className="num">{num(l.quantity)}</td>
              <td className="num"><input type="number" min={0} max={l.quantity} style={{ width: 100 }} value={received[l.id]}
                                         aria-label={`Received ${l.product.sku}`} onChange={e => setReceived({ ...received, [l.id]: Number(e.target.value) })} /></td>
            </tr>
          ))}</tbody>
        </table></div>
        <FormActions busy={action.busy} error={action.error} submitLabel="Book in" onCancel={onClose} />
      </form>
    </Modal>
  );
}

function CancelTransfer({ transfer, onClose, onDone }: { transfer: Transfer; onClose: () => void; onDone: (t: Transfer) => void }) {
  const [reason, setReason] = useState('');
  const action = useAction();
  return (
    <Modal title={`Cancel transfer #${transfer.id}`} onClose={onClose}>
      <p className="muted small">The reserved stock is released at {transfer.source.code}.</p>
      <form onSubmit={preventDefault(() => action.run(async () => onDone(await transferApi.cancel(transfer.id, reason))))}>
        <Field label="Reason"><input value={reason} onChange={e => setReason(e.target.value)} required maxLength={200} /></Field>
        <FormActions busy={action.busy} error={action.error} submitLabel="Cancel transfer" onCancel={onClose} />
      </form>
    </Modal>
  );
}

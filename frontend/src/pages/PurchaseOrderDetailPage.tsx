import { useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { purchaseOrderApi } from '../api/endpoints';
import type { PurchaseOrder } from '../api/types';
import { useAction, useApi, useIdempotencyKey } from '../api/useApi';
import { useAuth } from '../auth/AuthContext';
import { PurchaseOrderForm } from '../components/PurchaseOrderForm';
import {
  Alert, Async, Badge, Card, Field, FormActions, formatDate, formatDateTime, Lifecycle, Modal, money, moneyExact, num,
  PageHeader, preventDefault,
} from '../components/ui';
import { isLate } from './PurchaseOrdersPanel';

const STEPS = ['Draft', 'Placed with supplier', 'Delivery received'];

function lifecycle(po: PurchaseOrder, canManage: boolean) {
  switch (po.status) {
    case 'DRAFT':
      return { current: 0, next: canManage ? 'check the lines and quantities, then place the order with the supplier.' : 'a manager places this order with the supplier.' };
    case 'ORDERED':
      return { current: 1, next: <>receive the delivery when it arrives{po.expectedDate && <> (expected {formatDate(po.expectedDate)})</>}. That books the stock in.</> };
    case 'PARTIALLY_RECEIVED':
      return { current: 1, next: <>{num(po.remainingUnits)} units are still expected. Receive them when they arrive{canManage && ', or close the order short if they will not come'}.</> };
    case 'RECEIVED':
      // Closing short also ends in RECEIVED, with units that will no longer come.
      return po.remainingUnits > 0 ? { current: 1, stopped: 'Closed short' } : { current: 2 };
    case 'CANCELLED':
      return { current: po.orderedAt ? 1 : 0, stopped: 'Cancelled' };
  }
}

export function PurchaseOrderDetailPage() {
  const id = Number(useParams().id);
  const { canManage } = useAuth();
  const notice = (useLocation().state as { notice?: string } | null)?.notice;
  const order = useApi(() => purchaseOrderApi.get(id), [id]);
  const [dialog, setDialog] = useState<'receive' | 'cancel' | 'close' | 'edit' | null>(null);
  const place = useAction();
  const done = (po: PurchaseOrder) => { order.setData(po); setDialog(null); };

  return (
    <Async state={order}>{po => (
      <>
        <Link to="/replenish/orders" className="small">← Purchase orders</Link>
        <PageHeader title={`PO #${po.id} · ${po.supplierName}`}
                    subtitle={<>Deliver to {po.warehouse.code} {po.warehouse.name} · <Badge value={po.status} />{isLate(po) && <> <Badge value="LOW" label="late" /></>}</>}
                    actions={<>
                      {po.status === 'DRAFT' && canManage && <>
                        <button className="btn btn-ghost" onClick={() => setDialog('edit')}>Edit</button>
                        <button className="btn btn-primary" disabled={place.busy}
                                onClick={() => place.run(async () => order.setData(await purchaseOrderApi.place(id)), 'Order placed with the supplier.')}>Place order</button>
                      </>}
                      {(po.status === 'ORDERED' || po.status === 'PARTIALLY_RECEIVED') && <button className="btn btn-primary" onClick={() => setDialog('receive')}>Receive delivery</button>}
                      {po.status === 'PARTIALLY_RECEIVED' && canManage && <button className="btn btn-ghost" onClick={() => setDialog('close')}>Close short</button>}
                      {(po.status === 'DRAFT' || po.status === 'ORDERED') && canManage && <button className="btn btn-ghost" onClick={() => setDialog('cancel')}>Cancel</button>}
                    </>} />
        <Alert>{place.error}</Alert>
        <Alert kind="success">{place.message ?? notice}</Alert>
        <Lifecycle steps={STEPS} {...lifecycle(po, canManage)} />
        <div className="stats">
          <div className="stat"><div className="stat-label">Order value</div><div className="stat-value">{money(po.totalValue)}</div></div>
          <div className="stat"><div className="stat-label">Units ordered</div><div className="stat-value">{num(po.totalUnits)}</div></div>
          <div className="stat"><div className="stat-label">Still expected</div><div className="stat-value">{num(po.remainingUnits)}</div></div>
          <div className="stat"><div className="stat-label">Expected</div><div className="stat-value" style={{ fontSize: '1.15rem' }}>{formatDate(po.expectedDate)}</div></div>
        </div>
        <div className="grid-3">
          <Card title="Lines" className="flush">
            <div className="table-wrap"><table>
              <thead><tr><th>Product</th><th className="num">Ordered</th><th className="num">Received</th><th className="num">Remaining</th><th className="num">Unit cost</th><th className="num">Total</th></tr></thead>
              <tbody>{po.lines.map(l => (
                <tr key={l.id}>
                  <td><Link to={`/products/${l.product.id}`}>{l.product.name}</Link><span className="sub mono">{l.product.sku}</span></td>
                  <td className="num">{num(l.quantity)}</td>
                  <td className="num">{num(l.receivedQuantity)}</td>
                  <td className="num">{num(l.remaining)}</td>
                  <td className="num">{moneyExact(l.unitCost)}</td>
                  <td className="num">{money(l.lineTotal)}</td>
                </tr>
              ))}</tbody>
            </table></div>
          </Card>
          <Card title="History">
            <ul className="list">
              <li><span>Created by {po.createdBy}</span><span className="small muted">{formatDateTime(po.createdAt)}</span></li>
              {po.orderedAt && <li><span>Placed</span><span className="small muted">{formatDateTime(po.orderedAt)}</span></li>}
              {po.closedAt && <li><span>Closed</span><span className="small muted">{formatDateTime(po.closedAt)}</span></li>}
            </ul>
            {po.note && <p className="small muted">{po.note}</p>}
            {po.status !== 'DRAFT' && <Link to="/movements" className="small">Receipts appear in Movements →</Link>}
          </Card>
        </div>
        {dialog === 'receive' && <ReceiveDelivery order={po} onClose={() => setDialog(null)} onDone={done} />}
        {dialog === 'edit' && <PurchaseOrderForm order={po} onClose={() => setDialog(null)} onSaved={done} />}
        {(dialog === 'cancel' || dialog === 'close') && <ReasonDialog order={po} kind={dialog} onClose={() => setDialog(null)} onDone={done} />}
      </>
    )}</Async>
  );
}

function ReceiveDelivery({ order, onClose, onDone }: { order: PurchaseOrder; onClose: () => void; onDone: (po: PurchaseOrder) => void }) {
  const open = order.lines.filter(l => l.remaining > 0);
  const [qty, setQty] = useState<Record<number, number>>(Object.fromEntries(open.map(l => [l.id, l.remaining])));
  const action = useAction();
  const { key } = useIdempotencyKey();
  const submit = () => action.run(async () => {
    const lines = open.filter(l => qty[l.id] > 0).map(l => ({ lineId: l.id, quantity: qty[l.id] }));
    if (lines.length === 0) throw new Error('Enter a quantity for at least one line.');
    onDone(await purchaseOrderApi.receive(order.id, lines, key));
  });
  return (
    <Modal title={`Receive delivery · PO #${order.id}`} onClose={onClose} wide>
      <p className="muted small">Enter what arrived. Leave a line at 0 if it did not arrive; the rest stays expected.</p>
      <form onSubmit={preventDefault(submit)}>
        <div className="table-wrap"><table>
          <thead><tr><th>Product</th><th className="num">Remaining</th><th className="num">Received now</th></tr></thead>
          <tbody>{open.map(l => (
            <tr key={l.id}>
              <td>{l.product.name}<span className="sub mono">{l.product.sku}</span></td>
              <td className="num">{num(l.remaining)}</td>
              <td className="num"><input type="number" min={0} max={l.remaining} style={{ width: 100 }} value={qty[l.id]}
                                         aria-label={`Received ${l.product.sku}`} onChange={e => setQty({ ...qty, [l.id]: Number(e.target.value) })} /></td>
            </tr>
          ))}</tbody>
        </table></div>
        <FormActions busy={action.busy} error={action.error} submitLabel="Book in stock" onCancel={onClose} />
      </form>
    </Modal>
  );
}

function ReasonDialog({ order, kind, onClose, onDone }: { order: PurchaseOrder; kind: 'cancel' | 'close'; onClose: () => void; onDone: (po: PurchaseOrder) => void }) {
  const [reason, setReason] = useState('');
  const action = useAction();
  const submit = () => action.run(async () => onDone(kind === 'cancel'
    ? await purchaseOrderApi.cancel(order.id, reason) : await purchaseOrderApi.close(order.id, reason)));
  return (
    <Modal title={kind === 'cancel' ? `Cancel PO #${order.id}` : `Close PO #${order.id} short`} onClose={onClose}>
      <p className="muted small">{kind === 'cancel' ? 'Nothing will be received against this order.' : `The remaining ${num(order.remainingUnits)} units will no longer be expected.`}</p>
      <form onSubmit={preventDefault(submit)}>
        <Field label="Reason"><input value={reason} onChange={e => setReason(e.target.value)} required maxLength={200} /></Field>
        <FormActions busy={action.busy} error={action.error} submitLabel={kind === 'cancel' ? 'Cancel order' : 'Close order'} onCancel={onClose} />
      </form>
    </Modal>
  );
}

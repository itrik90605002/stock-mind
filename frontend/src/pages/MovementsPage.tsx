import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { movementApi, stockApi } from '../api/endpoints';
import type { ConsistencyReport, Movement, MovementType } from '../api/types';
import { useAction, useApi, useIdempotencyKey } from '../api/useApi';
import { useAuth } from '../auth/AuthContext';
import { CheckIcon } from '../components/icons';
import {
  Alert, Async, Badge, Card, Delta, Empty, Field, FormActions, formatDateTime, Modal, num, PageHeader, Pagination,
  preventDefault, WarehouseSelect,
} from '../components/ui';

const TYPES: MovementType[] = ['RECEIPT', 'ISSUE', 'ADJUSTMENT', 'TRANSFER_OUT', 'TRANSFER_IN'];

function reference(m: Movement) {
  if (m.referenceType === 'PURCHASE_ORDER') return <Link to={`/purchase-orders/${m.referenceId}`}>PO #{m.referenceId}</Link>;
  if (m.referenceType === 'TRANSFER') return <Link to={`/transfers/${m.referenceId}`}>Transfer #{m.referenceId}</Link>;
  if (m.reversesId) return <span className="muted">reverses #{m.reversesId}</span>;
  return <span className="muted">{m.referenceType === 'OPENING' ? 'opening' : 'manual'}</span>;
}

/** The append-only stock ledger, with reversal of manual entries and a ledger-vs-stock consistency check. */
export function MovementsPage() {
  const { myWarehouses, canManage } = useAuth();
  const [params] = useSearchParams();
  const productId = params.get('productId') ? Number(params.get('productId')) : undefined;
  const [warehouseId, setWarehouseId] = useState<number | ''>('');
  const [type, setType] = useState<MovementType | ''>('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const [reversing, setReversing] = useState<Movement | null>(null);
  const [report, setReport] = useState<ConsistencyReport | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const check = useAction();

  const movements = useApi(() => movementApi.list({ warehouseId: warehouseId || undefined, productId, type, from, to, page, size: 50 }),
    [warehouseId, productId, type, from, to, page]);

  const runCheck = () => check.run(async () => setReport(await stockApi.consistency(warehouseId || undefined)));

  return (
    <>
      <PageHeader title="Stock movements" subtitle="Every change to stock, in order. Entries are never edited — mistakes are corrected with a reversing entry."
                  actions={canManage && <button className="btn btn-ghost" onClick={runCheck} disabled={check.busy}>
                    <CheckIcon size={16} />{check.busy ? 'Checking…' : 'Verify ledger'}</button>} />
      <Alert>{check.error}</Alert>
      {report && (report.consistent
        ? <Alert kind="success">Ledger verified: all {num(report.levelsChecked)} stock levels match the sum of their movements and open transfer reservations.</Alert>
        : <Alert>
            {report.mismatches.length} stock level(s) differ from the ledger:
            <ul>{report.mismatches.map(m => (
              <li key={`${m.productId}-${m.warehouseId}`}>{m.sku} in {m.warehouseCode}: on hand {m.recordedOnHand} vs ledger {m.ledgerOnHand}; reserved {m.recordedReserved} vs expected {m.expectedReserved}</li>
            ))}</ul>
          </Alert>)}
      <Alert kind="success">{notice}</Alert>
      <div className="toolbar">
        <WarehouseSelect warehouses={myWarehouses} value={warehouseId} onChange={v => { setWarehouseId(v); setPage(0); }} allLabel="All my warehouses" />
        <select value={type} onChange={e => { setType(e.target.value as MovementType | ''); setPage(0); }}>
          <option value="">All types</option>
          {TYPES.map(t => <option key={t} value={t}>{t.replace('_', ' ').toLowerCase()}</option>)}
        </select>
        <input type="date" aria-label="From date" value={from} onChange={e => { setFrom(e.target.value); setPage(0); }} />
        <input type="date" aria-label="To date" value={to} onChange={e => { setTo(e.target.value); setPage(0); }} />
        {productId && <Link to="/movements" className="btn btn-ghost btn-sm">Clear product filter</Link>}
      </div>
      <Card className="flush">
        <Async state={movements}>{data => data.content.length === 0 ? <Empty>No movements match these filters.</Empty> : (
          <>
            <div className="table-wrap"><table>
              <thead><tr><th>#</th><th>When</th><th>Type</th><th>Product</th><th>Warehouse</th><th className="num">Qty</th><th className="num">Balance</th><th>Reference</th><th>Reason</th><th>By</th><th /></tr></thead>
              <tbody>{data.content.map(m => (
                <tr key={m.id}>
                  <td className="mono muted">{m.id}</td>
                  <td className="nowrap small">{formatDateTime(m.createdAt)}</td>
                  <td><Badge value={m.type} /></td>
                  <td><Link to={`/products/${m.product.id}`}>{m.product.name}</Link><span className="sub mono">{m.product.sku}</span></td>
                  <td>{m.warehouse.code}</td>
                  <td className="num"><Delta value={m.quantity} /></td>
                  <td className="num">{num(m.balanceAfter)}</td>
                  <td className="small">{reference(m)}</td>
                  <td className="small">{m.reason ?? '—'}</td>
                  <td className="small nowrap">{m.userName}</td>
                  <td className="actions">
                    {m.reversed ? <Badge value="CANCELLED" label="reversed" />
                      : canManage && m.referenceType === 'MANUAL' && !m.reversesId &&
                        <button className="btn btn-ghost btn-sm" onClick={() => setReversing(m)}>Reverse</button>}
                  </td>
                </tr>
              ))}</tbody>
            </table></div>
            <Pagination page={data.page} totalPages={data.totalPages} total={data.totalElements} onChange={setPage} />
          </>
        )}</Async>
      </Card>
      {reversing && <ReverseModal movement={reversing} onClose={() => setReversing(null)}
                                  onDone={msg => { setReversing(null); setNotice(msg); void movements.reload(); }} />}
    </>
  );
}

function ReverseModal({ movement, onClose, onDone }: { movement: Movement; onClose: () => void; onDone: (msg: string) => void }) {
  const [reason, setReason] = useState('');
  const action = useAction();
  const { key } = useIdempotencyKey();
  const submit = () => action.run(async () => {
    const r = await movementApi.reverse(movement.id, reason, key);
    onDone(`Recorded reversal #${r.id} (${r.quantity > 0 ? '+' : ''}${r.quantity} ${movement.product.sku}).`);
  });
  return (
    <Modal title={`Reverse movement #${movement.id}`} onClose={onClose}>
      <p className="muted small">
        Adds a new entry of <strong>{movement.quantity > 0 ? '' : '+'}{-movement.quantity}</strong> {movement.product.unit} of {movement.product.sku} in {movement.warehouse.code}.
        The original entry stays in the ledger.
      </p>
      <form onSubmit={preventDefault(submit)}>
        <Field label="Why is it being reversed?">
          <input value={reason} onChange={e => setReason(e.target.value)} required maxLength={200} placeholder="e.g. entered twice" />
        </Field>
        <FormActions busy={action.busy} error={action.error} submitLabel="Record reversal" onCancel={onClose} />
      </form>
    </Modal>
  );
}

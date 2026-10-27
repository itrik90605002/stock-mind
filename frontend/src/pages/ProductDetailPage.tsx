import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { movementApi, productApi, stockApi } from '../api/endpoints';
import type { StockRow } from '../api/types';
import { useAction, useApi } from '../api/useApi';
import { useAuth } from '../auth/AuthContext';
import { ProductForm } from '../components/ProductForm';
import { AdjustModal, IssueModal, SettingsModal } from '../components/StockModals';
import {
  Alert, Async, Badge, Card, Delta, Empty, formatDateTime, money, moneyExact, num, PageHeader,
} from '../components/ui';

type Dialog = { kind: 'issue' | 'adjust' | 'settings'; row: StockRow } | null;

/** One product across the user's warehouses: stock, the actions on it, and its movement history. */
export function ProductDetailPage() {
  const id = Number(useParams().id);
  const { canManage } = useAuth();
  const [editing, setEditing] = useState(false);
  const [dialog, setDialog] = useState<Dialog>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const product = useApi(() => productApi.get(id), [id]);
  const stock = useApi(() => stockApi.forProduct(id), [id]);
  const movements = useApi(() => movementApi.list({ productId: id, size: 15 }), [id]);
  const action = useAction();

  const done = (message: string) => {
    setDialog(null);
    setNotice(message);
    void stock.reload();
    void movements.reload();
  };

  const toggleArchive = () => action.run(async () => {
    const p = product.data!;
    product.setData(p.archived ? await productApi.restore(id) : await productApi.archive(id));
  });

  return (
    <Async state={product}>{p => (
      <>
        <Link to="/inventory" className="small">← Inventory</Link>
        <PageHeader title={p.name}
                    subtitle={<><span className="mono">{p.sku}</span> · {p.category} · {p.unit} · {moneyExact(p.unitCost)} per {p.unit}{p.archived && <> · <Badge value="CANCELLED" label="archived" /></>}</>}
                    actions={canManage && <>
                      <button className="btn btn-ghost" onClick={() => setEditing(true)}>Edit</button>
                      <button className="btn btn-ghost" onClick={toggleArchive} disabled={action.busy}>{p.archived ? 'Restore' : 'Archive'}</button>
                    </>} />
        <Alert>{action.error}</Alert>
        <Alert kind="success">{notice}</Alert>
        <div className="grid-3">
          <Card title="Stock by warehouse" className="flush">
            <Async state={stock}>{rows => rows.length === 0 ? <Empty>Not stocked in any of your warehouses.</Empty> : (
              <div className="table-wrap"><table>
                <thead><tr><th>Warehouse</th><th className="num">On hand</th><th className="num">Reserved</th><th className="num">Available</th><th className="num">Reorder at</th><th className="num">Value</th><th /></tr></thead>
                <tbody>{rows.map(r => (
                  <tr key={r.id} className={r.low ? 'row-low' : ''}>
                    <td>{r.warehouse.code} <span className="sub">{r.warehouse.name}</span></td>
                    <td className="num">{num(r.onHand)}</td>
                    <td className="num">{num(r.reserved)}</td>
                    <td className={`num ${r.low ? 'low' : ''}`}>{num(r.available)}</td>
                    <td className="num">{num(r.reorderPoint)}</td>
                    <td className="num">{money(r.value)}</td>
                    <td className="actions">
                      <button className="btn btn-ghost btn-sm" onClick={() => setDialog({ kind: 'issue', row: r })} disabled={r.available <= 0}>Issue</button>{' '}
                      <button className="btn btn-ghost btn-sm" onClick={() => setDialog({ kind: 'adjust', row: r })}>Adjust</button>{' '}
                      {canManage && <button className="btn btn-ghost btn-sm" onClick={() => setDialog({ kind: 'settings', row: r })}>Settings</button>}
                    </td>
                  </tr>
                ))}</tbody>
                <tfoot><tr>
                  <th>Total</th>
                  <th className="num">{num(rows.reduce((s, r) => s + r.onHand, 0))}</th>
                  <th className="num">{num(rows.reduce((s, r) => s + r.reserved, 0))}</th>
                  <th className="num">{num(rows.reduce((s, r) => s + r.available, 0))}</th>
                  <th /><th className="num">{money(rows.reduce((s, r) => s + r.value, 0))}</th><th />
                </tr></tfoot>
              </table></div>
            )}</Async>
          </Card>
          <Card title="Details">
            <dl className="facts">
              <div><dt>Supplier</dt><dd>{p.supplierName ?? '—'}</dd></div>
              <div><dt>Unit</dt><dd>{p.unit}</dd></div>
            </dl>
            {p.description && <p className="muted">{p.description}</p>}
            <div className="next-links">
              <Link to="/replenish">Reorder suggestions →</Link>
              {canManage && <Link to="/transfers?new=1">Move stock between warehouses →</Link>}
            </div>
          </Card>
        </div>
        <Card title="Recent movements" className="flush" actions={<Link to={`/movements?productId=${id}`} className="small">Full history →</Link>}>
          <Async state={movements}>{data => data.content.length === 0 ? <Empty>No movements yet.</Empty> : (
            <div className="table-wrap"><table>
              <thead><tr><th>When</th><th>Type</th><th>Warehouse</th><th className="num">Qty</th><th className="num">Balance</th><th>Reason</th><th>By</th></tr></thead>
              <tbody>{data.content.map(m => (
                <tr key={m.id}>
                  <td className="nowrap small">{formatDateTime(m.createdAt)}</td>
                  <td><Badge value={m.type} /></td>
                  <td>{m.warehouse.code}</td>
                  <td className="num"><Delta value={m.quantity} /></td>
                  <td className="num">{num(m.balanceAfter)}</td>
                  <td className="small">{m.reason ?? '—'}</td>
                  <td className="small">{m.userName}</td>
                </tr>
              ))}</tbody>
            </table></div>
          )}</Async>
        </Card>
        {dialog?.kind === 'issue' && <IssueModal row={dialog.row} onClose={() => setDialog(null)} onDone={done} />}
        {dialog?.kind === 'adjust' && <AdjustModal row={dialog.row} onClose={() => setDialog(null)} onDone={done} />}
        {dialog?.kind === 'settings' && <SettingsModal row={dialog.row} onClose={() => setDialog(null)} onDone={done} />}
        {editing && <ProductForm product={p} onClose={() => setEditing(false)} onSaved={saved => { product.setData(saved); setEditing(false); }} />}
      </>
    )}</Async>
  );
}

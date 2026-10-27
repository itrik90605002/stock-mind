import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { productApi, stockApi } from '../api/endpoints';
import type { StockRow } from '../api/types';
import { useApi } from '../api/useApi';
import { useAuth } from '../auth/AuthContext';
import { AdjustModal, IssueModal, SettingsModal } from '../components/StockModals';
import { Alert, Async, Card, Empty, money, num, Pagination, WarehouseSelect } from '../components/ui';

type Dialog = { kind: 'issue' | 'adjust' | 'settings'; row: StockRow } | null;

function Meter({ row }: { row: StockRow }) {
  const target = Math.max(row.reorderPoint * 2, 1);
  const pct = Math.min(100, Math.round((row.available / target) * 100));
  const cls = row.low ? 'meter-low' : row.available <= row.reorderPoint * 1.25 ? 'meter-mid' : '';
  return <div className={`meter ${cls}`} title={`${pct}% of 2× reorder point`}><span style={{ width: `${pct}%` }} /></div>;
}

export function StockPanel() {
  const { myWarehouses, canManage } = useAuth();
  const [params, setParams] = useSearchParams();
  const [warehouseId, setWarehouseId] = useState<number | ''>('');
  const [q, setQ] = useState('');
  const [category, setCategory] = useState('');
  const [page, setPage] = useState(0);
  const [dialog, setDialog] = useState<Dialog>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const lowOnly = params.get('low') === '1';

  const categories = useApi(() => productApi.categories(), []);
  const stock = useApi(() => stockApi.list({ warehouseId: warehouseId || undefined, q, category, lowOnly, page, size: 50 }),
    [warehouseId, q, category, lowOnly, page]);

  const done = (message: string) => { setDialog(null); setNotice(message); void stock.reload(); };

  return (
    <>
      <div className="toolbar">
        <input type="search" placeholder="Search SKU or product…" value={q} onChange={e => { setQ(e.target.value); setPage(0); }} />
        <WarehouseSelect warehouses={myWarehouses} value={warehouseId} onChange={v => { setWarehouseId(v); setPage(0); }} allLabel="All my warehouses" />
        <select value={category} onChange={e => { setCategory(e.target.value); setPage(0); }}>
          <option value="">All categories</option>
          {categories.data?.map(c => <option key={c}>{c}</option>)}
        </select>
        <label className="check">
          <input type="checkbox" checked={lowOnly} onChange={e => { setParams(e.target.checked ? { low: '1' } : {}); setPage(0); }} />
          Low stock only
        </label>
      </div>
      <Alert kind="success">{notice}</Alert>
      <Card className="flush">
        <Async state={stock}>{data => data.content.length === 0 ? <Empty>No stock matches these filters.</Empty> : (
          <>
            <div className="table-wrap"><table>
              <thead><tr>
                <th>Product</th><th>Warehouse</th><th className="num">On hand</th><th className="num">Reserved</th>
                <th className="num">Available</th><th>Level</th><th className="num">Reorder at</th><th className="num">Value</th><th />
              </tr></thead>
              <tbody>{data.content.map(r => (
                <tr key={r.id} className={r.low ? 'row-low' : ''}>
                  <td className="product-cell"><Link to={`/products/${r.product.id}`}>{r.product.name}</Link><span className="sub"><span className="mono">{r.product.sku}</span> · {r.category}</span></td>
                  <td>{r.warehouse.code}</td>
                  <td className="num">{num(r.onHand)}</td>
                  <td className="num muted">{r.reserved ? num(r.reserved) : '—'}</td>
                  <td className={`num ${r.low ? 'low' : ''}`}>{num(r.available)} <span className="muted small">{r.product.unit}</span></td>
                  <td><Meter row={r} /></td>
                  <td className="num">{num(r.reorderPoint)}</td>
                  <td className="num">{money(r.value)}</td>
                  <td className="actions">
                    <button className="btn btn-ghost btn-sm" onClick={() => setDialog({ kind: 'issue', row: r })} disabled={r.available <= 0}>Issue</button>{' '}
                    <button className="btn btn-ghost btn-sm" onClick={() => setDialog({ kind: 'adjust', row: r })}>Adjust</button>{' '}
                    {canManage && <button className="btn btn-ghost btn-sm" onClick={() => setDialog({ kind: 'settings', row: r })}>Settings</button>}
                  </td>
                </tr>
              ))}</tbody>
            </table></div>
            <Pagination page={data.page} totalPages={data.totalPages} total={data.totalElements} onChange={setPage} />
          </>
        )}</Async>
      </Card>
      {dialog?.kind === 'issue' && <IssueModal row={dialog.row} onClose={() => setDialog(null)} onDone={done} />}
      {dialog?.kind === 'adjust' && <AdjustModal row={dialog.row} onClose={() => setDialog(null)} onDone={done} />}
      {dialog?.kind === 'settings' && <SettingsModal row={dialog.row} onClose={() => setDialog(null)} onDone={done} />}
    </>
  );
}

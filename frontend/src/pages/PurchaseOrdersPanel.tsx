import { useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { purchaseOrderApi, supplierApi } from '../api/endpoints';
import type { PurchaseOrder, PurchaseOrderStatus } from '../api/types';
import { useApi } from '../api/useApi';
import { useAuth } from '../auth/AuthContext';
import { PlusIcon } from '../components/icons';
import { PurchaseOrderForm } from '../components/PurchaseOrderForm';
import { Alert, Async, Badge, Card, Empty, formatDate, money, num, Pagination, todayIso, WarehouseSelect } from '../components/ui';

/** Status filters in lifecycle order, named by what each status needs. The key is kept in the URL (?view=). */
const VIEWS: { key: string; label: string; status: PurchaseOrderStatus | ''; open?: boolean }[] = [
  { key: 'open', label: 'All open', status: '', open: true },
  { key: 'draft', label: 'To place', status: 'DRAFT' },
  { key: 'ordered', label: 'Awaiting delivery', status: 'ORDERED' },
  { key: 'partial', label: 'Part received', status: 'PARTIALLY_RECEIVED' },
  { key: 'received', label: 'Received', status: 'RECEIVED' },
  { key: 'cancelled', label: 'Cancelled', status: 'CANCELLED' },
  { key: 'all', label: 'All', status: '' },
];

export const isLate = (po: PurchaseOrder) =>
  (po.status === 'ORDERED' || po.status === 'PARTIALLY_RECEIVED') && !!po.expectedDate && po.expectedDate < todayIso();

/** Replenish step 2: purchase orders from draft to placed to received. Receiving books the stock in. */
export function PurchaseOrdersPanel() {
  const { myWarehouses, canManage } = useAuth();
  const navigate = useNavigate();
  const notice = (useLocation().state as { notice?: string } | null)?.notice;
  const [params, setParams] = useSearchParams();
  const view = VIEWS.find(v => v.key === params.get('view')) ?? VIEWS[0];
  const [warehouseId, setWarehouseId] = useState<number | ''>('');
  const [supplierId, setSupplierId] = useState<number | ''>(params.get('supplierId') ? Number(params.get('supplierId')) : '');
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const suppliers = useApi(() => supplierApi.list(), []);
  const { status, open } = view;
  const orders = useApi(() => purchaseOrderApi.list({ status, open, warehouseId: warehouseId || undefined, supplierId: supplierId || undefined, page }),
    [status, open, warehouseId, supplierId, page]);

  return (
    <>
      <Alert kind="success">{notice}</Alert>
      <div className="segmented" role="group" aria-label="Order status">
        {VIEWS.map(v => (
          <button key={v.key} aria-pressed={view === v} className={view === v ? 'active' : ''}
                  onClick={() => { const next = new URLSearchParams(params); next.set('view', v.key); setParams(next, { replace: true }); setPage(0); }}>
            {v.label}
          </button>
        ))}
      </div>
      <div className="toolbar">
        <WarehouseSelect warehouses={myWarehouses} value={warehouseId} onChange={v => { setWarehouseId(v); setPage(0); }} allLabel="All my warehouses" />
        <select value={supplierId} onChange={e => { setSupplierId(e.target.value ? Number(e.target.value) : ''); setPage(0); }}>
          <option value="">All suppliers</option>
          {suppliers.data?.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
        {canManage && <button className="btn btn-primary toolbar-end" onClick={() => setCreating(true)}><PlusIcon size={16} />New order</button>}
      </div>
      <Card className="flush">
        <Async state={orders}>{data => data.content.length === 0 ? <Empty>No purchase orders here.{view.key === 'draft' && canManage && <> Start from <Link to="/replenish">what to reorder</Link>.</>}</Empty> : (
          <>
            <div className="table-wrap"><table>
              <thead><tr><th>PO</th><th>Supplier</th><th>Warehouse</th><th>Status</th><th>Expected</th><th className="num">Units</th><th className="num">Still expected</th><th className="num">Value</th></tr></thead>
              <tbody>{data.content.map(po => (
                <tr key={po.id}>
                  <td><Link to={`/purchase-orders/${po.id}`}>#{po.id}</Link></td>
                  <td>{po.supplierName}{po.note && <span className="sub">{po.note}</span>}</td>
                  <td>{po.warehouse.code}</td>
                  <td><Badge value={po.status} /></td>
                  <td className="nowrap">{formatDate(po.expectedDate)} {isLate(po) && <Badge value="LOW" label="late" />}</td>
                  <td className="num">{num(po.totalUnits)}</td>
                  <td className="num">{po.remainingUnits ? num(po.remainingUnits) : '—'}</td>
                  <td className="num">{money(po.totalValue)}</td>
                </tr>
              ))}</tbody>
            </table></div>
            <Pagination page={data.page} totalPages={data.totalPages} total={data.totalElements} onChange={setPage} />
          </>
        )}</Async>
      </Card>
      {creating && <PurchaseOrderForm onClose={() => setCreating(false)} onSaved={po => navigate(`/purchase-orders/${po.id}`)} />}
    </>
  );
}

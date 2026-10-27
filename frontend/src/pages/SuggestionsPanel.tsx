import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { reorderApi } from '../api/endpoints';
import { useAction, useApi, useIdempotencyKey } from '../api/useApi';
import { useAuth } from '../auth/AuthContext';
import { RefreshIcon } from '../components/icons';
import { Alert, Async, Card, Empty, formatDateTime, money, num, WarehouseSelect } from '../components/ui';

export function SuggestionsPanel() {
  const { myWarehouses, canManage } = useAuth();
  const navigate = useNavigate();
  const [warehouseId, setWarehouseId] = useState<number | ''>('');
  const state = useApi(() => reorderApi.suggestions(warehouseId || undefined), [warehouseId]);
  const [selected, setSelected] = useState<Record<number, number>>({}); // suggestion id -> quantity
  const recompute = useAction();
  const create = useAction();
  const { key, renew } = useIdempotencyKey();

  const suggestions = state.data?.suggestions ?? [];
  const selectedTotal = useMemo(() => suggestions.filter(s => selected[s.id] != null)
    .reduce((sum, s) => sum + s.unitCost * selected[s.id], 0), [suggestions, selected]);

  const toggle = (id: number, qty: number) => setSelected(prev => {
    const next = { ...prev };
    if (next[id] != null) delete next[id]; else next[id] = qty;
    return next;
  });

  const createOrders = () => create.run(async () => {
    const items = suggestions.filter(s => selected[s.id] != null)
      .map(s => ({ productId: s.product.id, warehouseId: s.warehouse.id, quantity: selected[s.id] }));
    const result = await reorderApi.createOrders(items, key);
    renew();
    setSelected({});
    navigate(result.orders.length === 1 ? `/purchase-orders/${result.orders[0].id}` : '/replenish/orders?view=draft',
      { state: { notice: result.orders.length === 1
        ? 'Draft purchase order created. Check it, then place it with the supplier.'
        : `${result.orders.length} draft purchase orders created, one per supplier and warehouse. Review each one and place it with the supplier.` } });
  });

  return (
    <>
      <div className="toolbar">
        <WarehouseSelect warehouses={myWarehouses} value={warehouseId} onChange={setWarehouseId} allLabel="All my warehouses" />
        <span className="muted small">
          From the last 30–90 days of usage, lead times and stock already on order.
          {state.data?.computedAt && <> Calculated {formatDateTime(state.data.computedAt)}.</>}
        </span>
        {canManage && <button className="btn btn-ghost toolbar-end" disabled={recompute.busy}
                              onClick={() => recompute.run(async () => { const r = await reorderApi.recompute(); await state.reload(); return r; }, 'Recalculated from the latest movements.')}>
          <RefreshIcon size={16} />{recompute.busy ? 'Calculating…' : 'Recalculate'}</button>}
      </div>
      <Alert>{recompute.error ?? create.error}</Alert>
      <Alert kind="success">{recompute.message}</Alert>
      {!canManage && <Alert kind="info">A manager turns these suggestions into purchase orders. You will see the deliveries under Order &amp; receive.</Alert>}
      {canManage && suggestions.length > 0 && Object.keys(selected).length === 0 && (
        <Alert kind="info">Tick the items to order, adjust quantities if needed, then create draft purchase orders. Items leave this list once they are on an order.</Alert>
      )}
      {canManage && Object.keys(selected).length > 0 && (
        <div className="card row" style={{ marginBottom: '1rem', justifyContent: 'space-between' }}>
          <span><strong>{Object.keys(selected).length}</strong> selected · estimated {money(selectedTotal)}. One draft order is created per supplier and warehouse.</span>
          <button className="btn btn-primary" disabled={create.busy} onClick={createOrders}>{create.busy ? 'Creating…' : 'Create draft orders →'}</button>
        </div>
      )}
      <Card className="flush">
        <Async state={state}>{() => suggestions.length === 0 ? <Empty>Nothing needs reordering right now.</Empty> : (
          <div className="table-wrap"><table>
            <thead><tr>
              {canManage && <th aria-label="Select" />}
              <th>Product</th><th>Warehouse</th><th className="num">Available</th><th className="num">On order</th>
              <th className="num">Avg/day</th><th className="num">Days left</th><th className="num">Lead time</th>
              <th className="num">Suggested</th><th className="num">Est. cost</th><th>Why</th>
            </tr></thead>
            <tbody>{suggestions.map(s => (
              <tr key={s.id} className={s.daysOfCover != null && s.daysOfCover < s.leadTimeDays ? 'row-low' : ''}>
                {canManage && <td><input type="checkbox" aria-label={`Select ${s.product.sku}`} checked={selected[s.id] != null}
                                         onChange={() => toggle(s.id, s.suggestedQty)} disabled={!s.supplierId} /></td>}
                <td className="product-cell"><Link to={`/products/${s.product.id}`}>{s.product.name}</Link><span className="sub"><span className="mono">{s.product.sku}</span> · {s.supplierName ?? 'no supplier'}</span></td>
                <td>{s.warehouse.code}</td>
                <td className={`num ${s.available === 0 ? 'low' : ''}`}>{num(s.available)}</td>
                <td className="num">{s.incoming ? num(s.incoming) : '—'}</td>
                <td className="num">{s.avgDailyUsage.toFixed(1)}{s.trendPct != null && Math.abs(s.trendPct) >= 10 && s.available > 0 &&
                  <span className={`sub ${s.trendPct > 0 ? 'low' : ''}`}>{s.trendPct > 0 ? '▲' : '▼'} {Math.abs(Math.round(s.trendPct))}%</span>}</td>
                <td className="num">{s.daysOfCover == null ? '—' : s.daysOfCover}</td>
                <td className="num">{s.leadTimeDays}d</td>
                <td className="num">
                  {canManage && selected[s.id] != null
                    ? <input type="number" min={1} style={{ width: 84 }} value={selected[s.id]} aria-label={`Quantity for ${s.product.sku}`}
                             onChange={e => setSelected({ ...selected, [s.id]: Math.max(1, Number(e.target.value)) })} />
                    : <strong>{num(s.suggestedQty)}</strong>}
                </td>
                <td className="num">{money(s.estimatedCost)}</td>
                <td className="small why-cell">{s.explanation}</td>
              </tr>
            ))}</tbody>
          </table></div>
        )}</Async>
      </Card>
    </>
  );
}

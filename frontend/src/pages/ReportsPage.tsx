import { useState } from 'react';
import { Link } from 'react-router-dom';
import { dashboardApi } from '../api/endpoints';
import type { MoverRow } from '../api/types';
import { useApi } from '../api/useApi';
import { useAuth } from '../auth/AuthContext';
import { Async, Card, money, num, PageHeader, WarehouseSelect } from '../components/ui';

export function ReportsPage() {
  const { myWarehouses } = useAuth();
  const [warehouseId, setWarehouseId] = useState<number | ''>('');
  const [days, setDays] = useState(30);
  const valuation = useApi(() => dashboardApi.valuation(warehouseId || undefined), [warehouseId]);
  const movers = useApi(() => dashboardApi.movers(days, warehouseId || undefined), [days, warehouseId]);

  return (
    <>
      <PageHeader title="Reports" subtitle="Stock valuation and product velocity."
                  actions={<WarehouseSelect warehouses={myWarehouses} value={warehouseId} onChange={setWarehouseId} allLabel="All my warehouses" />} />
      <Card title="Stock valuation by warehouse and category" className="flush">
        <Async state={valuation}>{rows => {
          const total = rows.reduce((s, r) => s + r.value, 0);
          const max = Math.max(1, ...rows.map(r => r.value));
          return (
            <div className="table-wrap"><table>
              <thead><tr><th>Warehouse</th><th>Category</th><th className="num">Products</th><th className="num">Units</th><th className="num">Value</th><th style={{ width: '25%' }}>Share</th></tr></thead>
              <tbody>{rows.map(r => (
                <tr key={`${r.warehouseCode}-${r.category}`}>
                  <td>{r.warehouseCode}</td><td>{r.category}</td>
                  <td className="num">{r.products}</td><td className="num">{num(r.units)}</td><td className="num">{money(r.value)}</td>
                  <td><div className="meter"><span style={{ width: `${(r.value / max) * 100}%`, background: 'var(--primary)' }} /></div></td>
                </tr>
              ))}</tbody>
              <tfoot><tr><th colSpan={4}>Total</th><th className="num">{money(total)}</th><th /></tr></tfoot>
            </table></div>
          );
        }}</Async>
      </Card>
      <div className="row" style={{ margin: '1.5rem 0 .75rem' }}>
        <h2>Product velocity</h2>
        <select value={days} onChange={e => setDays(Number(e.target.value))} style={{ width: 'auto' }}>
          {[7, 30, 90].map(d => <option key={d} value={d}>Last {d} days</option>)}
        </select>
      </div>
      <Async state={movers}>{m => (
        <div className="grid-2">
          <MoverTable title="Fastest moving" rows={m.fastest} />
          <MoverTable title="Slowest moving (capital tied up)" rows={m.slowest} />
        </div>
      )}</Async>
    </>
  );
}

function MoverTable({ title, rows }: { title: string; rows: MoverRow[] }) {
  return (
    <Card title={title} className="flush">
      <div className="table-wrap"><table>
        <thead><tr><th>Product</th><th className="num">Units out</th><th className="num">On hand</th><th className="num">Stock value</th></tr></thead>
        <tbody>{rows.map(r => (
          <tr key={r.productId}>
            <td><Link to={`/products/${r.productId}`}>{r.name}</Link><span className="sub">{r.category}</span></td>
            <td className="num">{num(r.unitsOut)}</td><td className="num">{num(r.onHand)}</td><td className="num">{money(r.stockValue)}</td>
          </tr>
        ))}</tbody>
      </table></div>
    </Card>
  );
}

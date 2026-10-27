import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { productApi, supplierApi } from '../api/endpoints';
import { useApi } from '../api/useApi';
import { useAuth } from '../auth/AuthContext';
import { PlusIcon } from '../components/icons';
import { ProductForm } from '../components/ProductForm';
import { Async, Badge, Card, Empty, moneyExact, Pagination } from '../components/ui';

/** Inventory → Product catalogue: the products shared by all warehouses. Managers add and edit them here. */
export function CatalogPanel() {
  const { canManage } = useAuth();
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const [category, setCategory] = useState('');
  const [supplierId, setSupplierId] = useState<number | ''>('');
  const [includeArchived, setIncludeArchived] = useState(false);
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);

  const categories = useApi(() => productApi.categories(), []);
  const suppliers = useApi(() => supplierApi.list(), []);
  const products = useApi(() => productApi.search({ q, category, supplierId: supplierId || undefined, includeArchived, page, size: 25 }),
    [q, category, supplierId, includeArchived, page]);

  return (
    <>
      <div className="toolbar">
        <input type="search" placeholder="Search SKU or name…" value={q} onChange={e => { setQ(e.target.value); setPage(0); }} />
        <select value={category} onChange={e => { setCategory(e.target.value); setPage(0); }}>
          <option value="">All categories</option>
          {categories.data?.map(c => <option key={c}>{c}</option>)}
        </select>
        <select value={supplierId} onChange={e => { setSupplierId(e.target.value ? Number(e.target.value) : ''); setPage(0); }}>
          <option value="">All suppliers</option>
          {suppliers.data?.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
        <label className="check"><input type="checkbox" checked={includeArchived} onChange={e => setIncludeArchived(e.target.checked)} />Show archived</label>
        {canManage && <button className="btn btn-primary toolbar-end" onClick={() => setCreating(true)}><PlusIcon size={16} />New product</button>}
      </div>
      <Card className="flush">
        <Async state={products}>{data => data.content.length === 0 ? <Empty>No products found.</Empty> : (
          <>
            <div className="table-wrap"><table>
              <thead><tr><th>SKU</th><th>Name</th><th>Category</th><th>Unit</th><th className="num">Unit cost</th><th>Supplier</th><th /></tr></thead>
              <tbody>{data.content.map(p => (
                <tr key={p.id}>
                  <td className="mono">{p.sku}</td>
                  <td><Link to={`/products/${p.id}`}>{p.name}</Link></td>
                  <td>{p.category}</td>
                  <td>{p.unit}</td>
                  <td className="num">{moneyExact(p.unitCost)}</td>
                  <td>{p.supplierName ?? <span className="muted">—</span>}</td>
                  <td>{p.archived && <Badge value="CANCELLED" label="archived" />}</td>
                </tr>
              ))}</tbody>
            </table></div>
            <Pagination page={data.page} totalPages={data.totalPages} total={data.totalElements} onChange={setPage} />
          </>
        )}</Async>
      </Card>
      {creating && <ProductForm onClose={() => setCreating(false)} onSaved={p => navigate(`/products/${p.id}`)} />}
    </>
  );
}

import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { RequireAuth } from './auth/RequireAuth';
import { Layout } from './components/Layout';
import { Empty } from './components/ui';
import { AccountPage } from './pages/AccountPage';
import { AdminPage } from './pages/AdminPage';
import { AssistantPage } from './pages/AssistantPage';
import { CatalogPanel } from './pages/CatalogPanel';
import { HomePage } from './pages/HomePage';
import { InventoryPage } from './pages/InventoryPage';
import { LoginPage } from './pages/LoginPage';
import { MovementsPage } from './pages/MovementsPage';
import { ProductDetailPage } from './pages/ProductDetailPage';
import { PurchaseOrderDetailPage } from './pages/PurchaseOrderDetailPage';
import { PurchaseOrdersPanel } from './pages/PurchaseOrdersPanel';
import { ReplenishPage } from './pages/ReplenishPage';
import { ReportsPage } from './pages/ReportsPage';
import { StockPanel } from './pages/StockPanel';
import { SuggestionsPanel } from './pages/SuggestionsPanel';
import { SuppliersPanel } from './pages/SuppliersPanel';
import { TransferDetailPage } from './pages/TransferDetailPage';
import { TransfersPage } from './pages/TransfersPage';

/** Sends a pre-redesign URL to its new home, keeping the query string (filters such as ?low=1). */
function Moved({ to }: { to: string }) {
  const { search } = useLocation();
  const [path, query] = to.split('?');
  const params = new URLSearchParams(query);
  new URLSearchParams(search).forEach((v, k) => params.set(k, v));
  const qs = params.toString();
  return <Navigate to={qs ? `${path}?${qs}` : path} replace />;
}

/**
 * Route table, grouped by the stock journey: Inventory (what we have), Replenish (buy more),
 * Transfers (rebalance), Movements (audit). Role checks here are for UX only; the backend enforces
 * every permission.
 */
export function App() {
  return (
    <Routes>
      <Route path="login" element={<LoginPage />} />
      <Route element={<RequireAuth><Layout /></RequireAuth>}>
        <Route index element={<HomePage />} />
        <Route path="assistant" element={<AssistantPage />} />

        <Route path="inventory" element={<InventoryPage />}>
          <Route index element={<StockPanel />} />
          <Route path="catalogue" element={<CatalogPanel />} />
        </Route>
        <Route path="products/:id" element={<ProductDetailPage />} />

        <Route path="replenish" element={<ReplenishPage />}>
          <Route index element={<SuggestionsPanel />} />
          <Route path="orders" element={<PurchaseOrdersPanel />} />
          <Route path="suppliers" element={<SuppliersPanel />} />
        </Route>
        <Route path="purchase-orders/:id" element={<PurchaseOrderDetailPage />} />

        <Route path="transfers" element={<TransfersPage />} />
        <Route path="transfers/:id" element={<TransferDetailPage />} />
        <Route path="movements" element={<MovementsPage />} />
        <Route path="reports" element={<RequireAuth roles={['ADMIN', 'MANAGER']}><ReportsPage /></RequireAuth>} />
        <Route path="admin" element={<RequireAuth roles={['ADMIN']}><AdminPage /></RequireAuth>} />
        <Route path="account" element={<AccountPage />} />

        {/* Pre-redesign URLs */}
        <Route path="stock" element={<Moved to="/inventory" />} />
        <Route path="products" element={<Moved to="/inventory/catalogue" />} />
        <Route path="reorder" element={<Moved to="/replenish" />} />
        <Route path="purchase-orders" element={<Moved to="/replenish/orders" />} />
        <Route path="suppliers" element={<Moved to="/replenish/suppliers" />} />
        <Route path="ledger" element={<Moved to="/movements" />} />

        <Route path="*" element={<Empty>Page not found.</Empty>} />
      </Route>
    </Routes>
  );
}

import { ReactNode } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { RequireAuth } from './auth/RequireAuth';
import { Layout } from './components/Layout';
import { Empty, PageHeader } from './components/ui';
import { AccountPage } from './pages/AccountPage';
import { AdminPage } from './pages/AdminPage';
import { AssistantPage } from './pages/AssistantPage';
import { CatalogPanel } from './pages/CatalogPanel';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { MovementsPage } from './pages/MovementsPage';
import { ProductDetailPage } from './pages/ProductDetailPage';
import { PurchaseOrderDetailPage } from './pages/PurchaseOrderDetailPage';
import { PurchaseOrdersPanel } from './pages/PurchaseOrdersPanel';
import { ReportsPage } from './pages/ReportsPage';
import { StockPage } from './pages/StockPage';
import { StockPanel } from './pages/StockPanel';
import { SuggestionsPanel } from './pages/SuggestionsPanel';
import { SuppliersPanel } from './pages/SuppliersPanel';
import { TransferDetailPage } from './pages/TransferDetailPage';
import { TransfersPage } from './pages/TransfersPage';

function Moved({ to }: { to: string }) {
  const { search } = useLocation();
  const [path, query] = to.split('?');
  const params = new URLSearchParams(query);
  new URLSearchParams(search).forEach((v, k) => params.set(k, v));
  const qs = params.toString();
  return <Navigate to={qs ? `${path}?${qs}` : path} replace />;
}

function Screen({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return <><PageHeader title={title} subtitle={subtitle} />{children}</>;
}

export function App() {
  return (
    <Routes>
      <Route path="login" element={<LoginPage />} />
      <Route element={<RequireAuth><Layout /></RequireAuth>}>
        <Route index element={<HomePage />} />
        <Route path="assistant" element={<AssistantPage />} />

        <Route path="inventory" element={<StockPage />}>
          <Route index element={<CatalogPanel />} />
          <Route path="levels" element={<StockPanel />} />
        </Route>
        <Route path="inventory/catalogue" element={<Moved to="/inventory" />} />
        <Route path="products/:id" element={<ProductDetailPage />} />

        <Route path="replenish" element={<Screen title="What to reorder" subtitle="Products running low, with a suggested order quantity based on recent usage."><SuggestionsPanel /></Screen>} />
        <Route path="replenish/orders" element={<Screen title="Purchase orders" subtitle="Orders to suppliers: draft → placed → received."><PurchaseOrdersPanel /></Screen>} />
        <Route path="replenish/suppliers" element={<Screen title="Suppliers" subtitle="Who you buy from, and how long their deliveries take."><SuppliersPanel /></Screen>} />
        <Route path="purchase-orders/:id" element={<PurchaseOrderDetailPage />} />

        <Route path="transfers" element={<TransfersPage />} />
        <Route path="transfers/:id" element={<TransferDetailPage />} />
        <Route path="movements" element={<MovementsPage />} />
        <Route path="reports" element={<RequireAuth roles={['ADMIN', 'MANAGER']}><ReportsPage /></RequireAuth>} />
        <Route path="admin" element={<RequireAuth roles={['ADMIN']}><AdminPage /></RequireAuth>} />
        <Route path="account" element={<AccountPage />} />

        {/* Pre-redesign URLs */}
        <Route path="stock" element={<Moved to="/inventory/levels" />} />
        <Route path="products" element={<Moved to="/inventory" />} />
        <Route path="reorder" element={<Moved to="/replenish" />} />
        <Route path="purchase-orders" element={<Moved to="/replenish/orders" />} />
        <Route path="suppliers" element={<Moved to="/replenish/suppliers" />} />
        <Route path="ledger" element={<Moved to="/movements" />} />

        <Route path="*" element={<Empty>Page not found.</Empty>} />
      </Route>
    </Routes>
  );
}

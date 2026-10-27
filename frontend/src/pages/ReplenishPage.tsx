import { Outlet } from 'react-router-dom';
import { PageHeader, SectionTabs } from '../components/ui';

/**
 * Buying more stock, laid out as the steps it takes: see what is running out, turn it into purchase
 * orders, and receive the deliveries. Suppliers sit alongside as the reference data orders depend on.
 */
export function ReplenishPage() {
  return (
    <>
      <PageHeader title="Replenish" subtitle="Spot what is running out, order it from suppliers, and book deliveries in." />
      <SectionTabs tabs={[
        { to: '/replenish', label: 'What to reorder', end: true, step: 1 },
        { to: '/replenish/orders', label: 'Order & receive', step: 2 },
        { to: '/replenish/suppliers', label: 'Suppliers' },
      ]} />
      <Outlet />
    </>
  );
}

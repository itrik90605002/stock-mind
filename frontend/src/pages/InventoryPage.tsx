import { Outlet } from 'react-router-dom';
import { PageHeader, SectionTabs } from '../components/ui';

/** What we have: stock per warehouse, and the product catalogue behind it. */
export function InventoryPage() {
  return (
    <>
      <PageHeader title="Inventory" subtitle="What is on the shelves in each warehouse, and the products you stock." />
      <SectionTabs tabs={[
        { to: '/inventory', label: 'Stock levels', end: true },
        { to: '/inventory/catalogue', label: 'Product catalogue' },
      ]} />
      <Outlet />
    </>
  );
}

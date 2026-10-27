import { Outlet } from 'react-router-dom';
import { PageHeader, SectionTabs } from '../components/ui';

export function StockPage() {
  return (
    <>
      <PageHeader title="Stock" subtitle="The products you stock, and how much of each is in every warehouse." />
      <SectionTabs tabs={[
        { to: '/inventory', label: 'Products', end: true },
        { to: '/inventory/levels', label: 'Stock levels' },
      ]} />
      <Outlet />
    </>
  );
}

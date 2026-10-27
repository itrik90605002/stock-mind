import { useState } from 'react';
import { productApi, supplierApi } from '../api/endpoints';
import { ApiError } from '../api/client';
import type { Product, ProductInput } from '../api/types';
import { useAction, useApi } from '../api/useApi';
import { Field, FormActions, Modal, preventDefault } from './ui';

const UNITS = ['pcs', 'box', 'kg', 'l', 'm', 'pack', 'roll', 'pair'];

/** Create or edit a product (Managers). The SKU is fixed once created. */
export function ProductForm({ product, onClose, onSaved }: {
  product?: Product; onClose: () => void; onSaved: (p: Product) => void;
}) {
  const [form, setForm] = useState<ProductInput>({
    sku: product?.sku ?? '', name: product?.name ?? '', category: product?.category ?? '', unit: product?.unit ?? 'pcs',
    unitCost: product?.unitCost ?? 0, description: product?.description ?? '', supplierId: product?.supplierId ?? null,
  });
  const suppliers = useApi(() => supplierApi.list(), []);
  const categories = useApi(() => productApi.categories(), []);
  const action = useAction();
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const set = <K extends keyof ProductInput>(key: K, value: ProductInput[K]) => setForm(f => ({ ...f, [key]: value }));

  const submit = () => action.run(async () => {
    try {
      const saved = product ? await productApi.update(product.id, form) : await productApi.create(form);
      onSaved(saved);
    } catch (e) {
      if (e instanceof ApiError && e.fieldErrors) setFieldErrors(e.fieldErrors);
      throw e;
    }
  });

  return (
    <Modal title={product ? `Edit ${product.sku}` : 'New product'} onClose={onClose} wide>
      <form className="form-grid" onSubmit={preventDefault(submit)}>
        <Field label="SKU" error={fieldErrors.sku} hint={product ? 'Cannot be changed' : 'Letters, digits, - . _'}>
          <input value={form.sku} onChange={e => set('sku', e.target.value)} disabled={!!product} required maxLength={40} />
        </Field>
        <Field label="Name" error={fieldErrors.name}>
          <input value={form.name} onChange={e => set('name', e.target.value)} required maxLength={200} />
        </Field>
        <Field label="Category" error={fieldErrors.category}>
          <input list="categories" value={form.category} onChange={e => set('category', e.target.value)} required maxLength={80} />
          <datalist id="categories">{categories.data?.map(c => <option key={c} value={c} />)}</datalist>
        </Field>
        <Field label="Unit" error={fieldErrors.unit}>
          <input list="units" value={form.unit} onChange={e => set('unit', e.target.value)} required maxLength={20} />
          <datalist id="units">{UNITS.map(u => <option key={u} value={u} />)}</datalist>
        </Field>
        <Field label="Unit cost (₹)" error={fieldErrors.unitCost}>
          <input type="number" min={0} step="0.01" value={form.unitCost} onChange={e => set('unitCost', Number(e.target.value))} required />
        </Field>
        <Field label="Preferred supplier" hint="Used when turning reorder suggestions into orders">
          <select value={form.supplierId ?? ''} onChange={e => set('supplierId', e.target.value ? Number(e.target.value) : null)}>
            <option value="">None</option>
            {suppliers.data?.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </Field>
        <Field label="Description" span>
          <textarea value={form.description} onChange={e => set('description', e.target.value)} maxLength={2000} />
        </Field>
        <div className="span-2"><FormActions busy={action.busy} error={action.error} submitLabel={product ? 'Save changes' : 'Create product'} onCancel={onClose} /></div>
      </form>
    </Modal>
  );
}

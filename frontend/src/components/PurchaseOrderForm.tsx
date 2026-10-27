import { useState } from 'react';
import { purchaseOrderApi, supplierApi } from '../api/endpoints';
import type { PurchaseOrder } from '../api/types';
import { useAction, useApi, useIdempotencyKey } from '../api/useApi';
import { useAuth } from '../auth/AuthContext';
import { Line, LineEditor, validateLines } from './LineEditor';
import { Field, FormActions, Modal, preventDefault, WarehouseSelect } from './ui';

/** Create a draft purchase order, or revise an existing draft. */
export function PurchaseOrderForm({ order, onClose, onSaved }: { order?: PurchaseOrder; onClose: () => void; onSaved: (po: PurchaseOrder) => void }) {
  const { myWarehouses } = useAuth();
  const suppliers = useApi(() => supplierApi.list(), []);
  const [supplierId, setSupplierId] = useState<number | ''>(order?.supplierId ?? '');
  const [warehouseId, setWarehouseId] = useState<number | ''>(order?.warehouse.id ?? myWarehouses[0]?.id ?? '');
  const [expectedDate, setExpectedDate] = useState(order?.expectedDate ?? '');
  const [note, setNote] = useState(order?.note ?? '');
  const [lines, setLines] = useState<Line[]>(order
    ? order.lines.map(l => ({ product: l.product, quantity: l.quantity, unitCost: String(l.unitCost) }))
    : [{ product: null, quantity: 10 }]);
  const action = useAction();
  const { key } = useIdempotencyKey();

  const submit = () => action.run(async () => {
    const problem = validateLines(lines) ?? (!supplierId || !warehouseId ? 'Choose a supplier and a warehouse.' : null);
    if (problem) throw new Error(problem);
    const body = {
      supplierId: Number(supplierId), warehouseId: Number(warehouseId), expectedDate: expectedDate || null, note,
      lines: lines.map(l => ({ productId: l.product!.id, quantity: l.quantity, unitCost: l.unitCost ? Number(l.unitCost) : null })),
    };
    onSaved(order ? await purchaseOrderApi.revise(order.id, body) : await purchaseOrderApi.create(body, key));
  });

  return (
    <Modal title={order ? `Edit draft PO #${order.id}` : 'New purchase order'} onClose={onClose} wide>
      <form className="form-grid" onSubmit={preventDefault(submit)}>
        <Field label="Supplier">
          <select value={supplierId} onChange={e => setSupplierId(e.target.value ? Number(e.target.value) : '')} required>
            <option value="" disabled>Select supplier…</option>
            {suppliers.data?.map(s => <option key={s.id} value={s.id}>{s.name} ({s.leadTimeDays}d)</option>)}
          </select>
        </Field>
        <Field label="Deliver to"><WarehouseSelect warehouses={myWarehouses} value={warehouseId} onChange={setWarehouseId} required /></Field>
        <Field label="Expected delivery" hint="Defaults to today + supplier lead time when placed">
          <input type="date" value={expectedDate} onChange={e => setExpectedDate(e.target.value)} />
        </Field>
        <Field label="Note"><input value={note} onChange={e => setNote(e.target.value)} maxLength={500} /></Field>
        <div className="span-2">
          <div className="field-label" style={{ marginBottom: '.4rem' }}>Lines (product, quantity, unit cost)</div>
          <LineEditor lines={lines} onChange={setLines} withCost />
        </div>
        <div className="span-2"><FormActions busy={action.busy} error={action.error} submitLabel={order ? 'Save draft' : 'Create draft'} onCancel={onClose} /></div>
      </form>
    </Modal>
  );
}

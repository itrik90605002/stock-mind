import { useState } from 'react';
import { stockApi } from '../api/endpoints';
import type { StockRow } from '../api/types';
import { useAction, useIdempotencyKey } from '../api/useApi';
import { Field, FormActions, Modal, num, preventDefault } from './ui';

interface Props {
  row: StockRow;
  onClose: () => void;
  onDone: (message: string) => void;
}

/**
 * Records goods leaving the warehouse. The Idempotency-Key is kept across retries of this
 * dialog, so pressing Save twice or retrying after a network error records one movement.
 */
export function IssueModal({ row, onClose, onDone }: Props) {
  const [quantity, setQuantity] = useState(1);
  const [reason, setReason] = useState('');
  const action = useAction();
  const { key } = useIdempotencyKey();

  const submit = () => action.run(async () => {
    const m = await stockApi.issue({ productId: row.product.id, warehouseId: row.warehouse.id, quantity, reason }, key);
    onDone(`Issued ${num(quantity)} ${row.product.unit} of ${row.product.sku}. New balance ${num(m.balanceAfter)}.`);
  });

  return (
    <Modal title={`Issue stock · ${row.product.sku}`} onClose={onClose}>
      <p className="muted small">{row.product.name} in {row.warehouse.code}. Available: <strong>{num(row.available)}</strong> {row.product.unit}.</p>
      <form className="form-grid" onSubmit={preventDefault(submit)}>
        <Field label="Quantity">
          <input type="number" min={1} max={row.available} value={quantity} onChange={e => setQuantity(Number(e.target.value))} required />
        </Field>
        <Field label="Reference / reason" hint="e.g. order number">
          <input value={reason} maxLength={255} onChange={e => setReason(e.target.value)} />
        </Field>
        <div className="span-2"><FormActions busy={action.busy} error={action.error} submitLabel="Issue" onCancel={onClose} /></div>
      </form>
    </Modal>
  );
}

const ADJUST_REASONS = ['Cycle count correction', 'Damaged', 'Lost / missing', 'Found stock', 'Expired'];

/** Count corrections, damage and loss. A reason is mandatory and lands in the ledger. */
export function AdjustModal({ row, onClose, onDone }: Props) {
  const [direction, setDirection] = useState<'down' | 'up'>('down');
  const [quantity, setQuantity] = useState(1);
  const [reason, setReason] = useState(ADJUST_REASONS[1]);
  const [detail, setDetail] = useState('');
  const action = useAction();
  const { key } = useIdempotencyKey();

  const submit = () => action.run(async () => {
    const change = direction === 'down' ? -quantity : quantity;
    const fullReason = detail.trim() ? `${reason}: ${detail.trim()}` : reason;
    const m = await stockApi.adjust({ productId: row.product.id, warehouseId: row.warehouse.id, quantityChange: change, reason: fullReason }, key);
    onDone(`Adjusted ${row.product.sku} by ${change > 0 ? '+' : ''}${change}. New balance ${num(m.balanceAfter)}.`);
  });

  return (
    <Modal title={`Adjust stock · ${row.product.sku}`} onClose={onClose}>
      <p className="muted small">{row.product.name} in {row.warehouse.code}. On hand {num(row.onHand)}, reserved {num(row.reserved)}.</p>
      <form className="form-grid" onSubmit={preventDefault(submit)}>
        <Field label="Direction">
          <select value={direction} onChange={e => setDirection(e.target.value as 'down' | 'up')}>
            <option value="down">Decrease (−)</option>
            <option value="up">Increase (+)</option>
          </select>
        </Field>
        <Field label="Quantity">
          <input type="number" min={1} value={quantity} onChange={e => setQuantity(Number(e.target.value))} required />
        </Field>
        <Field label="Reason">
          <select value={reason} onChange={e => setReason(e.target.value)}>
            {ADJUST_REASONS.map(r => <option key={r}>{r}</option>)}
          </select>
        </Field>
        <Field label="Details (optional)">
          <input value={detail} maxLength={200} onChange={e => setDetail(e.target.value)} />
        </Field>
        <div className="span-2"><FormActions busy={action.busy} error={action.error} submitLabel="Record adjustment" onCancel={onClose} /></div>
      </form>
    </Modal>
  );
}

/** Reorder point and lead-time override for one product in one warehouse (Managers). */
export function SettingsModal({ row, onClose, onDone }: Props) {
  const [reorderPoint, setReorderPoint] = useState(row.reorderPoint);
  const [leadTime, setLeadTime] = useState<string>(row.leadTimeDays == null ? '' : String(row.leadTimeDays));
  const action = useAction();

  const submit = () => action.run(async () => {
    await stockApi.settings({
      productId: row.product.id, warehouseId: row.warehouse.id, reorderPoint,
      leadTimeDays: leadTime === '' ? null : Number(leadTime),
    });
    onDone(`Saved planning settings for ${row.product.sku} in ${row.warehouse.code}.`);
  });

  return (
    <Modal title={`Planning settings · ${row.product.sku}`} onClose={onClose}>
      <form className="form-grid" onSubmit={preventDefault(submit)}>
        <Field label="Reorder point" hint="Flag as low at or below this available quantity">
          <input type="number" min={0} value={reorderPoint} onChange={e => setReorderPoint(Number(e.target.value))} required />
        </Field>
        <Field label="Lead time (days)" hint="Leave empty to use the supplier's lead time">
          <input type="number" min={0} max={365} value={leadTime} onChange={e => setLeadTime(e.target.value)} />
        </Field>
        <div className="span-2"><FormActions busy={action.busy} error={action.error} submitLabel="Save" onCancel={onClose} /></div>
      </form>
    </Modal>
  );
}

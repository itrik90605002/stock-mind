import { CloseIcon, PlusIcon } from './icons';
import { PickedProduct, ProductPicker } from './ui';

export interface Line {
  product: PickedProduct | null;
  quantity: number;
  unitCost?: string;
}

export function LineEditor({ lines, onChange, withCost }: { lines: Line[]; onChange: (lines: Line[]) => void; withCost?: boolean }) {
  const update = (i: number, patch: Partial<Line>) => onChange(lines.map((l, j) => (j === i ? { ...l, ...patch } : l)));
  return (
    <div className="stack" style={{ gap: '.5rem' }}>
      {lines.map((line, i) => (
        <div key={i} className="row" style={{ alignItems: 'flex-start', flexWrap: 'nowrap' }}>
          <div style={{ flex: 1, minWidth: 0 }}>
            <ProductPicker value={line.product} onChange={p => update(i, { product: p, unitCost: p && withCost ? String(p.unitCost) : line.unitCost })} />
          </div>
          <input type="number" min={1} aria-label="Quantity" style={{ width: 96 }} value={line.quantity}
                 onChange={e => update(i, { quantity: Number(e.target.value) })} required />
          {withCost && <input type="number" min={0} step="0.01" aria-label="Unit cost" placeholder="Unit cost" style={{ width: 110 }}
                              value={line.unitCost ?? ''} onChange={e => update(i, { unitCost: e.target.value })} />}
          <button type="button" className="icon-btn" aria-label="Remove line" disabled={lines.length === 1}
                  onClick={() => onChange(lines.filter((_, j) => j !== i))}><CloseIcon size={16} /></button>
        </div>
      ))}
      <div><button type="button" className="btn btn-ghost btn-sm" onClick={() => onChange([...lines, { product: null, quantity: 1 }])}>
        <PlusIcon size={14} />Add line</button></div>
    </div>
  );
}

export function validateLines(lines: Line[]): string | null {
  if (lines.some(l => !l.product)) return 'Choose a product on every line.';
  if (lines.some(l => !(l.quantity > 0))) return 'Every quantity must be at least 1.';
  const ids = lines.map(l => l.product!.id);
  if (new Set(ids).size !== ids.length) return 'Each product may appear only once.';
  return null;
}

import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { transferApi } from '../api/endpoints';
import type { TransferStatus } from '../api/types';
import { useAction, useApi, useIdempotencyKey } from '../api/useApi';
import { useAuth } from '../auth/AuthContext';
import { PlusIcon } from '../components/icons';
import { Line, LineEditor, validateLines } from '../components/LineEditor';
import {
  Async, Badge, Card, Empty, Field, FormActions, formatDateTime, Modal, num, PageHeader, Pagination, preventDefault,
  WarehouseSelect,
} from '../components/ui';

const TABS: { label: string; status: TransferStatus | '' }[] = [
  { label: 'All', status: '' }, { label: 'Draft', status: 'DRAFT' }, { label: 'In transit', status: 'DISPATCHED' },
  { label: 'Received', status: 'RECEIVED' }, { label: 'Cancelled', status: 'CANCELLED' },
];

export function TransfersPage() {
  const { myWarehouses, canManage } = useAuth();
  const [status, setStatus] = useState<TransferStatus | ''>('');
  const [warehouseId, setWarehouseId] = useState<number | ''>('');
  const [page, setPage] = useState(0);
  const [params] = useSearchParams();
  const [creating, setCreating] = useState(canManage && params.get('new') === '1');
  const transfers = useApi(() => transferApi.list({ status, warehouseId: warehouseId || undefined, page }), [status, warehouseId, page]);

  return (
    <>
      <PageHeader title="Transfers" subtitle="Rebalance stock between warehouses: create a draft (reserves the stock), dispatch it, receive it at the other end."
                  actions={canManage && <button className="btn btn-primary" onClick={() => setCreating(true)}><PlusIcon size={16} />New transfer</button>} />
      <div className="tabs" role="tablist">
        {TABS.map(t => <button key={t.label} role="tab" aria-selected={status === t.status} className={`tab ${status === t.status ? 'active' : ''}`}
                               onClick={() => { setStatus(t.status); setPage(0); }}>{t.label}</button>)}
      </div>
      <div className="toolbar">
        <WarehouseSelect warehouses={myWarehouses} value={warehouseId} onChange={v => { setWarehouseId(v); setPage(0); }} allLabel="All my warehouses" />
      </div>
      <Card className="flush">
        <Async state={transfers}>{data => data.content.length === 0 ? <Empty>No transfers.</Empty> : (
          <>
            <div className="table-wrap"><table>
              <thead><tr><th>#</th><th>Route</th><th>Status</th><th className="num">Lines</th><th className="num">Units</th><th>Created</th><th>Dispatched</th><th>Received</th></tr></thead>
              <tbody>{data.content.map(t => (
                <tr key={t.id}>
                  <td><Link to={`/transfers/${t.id}`}>#{t.id}</Link></td>
                  <td className="nowrap"><strong>{t.source.code}</strong> → <strong>{t.destination.code}</strong>{t.note && <span className="sub">{t.note}</span>}</td>
                  <td><Badge value={t.status} label={t.status === 'DISPATCHED' ? 'in transit' : undefined} /></td>
                  <td className="num">{t.lineCount}</td>
                  <td className="num">{num(t.totalUnits)}</td>
                  <td className="small nowrap">{formatDateTime(t.createdAt)}<span className="sub">{t.createdBy}</span></td>
                  <td className="small nowrap">{formatDateTime(t.dispatchedAt)}</td>
                  <td className="small nowrap">{formatDateTime(t.receivedAt)}</td>
                </tr>
              ))}</tbody>
            </table></div>
            <Pagination page={data.page} totalPages={data.totalPages} total={data.totalElements} onChange={setPage} />
          </>
        )}</Async>
      </Card>
      {creating && <CreateTransfer onClose={() => setCreating(false)} />}
    </>
  );
}

function CreateTransfer({ onClose }: { onClose: () => void }) {
  const { myWarehouses, warehouses } = useAuth();
  const navigate = useNavigate();
  const [source, setSource] = useState<number | ''>(myWarehouses[0]?.id ?? '');
  const [destination, setDestination] = useState<number | ''>('');
  const [note, setNote] = useState('');
  const [lines, setLines] = useState<Line[]>([{ product: null, quantity: 1 }]);
  const action = useAction();
  const { key } = useIdempotencyKey();

  const submit = () => action.run(async () => {
    const problem = validateLines(lines) ?? (!source || !destination ? 'Choose both warehouses.' : source === destination ? 'Source and destination must differ.' : null);
    if (problem) throw new Error(problem);
    const t = await transferApi.create({
      sourceWarehouseId: Number(source), destinationWarehouseId: Number(destination), note,
      lines: lines.map(l => ({ productId: l.product!.id, quantity: l.quantity })),
    }, key);
    navigate(`/transfers/${t.id}`);
  });

  return (
    <Modal title="New transfer" onClose={onClose} wide>
      <form className="form-grid" onSubmit={preventDefault(submit)}>
        <Field label="From (source)"><WarehouseSelect warehouses={myWarehouses} value={source} onChange={setSource} required /></Field>
        <Field label="To (destination)">
          <WarehouseSelect warehouses={warehouses.filter(w => w.active && w.id !== source)} value={destination} onChange={setDestination} required />
        </Field>
        <Field label="Note" span><input value={note} onChange={e => setNote(e.target.value)} maxLength={500} placeholder="Optional" /></Field>
        <div className="span-2">
          <div className="field-label" style={{ marginBottom: '.4rem' }}>Products and quantities</div>
          <LineEditor lines={lines} onChange={setLines} />
          <p className="field-hint">Creating the transfer reserves this stock at the source; it fails if not enough is available.</p>
        </div>
        <div className="span-2"><FormActions busy={action.busy} error={action.error} submitLabel="Create draft" onCancel={onClose} /></div>
      </form>
    </Modal>
  );
}

import { FormEvent, useState } from 'react';
import { assistantApi } from '../api/endpoints';
import type { AiSqlAnswer, AnswerColumn, AssistantAnswer, QueryPlan } from '../api/types';
import { useAction, useApi } from '../api/useApi';
import { SendIcon, SparklesIcon } from '../components/icons';
import { Alert, Badge, Card, Empty, formatDate, formatDateTime, humanize, Loading, money, num, PageHeader } from '../components/ui';

const EXAMPLES = [
  'Which items will run out next week?',
  'How many DRY-1007 do we have in Pune?',
  'Show the biggest stock adjustments this month',
  'Top 5 slowest moving snacks',
  'Which purchase orders from Morning Fields are still open?',
  'What should I reorder for the Andheri dark store?',
];

const QUICK: { label: string; plan: QueryPlan }[] = [
  { label: 'Running out in 7 days', plan: { intent: 'STOCKOUT_FORECAST', horizonDays: 7 } },
  { label: 'Low stock', plan: { intent: 'LOW_STOCK' } },
  { label: 'Reorder suggestions', plan: { intent: 'REORDER_SUGGESTIONS' } },
  { label: 'Open purchase orders', plan: { intent: 'PURCHASE_ORDER_STATUS', status: 'OPEN' } },
  { label: 'Transfers in transit', plan: { intent: 'TRANSFER_STATUS', status: 'IN_TRANSIT' } },
  { label: 'Fastest movers (30 days)', plan: { intent: 'TOP_MOVERS', direction: 'FAST' } },
  { label: 'Largest adjustments (30 days)', plan: { intent: 'MOVEMENT_HISTORY', movementType: 'ADJUSTMENT', sortBy: 'LARGEST' } },
  { label: 'Stock value by category', plan: { intent: 'STOCK_VALUE' } },
];

function cell(value: unknown, column: AnswerColumn) {
  if (value === null || value === undefined || value === '') return <span className="muted">—</span>;
  switch (column.type) {
    case 'number': return num(Number(value));
    case 'decimal': return Number(value).toLocaleString(undefined, { maximumFractionDigits: 1 });
    case 'money': return money(Number(value));
    case 'date': return formatDate(String(value));
    case 'datetime': return formatDateTime(String(value));
    case 'status': return <Badge value={String(value)} />;
    default: return String(value);
  }
}

/** Matches AssistantService.QUICK_QUERY_PREFIX: history entries from quick-query buttons. */
const QUICK_PREFIX = 'Quick query: ';

const isNumeric = (c: AnswerColumn) => ['number', 'decimal', 'money'].includes(c.type);

export function AssistantPage() {
  const [question, setQuestion] = useState('');
  const [answer, setAnswer] = useState<AssistantAnswer | null>(null);
  const status = useApi(() => assistantApi.status(), []);
  const history = useApi(() => assistantApi.history(), []);
  const action = useAction();

  const ask = (text: string) => action.run(async () => {
    setQuestion(text);
    setAnswer(await assistantApi.ask(text));
    void history.reload();
    void status.reload();
  });
  const runQuick = (plan: QueryPlan) => action.run(async () => {
    setAnswer(await assistantApi.run(plan));
    void history.reload();
  });
  const submit = (e: FormEvent) => { e.preventDefault(); if (question.trim()) void ask(question.trim()); };

  return (
    <>
      <PageHeader title="Ask about your inventory"
                  subtitle="Ask in plain English. You get two answers from live data: a built-in query the AI picked, and a query the AI wrote."
                  actions={status.data && (
                    <span className="ai-status muted">
                      <span className={`dot ${status.data.available ? 'dot-ok' : 'dot-bad'}`} />
                      {status.data.available ? `AI on · ${status.data.model}` : status.data.enabled ? 'AI temporarily unavailable' : 'AI disabled'}
                    </span>
                  )} />
      <div className="assistant">
        <div className="stack">
          <Card>
            <form className="ask-box" onSubmit={submit}>
              <input value={question} onChange={e => setQuestion(e.target.value)} maxLength={300}
                     placeholder="e.g. Which items will run out next week?" aria-label="Your question" />
              <button className="btn btn-ai" disabled={action.busy || !question.trim()}><SendIcon size={16} />Ask</button>
            </form>
            <div className="chips">
              {EXAMPLES.map(e => <button key={e} className="chip" onClick={() => void ask(e)} disabled={action.busy}>{e}</button>)}
            </div>
            <div className="small muted" style={{ marginTop: '1rem' }}>Quick questions (no AI needed):</div>
            <div className="chips" style={{ marginTop: '.4rem' }}>
              {QUICK.map(q => <button key={q.label} className="chip" onClick={() => void runQuick(q.plan)} disabled={action.busy}>{q.label}</button>)}
            </div>
          </Card>

          <Alert>{action.error}</Alert>
          {action.busy && <Card><Loading label="Working on it…" /></Card>}
          {!action.busy && answer && <AnswerView answer={answer} />}
        </div>

        <Card title="Your recent questions">
          {!history.data ? <Loading /> : history.data.length === 0 ? <Empty>No questions yet.</Empty> : (
            <ul className="list history">
              {history.data.map(h => (
                <li key={h.id} onClick={() => !h.question.startsWith(QUICK_PREFIX) && void ask(h.question)}
                    title={h.question.startsWith(QUICK_PREFIX) ? undefined : 'Ask again'}>
                  <span className="primary small">{h.question}</span>
                  <span className="row small muted"><Badge value={h.outcome} /> {formatDateTime(h.createdAt)}</span>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </>
  );
}

function AnswerView({ answer }: { answer: AssistantAnswer }) {
  return (
    <>
      <Card title="System-built query" className="flush">
        {answer.outcome !== 'ANSWERED' ? <div className="answer-head"><Alert kind={answer.outcome === 'FAILED' ? 'error' : answer.outcome === 'AI_UNAVAILABLE' ? 'info' : 'warn'}>{answer.message}</Alert></div> : (
          <>
            <div className="answer-head">
              <span className="plan-chip" title="How your question was understood">{answer.usedAi && <SparklesIcon size={14} />}{answer.interpretation}</span>
              <p className="answer-summary">{answer.summary}</p>
            </div>
            <ResultTable columns={answer.columns} rows={answer.rows} />
            <div className="pagination small muted">
              {answer.truncated && 'Showing the first rows only. '}{answer.intent && `Query: ${humanize(answer.intent)}`} · {answer.durationMs} ms
            </div>
          </>
        )}
      </Card>
      {answer.aiSql && <AiSqlView result={answer.aiSql} />}
    </>
  );
}

function AiSqlView({ result }: { result: AiSqlAnswer }) {
  return (
    <Card title={<><SparklesIcon size={14} /> AI-built query</>} className="flush">
      <div className="answer-head">
        {result.sql && <details className="sql"><summary className="small">Show SQL</summary><pre>{result.sql}</pre></details>}
        {result.answered ? <p className="answer-summary">{result.summary}</p> : <Alert kind="warn">{result.message}</Alert>}
      </div>
      {result.answered && <ResultTable columns={result.columns} rows={result.rows} />}
      {result.truncated && <div className="pagination small muted">Showing the first rows only.</div>}
    </Card>
  );
}

function ResultTable({ columns, rows }: { columns: AnswerColumn[]; rows: Record<string, unknown>[] }) {
  if (rows.length === 0) return <Empty>No matching records.</Empty>;
  return (
    <div className="table-wrap"><table>
      <thead><tr>{columns.map(c => <th key={c.key} className={isNumeric(c) ? 'num' : ''}>{humanize(c.label)}</th>)}</tr></thead>
      <tbody>{rows.map((row, i) => (
        <tr key={i}>{columns.map(c => (
          <td key={c.key} className={`${isNumeric(c) ? 'num' : ''} ${c.key === 'explanation' || c.key === 'reason' ? 'small' : ''} ${c.key === 'sku' ? 'mono nowrap' : ''}`}>
            {c.key === 'late' && row[c.key] ? <Badge value="LOW" label="late" /> : cell(row[c.key], c)}
          </td>
        ))}</tr>
      ))}</tbody>
    </table></div>
  );
}

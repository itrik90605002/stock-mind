import type { TrendPoint } from '../api/types';
import { num } from './ui';

export function ActivityChart({ points }: { points: TrendPoint[] }) {
  const width = 720;
  const height = 180;
  const pad = { top: 10, right: 8, bottom: 22, left: 40 };
  const max = Math.max(1, ...points.map(p => Math.max(p.unitsIn, p.unitsOut)));
  const slot = (width - pad.left - pad.right) / Math.max(1, points.length);
  const bar = Math.max(1, slot / 2 - 1);
  const y = (v: number) => pad.top + (height - pad.top - pad.bottom) * (1 - v / max);
  const ticks = [0, max / 2, max];

  return (
    <div>
      <svg className="chart" viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" role="img"
           aria-label="Units in and out per day over the last 30 days">
        {ticks.map(t => (
          <g key={t}>
            <line className="axis" x1={pad.left} x2={width - pad.right} y1={y(t)} y2={y(t)} />
            <text x={pad.left - 6} y={y(t) + 3} textAnchor="end">{num(Math.round(t))}</text>
          </g>
        ))}
        {points.map((p, i) => {
          const x = pad.left + i * slot;
          const label = new Date(`${p.day}T00:00:00`).toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
          return (
            <g key={p.day}>
              <title>{`${label}: ${num(p.unitsIn)} in, ${num(p.unitsOut)} out`}</title>
              <rect className="bar-in" x={x} y={y(p.unitsIn)} width={bar} height={y(0) - y(p.unitsIn)} rx="1" />
              <rect className="bar-out" x={x + bar + 1} y={y(p.unitsOut)} width={bar} height={y(0) - y(p.unitsOut)} rx="1" />
              {i % 7 === 0 && <text x={x} y={height - 6}>{label}</text>}
            </g>
          );
        })}
      </svg>
      <div className="legend"><span><i style={{ background: 'var(--ok)' }} />Units in</span><span><i style={{ background: 'var(--primary)' }} />Units out</span></div>
    </div>
  );
}

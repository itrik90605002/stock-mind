import { FormEvent, useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { BoxesIcon } from '../components/icons';
import { Alert, Field } from '../components/ui';

const DEMO_ACCOUNTS = [
  { label: 'Admin', email: 'admin@warehouse.local', password: 'Admin@12345' },
  { label: 'Manager', email: 'manager@warehouse.local', password: 'Manager@123' },
  { label: 'Clerk', email: 'clerk@warehouse.local', password: 'Clerk@1234' },
];

export function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to="/" replace />;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email, password);
      navigate((location.state as { from?: string } | null)?.from ?? '/', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="login">
      <div className="login-art">
        <div className="brand"><span className="brand-mark"><BoxesIcon size={16} /></span>StockPilot</div>
        <div>
          <h1>Every unit, every warehouse, accounted for.</h1>
          <ul>
            <li>Live stock across warehouses with an audit ledger of every movement</li>
            <li>Transfers and purchase orders with safe, concurrent updates</li>
            <li>Ask questions in plain English and get reorder suggestions from real usage</li>
          </ul>
        </div>
        <p className="small" style={{ opacity: .7 }}>AI Warehouse Inventory System</p>
      </div>
      <div className="login-panel">
        <form className="login-card stack" onSubmit={submit}>
          <div>
            <h2>Sign in</h2>
            <p className="muted">Use your work account.</p>
          </div>
          <Field label="Email">
            <input type="email" autoComplete="username" value={email} onChange={e => setEmail(e.target.value)} required />
          </Field>
          <Field label="Password">
            <input type="password" autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} required />
          </Field>
          <Alert>{error}</Alert>
          <button className="btn btn-primary" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
          <div className="demo-accounts">
            <span className="muted small">Demo accounts (fill the form):</span>
            <div className="row">
              {DEMO_ACCOUNTS.map(a => (
                <button key={a.label} type="button" className="btn btn-ghost btn-sm"
                        onClick={() => { setEmail(a.email); setPassword(a.password); }}>{a.label}</button>
              ))}
            </div>
          </div>
        </form>
      </div>
    </div>
  );
}

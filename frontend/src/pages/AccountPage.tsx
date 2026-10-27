import { useState } from 'react';
import { authApi } from '../api/endpoints';
import { useAction } from '../api/useApi';
import { useAuth } from '../auth/AuthContext';
import { Alert, Badge, Card, Field, PageHeader, preventDefault } from '../components/ui';

export function AccountPage() {
  const { user } = useAuth();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const action = useAction();

  const submit = () => action.run(async () => {
    if (next !== confirm) throw new Error('The new passwords do not match.');
    await authApi.changePassword(current, next);
    setCurrent(''); setNext(''); setConfirm('');
  }, 'Password changed.');

  if (!user) return null;
  return (
    <>
      <PageHeader title="My account" />
      <div className="grid-2">
        <Card title="Profile">
          <dl className="facts">
            <div><dt>Name</dt><dd>{user.fullName}</dd></div>
            <div><dt>Email</dt><dd>{user.email}</dd></div>
            <div><dt>Role</dt><dd><Badge value={user.role} /></dd></div>
            <div><dt>Warehouses</dt><dd>{user.role === 'ADMIN' ? 'All' : user.warehouses.map(w => `${w.code} ${w.name}`).join(', ')}</dd></div>
          </dl>
        </Card>
        <Card title="Change password">
          <form className="stack" onSubmit={preventDefault(submit)}>
            <Field label="Current password"><input type="password" autoComplete="current-password" value={current} onChange={e => setCurrent(e.target.value)} required /></Field>
            <Field label="New password" hint="8+ characters with a letter and a digit">
              <input type="password" autoComplete="new-password" minLength={8} value={next} onChange={e => setNext(e.target.value)} required />
            </Field>
            <Field label="Confirm new password"><input type="password" autoComplete="new-password" value={confirm} onChange={e => setConfirm(e.target.value)} required /></Field>
            <Alert>{action.error}</Alert>
            <Alert kind="success">{action.message}</Alert>
            <div><button className="btn btn-primary" disabled={action.busy}>Change password</button></div>
          </form>
        </Card>
      </div>
    </>
  );
}

import { DependencyList, useCallback, useEffect, useRef, useState } from 'react';
import { newIdempotencyKey } from './client';

export function useApi<T>(load: () => Promise<T>, deps: DependencyList = []) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const generation = useRef(0);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(load, deps);

  const reload = useCallback(async () => {
    const current = ++generation.current;
    setLoading(true);
    setError(null);
    try {
      const result = await run();
      if (current === generation.current) setData(result);
    } catch (e) {
      if (current === generation.current) setError(e instanceof Error ? e.message : 'Something went wrong');
    } finally {
      if (current === generation.current) setLoading(false);
    }
  }, [run]);

  useEffect(() => {
    void reload();
  }, [reload]);

  return { data, error, loading, reload, setData };
}

export function useAction() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const run = useCallback(async <T,>(action: () => Promise<T>, successMessage?: string): Promise<T | undefined> => {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const result = await action();
      if (successMessage) setMessage(successMessage);
      return result;
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong');
      return undefined;
    } finally {
      setBusy(false);
    }
  }, []);

  return { run, busy, error, message, setError, setMessage };
}

/**
 * An Idempotency-Key that stays the same across retries of one form submission and is renewed
 * once the submission succeeds (call {@code renew}). A double click or a retry after a network
 * error therefore never records the movement twice.
 */
export function useIdempotencyKey() {
  const [key, setKey] = useState(newIdempotencyKey);
  const renew = useCallback(() => setKey(newIdempotencyKey()), []);
  return { key, renew };
}

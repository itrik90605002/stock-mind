import { createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { authApi, warehouseApi } from '../api/endpoints';
import { tokenStore, UNAUTHORIZED_EVENT } from '../api/client';
import type { Role, User, Warehouse } from '../api/types';

const USER_KEY = 'wms.user';

interface AuthState {
  user: User | null;
  warehouses: Warehouse[];
  myWarehouses: Warehouse[];
  login: (email: string, password: string) => Promise<User>;
  logout: () => void;
  refreshWarehouses: () => Promise<void>;
  hasRole: (...roles: Role[]) => boolean;
  canManage: boolean;
}

const AuthContext = createContext<AuthState | null>(null);

function loadUser(): User | null {
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw && tokenStore.get() ? (JSON.parse(raw) as User) : null;
  } catch {
    return null;
  }
}

function saveUser(user: User | null) {
  try {
    if (user) localStorage.setItem(USER_KEY, JSON.stringify(user));
    else localStorage.removeItem(USER_KEY);
  } catch {
    // storage unavailable (private mode): the session lasts until reload
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(loadUser);
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);

  const logout = useCallback(() => {
    tokenStore.clear();
    saveUser(null);
    setUser(null);
    setWarehouses([]);
  }, []);

  const refreshWarehouses = useCallback(async () => {
    setWarehouses(await warehouseApi.list());
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const response = await authApi.login(email, password);
    tokenStore.set(response.token);
    saveUser(response.user);
    setUser(response.user);
    return response.user;
  }, []);

  useEffect(() => {
    if (!user) return;
    authApi.me().then(fresh => { saveUser(fresh); setUser(fresh); }).catch(() => undefined);
    refreshWarehouses().catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.id]);

  useEffect(() => {
    window.addEventListener(UNAUTHORIZED_EVENT, logout);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, logout);
  }, [logout]);

  const value = useMemo<AuthState>(() => ({
    user,
    warehouses,
    myWarehouses: warehouses.filter(w => w.accessible && w.active),
    login,
    logout,
    refreshWarehouses,
    hasRole: (...roles) => !!user && roles.includes(user.role),
    canManage: !!user && (user.role === 'ADMIN' || user.role === 'MANAGER'),
  }), [user, warehouses, login, logout, refreshWarehouses]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>');
  return context;
}

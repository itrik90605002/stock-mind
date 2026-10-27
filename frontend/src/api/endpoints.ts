import { newIdempotencyKey, query, request } from './client';
import type {
  AssistantAnswer, AssistantStatus, AuthResponse, ConsistencyReport, Dashboard, ForecastRunResult, Movement, MovementType, Movers, NavCounts, Page, Product, ProductInput, PurchaseOrder, PurchaseOrderInput, PurchaseOrderStatus, QueryLog, QueryPlan, ReorderSuggestion, Role, StockRow, Supplier, Transfer, TransferStatus, User, ValuationRow, Warehouse,
} from './types';

export const authApi = {
  login: (email: string, password: string) => request<AuthResponse>('POST', '/api/auth/login', { email, password }),
  me: () => request<User>('GET', '/api/auth/me'),
  changePassword: (currentPassword: string, newPassword: string) =>
    request<void>('POST', '/api/auth/password', { currentPassword, newPassword }),
};

export const userApi = {
  list: () => request<User[]>('GET', '/api/users'),
  create: (body: { email: string; fullName: string; password: string; role: Role; warehouseIds: number[] }) =>
    request<User>('POST', '/api/users', body),
  update: (id: number, body: { fullName: string; role: Role; warehouseIds: number[] }) =>
    request<User>('PUT', `/api/users/${id}`, body),
  deactivate: (id: number, reason: string) => request<User>('POST', `/api/users/${id}/deactivate`, { reason }),
  activate: (id: number) => request<User>('POST', `/api/users/${id}/activate`),
};

export const warehouseApi = {
  list: () => request<Warehouse[]>('GET', '/api/warehouses'),
  create: (body: { code: string; name: string; address: string }) => request<Warehouse>('POST', '/api/warehouses', body),
  update: (id: number, body: { code: string; name: string; address: string }) =>
    request<Warehouse>('PUT', `/api/warehouses/${id}`, body),
  deactivate: (id: number) => request<Warehouse>('POST', `/api/warehouses/${id}/deactivate`),
  activate: (id: number) => request<Warehouse>('POST', `/api/warehouses/${id}/activate`),
};

export const supplierApi = {
  list: () => request<Supplier[]>('GET', '/api/suppliers'),
  create: (body: Omit<Supplier, 'id'>) => request<Supplier>('POST', '/api/suppliers', body),
  update: (id: number, body: Omit<Supplier, 'id'>) => request<Supplier>('PUT', `/api/suppliers/${id}`, body),
};

export const productApi = {
  search: (params: { q?: string; category?: string; supplierId?: number; includeArchived?: boolean; page?: number; size?: number }) =>
    request<Page<Product>>('GET', `/api/products${query(params)}`),
  get: (id: number) => request<Product>('GET', `/api/products/${id}`),
  categories: () => request<string[]>('GET', '/api/products/categories'),
  create: (body: ProductInput) => request<Product>('POST', '/api/products', body),
  update: (id: number, body: ProductInput) => request<Product>('PUT', `/api/products/${id}`, body),
  archive: (id: number) => request<Product>('POST', `/api/products/${id}/archive`),
  restore: (id: number) => request<Product>('POST', `/api/products/${id}/restore`),
};

export const stockApi = {
  list: (params: { warehouseId?: number; q?: string; category?: string; lowOnly?: boolean; page?: number; size?: number }) =>
    request<Page<StockRow>>('GET', `/api/stock${query(params)}`),
  forProduct: (productId: number) => request<StockRow[]>('GET', `/api/stock/product/${productId}`),
  issue: (body: { productId: number; warehouseId: number; quantity: number; reason: string }, key = newIdempotencyKey()) =>
    request<Movement>('POST', '/api/stock/issue', body, { idempotencyKey: key }),
  adjust: (body: { productId: number; warehouseId: number; quantityChange: number; reason: string }, key = newIdempotencyKey()) =>
    request<Movement>('POST', '/api/stock/adjust', body, { idempotencyKey: key }),
  settings: (body: { productId: number; warehouseId: number; reorderPoint: number; leadTimeDays: number | null }) =>
    request<StockRow>('PUT', '/api/stock/settings', body),
  consistency: (warehouseId?: number) => request<ConsistencyReport>('GET', `/api/stock/consistency${query({ warehouseId })}`),
};

export const movementApi = {
  list: (params: {
    warehouseId?: number; productId?: number; type?: MovementType | ''; referenceType?: string; referenceId?: number;
    from?: string; to?: string; page?: number; size?: number;
  }) => request<Page<Movement>>('GET', `/api/movements${query(params)}`),
  reverse: (id: number, reason: string, key = newIdempotencyKey()) =>
    request<Movement>('POST', `/api/movements/${id}/reverse`, { reason }, { idempotencyKey: key }),
};

export const transferApi = {
  list: (params: { status?: TransferStatus | ''; warehouseId?: number; page?: number; size?: number }) =>
    request<Page<Transfer>>('GET', `/api/transfers${query(params)}`),
  get: (id: number) => request<Transfer>('GET', `/api/transfers/${id}`),
  create: (body: { sourceWarehouseId: number; destinationWarehouseId: number; note: string; lines: { productId: number; quantity: number }[] },
           key = newIdempotencyKey()) => request<Transfer>('POST', '/api/transfers', body, { idempotencyKey: key }),
  dispatch: (id: number, key = newIdempotencyKey()) =>
    request<Transfer>('POST', `/api/transfers/${id}/dispatch`, undefined, { idempotencyKey: key }),
  receive: (id: number, lines: { lineId: number; receivedQuantity: number }[], key = newIdempotencyKey()) =>
    request<Transfer>('POST', `/api/transfers/${id}/receive`, { lines }, { idempotencyKey: key }),
  cancel: (id: number, reason: string) => request<Transfer>('POST', `/api/transfers/${id}/cancel`, { reason }),
};

export const purchaseOrderApi = {
  list: (params: { status?: PurchaseOrderStatus | ''; open?: boolean; warehouseId?: number; supplierId?: number; page?: number; size?: number }) =>
    request<Page<PurchaseOrder>>('GET', `/api/purchase-orders${query(params)}`),
  get: (id: number) => request<PurchaseOrder>('GET', `/api/purchase-orders/${id}`),
  create: (body: PurchaseOrderInput, key = newIdempotencyKey()) =>
    request<PurchaseOrder>('POST', '/api/purchase-orders', body, { idempotencyKey: key }),
  revise: (id: number, body: PurchaseOrderInput) => request<PurchaseOrder>('PUT', `/api/purchase-orders/${id}`, body),
  place: (id: number) => request<PurchaseOrder>('POST', `/api/purchase-orders/${id}/place`),
  receive: (id: number, lines: { lineId: number; quantity: number }[], key = newIdempotencyKey()) =>
    request<PurchaseOrder>('POST', `/api/purchase-orders/${id}/receive`, { lines }, { idempotencyKey: key }),
  cancel: (id: number, reason: string) => request<PurchaseOrder>('POST', `/api/purchase-orders/${id}/cancel`, { reason }),
  close: (id: number, reason: string) => request<PurchaseOrder>('POST', `/api/purchase-orders/${id}/close`, { reason }),
};

export const reorderApi = {
  suggestions: (warehouseId?: number) =>
    request<{ computedAt: string | null; suggestions: ReorderSuggestion[] }>('GET', `/api/reorder/suggestions${query({ warehouseId })}`),
  recompute: () => request<ForecastRunResult>('POST', '/api/reorder/recompute'),
  createOrders: (items: { productId: number; warehouseId: number; quantity: number }[], key = newIdempotencyKey()) =>
    request<{ orders: PurchaseOrder[] }>('POST', '/api/reorder/purchase-orders', { items }, { idempotencyKey: key }),
};

export const assistantApi = {
  status: () => request<AssistantStatus>('GET', '/api/assistant/status'),
  ask: (question: string) => request<AssistantAnswer>('POST', '/api/assistant/ask', { question }),
  run: (plan: QueryPlan) => request<AssistantAnswer>('POST', '/api/assistant/run', { plan }),
  history: () => request<QueryLog[]>('GET', '/api/assistant/history'),
  logs: (page = 0) => request<Page<QueryLog>>('GET', `/api/assistant/logs${query({ page })}`),
};

export const dashboardApi = {
  get: (warehouseId?: number) => request<Dashboard>('GET', `/api/dashboard${query({ warehouseId })}`),
  counts: () => request<NavCounts>('GET', '/api/dashboard/counts'),
  valuation: (warehouseId?: number) => request<ValuationRow[]>('GET', `/api/reports/valuation${query({ warehouseId })}`),
  movers: (days: number, warehouseId?: number) => request<Movers>('GET', `/api/reports/movers${query({ days, warehouseId })}`),
};

export const jobApi = {
  runDaily: () => request<{ forecast: ForecastRunResult; idempotencyRecordsPurged: number }>('POST', '/api/jobs/daily'),
};

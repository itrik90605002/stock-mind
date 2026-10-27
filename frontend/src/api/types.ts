
export type Role = 'ADMIN' | 'MANAGER' | 'CLERK';
export type MovementType = 'RECEIPT' | 'ISSUE' | 'ADJUSTMENT' | 'TRANSFER_OUT' | 'TRANSFER_IN';
export type ReferenceType = 'PURCHASE_ORDER' | 'TRANSFER' | 'MANUAL' | 'OPENING';
export type TransferStatus = 'DRAFT' | 'DISPATCHED' | 'RECEIVED' | 'CANCELLED';
export type PurchaseOrderStatus = 'DRAFT' | 'ORDERED' | 'PARTIALLY_RECEIVED' | 'RECEIVED' | 'CANCELLED';
export type QueryOutcome = 'ANSWERED' | 'REFUSED' | 'INVALID_PLAN' | 'AI_UNAVAILABLE' | 'FAILED';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface WarehouseRef { id: number; code: string; name: string }
export interface ProductRef { id: number; sku: string; name: string; unit: string }

export interface User {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  active: boolean;
  statusReason: string | null;
  warehouses: WarehouseRef[];
  createdAt: string;
}

export interface AuthResponse { token: string; expiresAt: string; user: User }

export interface Warehouse {
  id: number;
  code: string;
  name: string;
  address: string | null;
  active: boolean;
  accessible: boolean;
}

export interface Supplier {
  id: number;
  name: string;
  contactEmail: string | null;
  phone: string | null;
  leadTimeDays: number;
}

export interface Product {
  id: number;
  sku: string;
  name: string;
  category: string;
  unit: string;
  unitCost: number;
  description: string | null;
  supplierId: number | null;
  supplierName: string | null;
  archived: boolean;
}

export interface ProductInput {
  sku: string;
  name: string;
  category: string;
  unit: string;
  unitCost: number;
  description: string;
  supplierId: number | null;
}

export interface StockRow {
  id: number;
  product: ProductRef;
  category: string;
  warehouse: WarehouseRef;
  onHand: number;
  reserved: number;
  available: number;
  reorderPoint: number;
  leadTimeDays: number | null;
  low: boolean;
  unitCost: number;
  value: number;
  version: number;
}

export interface Movement {
  id: number;
  createdAt: string;
  type: MovementType;
  quantity: number;
  balanceAfter: number;
  product: ProductRef;
  warehouse: WarehouseRef;
  reason: string | null;
  referenceType: ReferenceType;
  referenceId: number | null;
  reversesId: number | null;
  reversed: boolean;
  userName: string;
}

export interface ConsistencyReport {
  checkedAt: string;
  levelsChecked: number;
  consistent: boolean;
  mismatches: {
    productId: number; sku: string; warehouseId: number; warehouseCode: string;
    recordedOnHand: number; ledgerOnHand: number; recordedReserved: number; expectedReserved: number;
  }[];
}

export interface Transfer {
  id: number;
  source: WarehouseRef;
  destination: WarehouseRef;
  status: TransferStatus;
  note: string | null;
  totalUnits: number;
  lineCount: number;
  createdBy: string;
  createdAt: string;
  dispatchedBy: string | null;
  dispatchedAt: string | null;
  receivedBy: string | null;
  receivedAt: string | null;
  cancelledAt: string | null;
  lines: { id: number; product: ProductRef; quantity: number; receivedQuantity: number | null; shortfall: number }[];
}

export interface PurchaseOrder {
  id: number;
  supplierId: number;
  supplierName: string;
  warehouse: WarehouseRef;
  status: PurchaseOrderStatus;
  expectedDate: string | null;
  note: string | null;
  totalUnits: number;
  remainingUnits: number;
  totalValue: number;
  lineCount: number;
  createdBy: string;
  createdAt: string;
  orderedAt: string | null;
  closedAt: string | null;
  lines: {
    id: number; product: ProductRef; quantity: number; receivedQuantity: number; remaining: number;
    unitCost: number; lineTotal: number;
  }[];
}

export interface PurchaseOrderInput {
  supplierId: number;
  warehouseId: number;
  expectedDate: string | null;
  note: string;
  lines: { productId: number; quantity: number; unitCost: number | null }[];
}

export interface ReorderSuggestion {
  id: number;
  product: ProductRef;
  category: string;
  warehouse: WarehouseRef;
  supplierId: number | null;
  supplierName: string | null;
  unitCost: number;
  avgDailyUsage: number;
  trendPct: number | null;
  leadTimeDays: number;
  safetyStock: number;
  available: number;
  incoming: number;
  daysOfCover: number | null;
  suggestedQty: number;
  estimatedCost: number;
  explanation: string | null;
  computedAt: string;
}

export interface ForecastRunResult {
  computedAt: string; pairsAnalysed: number; suggestions: number; durationMs: number;
}

export interface QueryPlan {
  intent: string;
  product?: string;
  category?: string;
  warehouse?: string;
  supplier?: string;
  movementType?: string;
  status?: string;
  fromDate?: string;
  toDate?: string;
  horizonDays?: number;
  direction?: 'FAST' | 'SLOW';
  sortBy?: 'RECENT' | 'LARGEST';
  limit?: number;
}

export interface AnswerColumn {
  key: string;
  label: string;
  type: 'text' | 'number' | 'decimal' | 'money' | 'date' | 'datetime' | 'status';
}

export interface AssistantAnswer {
  outcome: QueryOutcome;
  question: string;
  intent: string | null;
  interpretation: string | null;
  summary: string | null;
  columns: AnswerColumn[];
  rows: Record<string, unknown>[];
  truncated: boolean;
  message: string | null;
  usedAi: boolean;
  durationMs: number;
  aiSql: AiSqlAnswer | null;
}

export interface AiSqlAnswer {
  sql: string | null;
  answered: boolean;
  summary: string | null;
  columns: AnswerColumn[];
  rows: Record<string, unknown>[];
  truncated: boolean;
  message: string | null;
}

export interface AssistantStatus { enabled: boolean; available: boolean; model: string }

export interface QueryLog {
  id: number;
  userId: number;
  userName: string;
  question: string;
  planJson: string | null;
  outcome: QueryOutcome;
  message: string | null;
  rowCount: number | null;
  durationMs: number;
  createdAt: string;
}

export interface DashboardStats {
  activeProducts: number;
  stockUnits: number;
  stockValue: number;
  lowStockCount: number;
  openPurchaseOrders: number;
  inTransitTransfers: number;
  movementsToday: number;
  reorderSuggestions: number;
}

export interface TrendPoint { day: string; unitsIn: number; unitsOut: number }

export type TaskKind = 'PLACE_ORDER' | 'RECEIVE_DELIVERY' | 'DISPATCH_TRANSFER' | 'RECEIVE_TRANSFER';

export interface Task {
  kind: TaskKind;
  documentId: number;
  title: string;
  warehouseCode: string;
  units: number;
  dueDate: string | null;
  since: string | null;
  late: boolean;
}

export interface NavCounts { toReorder: number; purchaseOrders: number; transfers: number; late: number }

export interface TaskGroup { kind: TaskKind; count: number; late: number; items: Task[] }

export interface Dashboard {
  stats: DashboardStats;
  urgentReorders: ReorderSuggestion[];
  tasks: TaskGroup[];
  recentMovements: Movement[];
  trend: TrendPoint[];
}

export interface ValuationRow { warehouseCode: string; category: string; products: number; units: number; value: number }
export interface MoverRow {
  productId: number; sku: string; name: string; category: string; unitsOut: number; onHand: number; stockValue: number;
}
export interface Movers { days: number; fastest: MoverRow[]; slowest: MoverRow[] }

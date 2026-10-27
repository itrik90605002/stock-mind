# StockPilot: AI Warehouse Inventory System

Track stock across warehouses, buy from suppliers, move stock between warehouses, and ask questions
in plain English ("Which items will run out next week?").

**Stack:** Spring Boot 3.3 (Java 21) · PostgreSQL + Flyway · React 18 + TypeScript + Vite · Google Gemini or OpenAI.

- [project-overview.md](project-overview.md): what the app does and how it keeps stock correct
- [DEPLOYMENT.md](DEPLOYMENT.md): what you need, how to run it locally and in production

## Quick start

```bash
createdb warehouse
cd backend && mvn spring-boot:run          # http://localhost:8080
cd frontend && cp .env.example .env && npm install && npm run dev   # http://localhost:5173
```

The first start loads demo data: 4 warehouses, 10 suppliers, 120 products and 90 days of history.

## Demo accounts

| Role | Email | Password | Warehouses |
|---|---|---|---|
| Admin | admin@warehouse.local | Admin@12345 | all |
| Manager | manager@warehouse.local | Manager@123 | PUN-01, MUM-02 |
| Manager | north.manager@warehouse.local | Manager@123 | DEL-03, BLR-04 |
| Clerk | clerk@warehouse.local | Clerk@1234 | PUN-01 |
| Clerk | mumbai.clerk@, delhi.clerk@, blr.clerk@warehouse.local | Clerk@1234 | one each |

## Project layout

```
backend/    Spring Boot API, one package per feature (stock, purchasing, transfer, forecast, assistant, …)
  src/main/resources/db/migration/   Flyway schema
  src/main/resources/seed/           Demo data (JSON)
frontend/   React SPA, plain CSS with light and dark themes
render.yaml Render blueprint for the backend
```

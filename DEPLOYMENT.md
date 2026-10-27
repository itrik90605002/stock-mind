# Deployment

Three pieces: **Postgres database**, **Spring Boot backend**, **React frontend**. An AI model is optional.

## What you need

| Need | Local | Production (free tiers) |
|---|---|---|
| Database | PostgreSQL 14+ | [Neon](https://neon.tech) (or Supabase) |
| Backend | Java 21 + Maven | [Render](https://render.com), Docker, via `render.yaml` |
| Frontend | Node 20+ | [Vercel](https://vercel.com), root `frontend/` |
| AI (optional) | A Gemini key ([Google AI Studio](https://aistudio.google.com), free tier) or an OpenAI key ([platform.openai.com](https://platform.openai.com)) | same key |
| Code | — | This repo on GitHub (Render and Vercel deploy from it) |

## Run locally

```bash
createdb warehouse                         # user postgres / password postgres on localhost:5432
cd backend && mvn spring-boot:run          # http://localhost:8080 — first start seeds demo data
cd frontend && cp .env.example .env && npm install && npm run dev   # http://localhost:5173
```

Optional AI: `export AI_API_KEY=<gemini key>` before starting the backend. For OpenAI, also `export AI_PROVIDER=openai`.

## Deploy

1. **Database (Neon).** Create a project, copy the connection string, and turn it into a JDBC URL:
   `jdbc:postgresql://<host>/<db>?sslmode=require`. Flyway creates the schema on first start.
2. **Backend (Render).** New → Blueprint → pick this repo. Render reads `render.yaml`; fill in:
   - `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
   - `AI_API_KEY`: your Gemini key. For OpenAI, also change `AI_PROVIDER` to `openai`. Leave it empty to run without AI.
   - `CORS_ALLOWED_ORIGINS` — the Vercel URL from step 3 (set it after step 3, then redeploy)

   `JWT_SECRET` is generated for you. Check `https://<backend>.onrender.com/actuator/health` returns `UP`.
3. **Frontend (Vercel).** Import the repo, set **Root Directory** to `frontend`, and add
   `VITE_API_BASE_URL=https://<backend>.onrender.com` (no trailing slash). `vercel.json` handles SPA routing.
4. **Log in** with `admin@warehouse.local` / `Admin@12345`, then change the password (Account page).

## Environment variables (backend)

| Variable | Default | Notes |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/warehouse` | Add `?sslmode=require` for Neon |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | `postgres` / `postgres` | |
| `DB_POOL_SIZE` | `5` | Free databases allow few connections |
| `JWT_SECRET` | dev value | **Must** be overridden in production, ≥ 32 bytes |
| `JWT_EXPIRATION` | `PT12H` | Login lifetime |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Frontend URL(s), comma-separated |
| `AI_PROVIDER` | `gemini` | `gemini` or `openai` |
| `AI_API_KEY` | empty | The provider's key. Empty means AI is off. |
| `AI_CHAT_MODEL` | provider default | `gemini-2.5-flash` or `gpt-4o-mini` unless set |
| `AI_BASE_URL` | provider default | Only needed for a proxy or another compatible API |
| `AI_ENABLED` | `true` | `false` turns AI off even with a key |
| `AI_QUESTIONS_PER_MINUTE` | `10` | Per-user limit |
| `DEMO_SEED_DATA` | `true` | Set `false` for real use (see below) |
| `PORT` | `8080` | Render sets this |

Frontend: only `VITE_API_BASE_URL`, read at **build** time — redeploy the frontend after changing it.

## Going live with real data

- Seeding runs only when the database has no users, and it loads **everything**: warehouses, products, users and 90 days of history. To start over, drop and recreate the database.
- **Gap:** with `DEMO_SEED_DATA=false` no users are created, so nobody can log in. There is no "create first admin" step yet. Until one is added, start with demo data, then add your own warehouses, products and users and deactivate the demo ones. Demo stock and history stay in the ledger.
- Change every demo password you keep.

## Good to know

- Free Render instances sleep after ~15 minutes idle; the first request then takes ~30 s (the UI says so). The daily forecast job reruns at startup to catch up.
- Without AI, the assistant shows "unavailable" and the quick-question buttons still work.
- The AI-built query needs the database user to be allowed to create a role (`warehouse_ai_reader`); Neon and a local Postgres allow this. Where it isn't allowed, the app still starts and shows only the system-built answer.
- API docs: `https://<backend>/swagger-ui.html`.

import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  // strictPort: fail loudly instead of drifting to 5174, which the backend CORS config won't allow
  server: { port: 5173, strictPort: true },
});

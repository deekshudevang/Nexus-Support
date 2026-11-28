import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

function enforceProductionEnvPlugin() {
  return {
    name: 'enforce-production-env',
    config(config, { command, mode }) {
      if (command === 'build' && mode === 'production') {
        const loaded = loadEnv(mode, process.cwd(), '');
        const apiUrl = (process.env.VITE_API_URL || loaded.VITE_API_URL || '').trim();
        const wsUrl = (process.env.VITE_WS_URL || loaded.VITE_WS_URL || '').trim();
        if (!apiUrl || !wsUrl) {
          throw new Error(
            'Production build aborted: VITE_API_URL and VITE_WS_URL must be explicitly set (no silent localhost fallback).'
          );
        }
      }
    },
  };
}

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss(), enforceProductionEnvPlugin()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.jsx',
  },
});

import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import fs from 'node:fs';
import path from 'node:path';
import type { Plugin } from 'vite';

declare const process: { cwd: () => string; env: Record<string, string | undefined> };
const root = process.cwd();
const apiProxyTarget = process.env.VITE_API_PROXY_TARGET || 'http://localhost:8080';
const assetRoot = path.resolve(root, '../front/assets');

const localAssetsPlugin: Plugin = {
  name: 'qynex-local-assets',
  configureServer(server) {
    server.middlewares.use('/assets', (request, response, next) => {
      const requestPath = decodeURIComponent((request.url || '/').split('?')[0]);
      const filePath = path.resolve(assetRoot, `.${requestPath}`);
      if (filePath !== assetRoot && !filePath.startsWith(`${assetRoot}${path.sep}`)) {
        next();
        return;
      }
      let stat: fs.Stats;
      try { stat = fs.statSync(filePath); } catch { next(); return; }
      if (!stat.isFile()) { next(); return; }
      const extension = path.extname(filePath).toLowerCase();
      const contentTypes: Record<string, string> = {
        '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg',
        '.webp': 'image/webp', '.svg': 'image/svg+xml', '.woff2': 'font/woff2',
      };
      response.setHeader('Content-Type', contentTypes[extension] || 'application/octet-stream');
      response.setHeader('Cache-Control', 'no-cache');
      fs.createReadStream(filePath).pipe(response);
    });
  },
};

export default defineConfig({
  root,
  plugins: [react(), localAssetsPlugin],
  publicDir: `${root}/../front/assets`,
  server: {
    port: 8093,
    strictPort: true,
    fs: { allow: [root, `${root}/../front`] },
    proxy: {
      // En Docker el backend no publica 8080 al host; el frontend legado en
      // 8092 ya expone el reverse proxy exacto que React debe reutilizar en dev.
      // El origen del navegador es 8093, pero el backend local autoriza 8092.
      // Vite mantiene el origen del navegador por defecto, por eso lo fijamos
      // solo en este proxy interno; no se desactiva CORS en la aplicación.
      '/api': { target: apiProxyTarget, changeOrigin: true },
      '/uploads': { target: apiProxyTarget, changeOrigin: true },
    },
  },
  build: {
    outDir: `${root}/dist`,
    emptyOutDir: true,
  },
});

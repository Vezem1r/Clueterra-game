import { defineConfig, type ProxyOptions } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { readFileSync } from 'fs'
import path from 'path'

const TILE_CACHE_CONTROL = 'public, max-age=2592000, immutable'
const MODULE_SCRIPT_EXTENSION = '.mjs'
const SCRIPT_EXTENSION = '.js'
const HTTP_OK = 200
const HTTP_NOT_FOUND = 404
const WEBP_CONTENT_TYPE = 'image/webp'
const SEA_LEVEL_TERRAIN_TILE = path.resolve(__dirname, 'public/maps/terrain-sea-level.webp')

const cacheTiles: ProxyOptions['configure'] = proxy => {
  proxy.on('proxyRes', response => {
    response.headers['cache-control'] = TILE_CACHE_CONTROL
  })
}

const fillMissingTerrain: ProxyOptions['configure'] = proxy => {
  proxy.on('proxyRes', (upstream, _request, response) => {
    if (upstream.statusCode === HTTP_NOT_FOUND) {
      upstream.resume()
      response.writeHead(HTTP_OK, { 'content-type': WEBP_CONTENT_TYPE, 'cache-control': TILE_CACHE_CONTROL })
      response.end(readFileSync(SEA_LEVEL_TERRAIN_TILE))
      return
    }
    response.writeHead(upstream.statusCode ?? HTTP_OK, { ...upstream.headers, 'cache-control': TILE_CACHE_CONTROL })
    upstream.pipe(response)
  })
}

export default defineConfig(({ mode, isSsrBuild }) => ({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  esbuild: {
    drop: mode === 'production' ? ['console', 'debugger'] : [],
  },
  build: {
    manifest: !isSsrBuild,
    rollupOptions: {
      output: isSsrBuild ? undefined : {
        manualChunks: {
          'vendor-react': ['react', 'react-dom', 'react-router-dom'],
          'vendor-query': ['@tanstack/react-query'],
          'vendor-motion': ['framer-motion'],
        },
        assetFileNames: asset => asset.names.some(name => name.endsWith(MODULE_SCRIPT_EXTENSION))
          ? `assets/[name]-[hash]${SCRIPT_EXTENSION}`
          : 'assets/[name]-[hash][extname]',
      },
    },
  },
  server: {
    port: 3001,
    proxy: {
      '/sitemap.xml': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        rewrite: () => '/api/project-pages/sitemap.xml',
      },
      '/s/': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        rewrite: path => `/api${path}`,
      },
      '/assets/rivers/': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        rewrite: path => path.replace(/^\/assets\/rivers\//, '/api/assets/rivers/'),
      },
      '/tiles/relief/': {
        target: 'https://gibs.earthdata.nasa.gov',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/tiles\/relief\//, '/wmts/epsg3857/best/BlueMarble_ShadedRelief_Bathymetry/default/GoogleMapsCompatible_Level8/'),
        configure: cacheTiles,
      },
      '/tiles/terrain/': {
        target: 'https://tiles.mapterhorn.com',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/tiles\/terrain\//, '/'),
        selfHandleResponse: true,
        configure: fillMissingTerrain,
      },
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
    },
  },
}))

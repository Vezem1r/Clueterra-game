FROM node:20-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM node:20-alpine AS ssr

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY --from=build /app/dist ./dist
COPY --from=build /app/dist-ssr ./dist-ssr
COPY server ./server

ENV NODE_ENV=production
ENV PORT=3000
ENV API_ORIGIN=http://api:8080
ENV SITE_ORIGIN=https://clueterra.com

USER node

EXPOSE 3000

CMD ["node", "server/ssr-server.mjs"]

FROM nginx:alpine AS static

COPY --from=build /app/dist /usr/share/nginx/html
COPY --from=build /app/dist/assets /opt/clueterra/current-assets
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY docker-entrypoint.d/40-sync-versioned-assets.sh /docker-entrypoint.d/40-sync-versioned-assets.sh

RUN chmod +x /docker-entrypoint.d/40-sync-versioned-assets.sh \
    && mkdir -p /var/cache/map-tiles /var/log/clueterra \
    && chown nginx:nginx /var/cache/map-tiles

ENV API_HOST=api
ENV API_PORT=8080
ENV MINIO_HOST=minio
ENV MINIO_PORT=9000
ENV SSR_HOST=frontend-ssr
ENV SSR_PORT=3000
ENV ASSET_RETENTION_HOURS=72

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]

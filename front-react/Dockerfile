FROM node:22-alpine AS build
WORKDIR /workspace/front-react
COPY front-react/package*.json ./
RUN npm ci
COPY front-react/index.html ./
COPY front-react/tsconfig.json ./
COPY front-react/tsconfig.app.json ./
COPY front-react/tsconfig.node.json ./
COPY front-react/vite.config.ts ./
COPY front-react/src ./src
COPY front/css /workspace/front/css
COPY front/assets/fonts /workspace/front/assets/fonts
COPY front/tienda/css /workspace/front/tienda/css
COPY front/tienda/templates /workspace/front/tienda/templates
COPY front/tienda/js/store/data/peru-ubigeo.json /workspace/front/tienda/js/store/data/peru-ubigeo.json
RUN npm run build

FROM nginx:1.27-alpine
COPY front-react/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/front-react/dist /usr/share/nginx/html
COPY front/assets /usr/share/nginx/html/assets
EXPOSE 80

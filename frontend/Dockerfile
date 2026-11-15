FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
ARG VITE_API_URL=/api
ENV VITE_API_URL=${VITE_API_URL}
ARG VITE_DEMO_PAYMENTS=false
ENV VITE_DEMO_PAYMENTS=${VITE_DEMO_PAYMENTS}
RUN npm run build

FROM nginx:alpine
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
ENV PORT=8080
ENV NGINX_ENVSUBST_FILTER=^PORT$
EXPOSE 8080

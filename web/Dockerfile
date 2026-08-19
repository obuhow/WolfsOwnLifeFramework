# Build inside Docker so the VPS needs only Docker and Compose.
FROM node:20-alpine AS builder
WORKDIR /app
COPY .npmrc .npmrc
COPY package*.json ./
RUN npm install --legacy-peer-deps --prefer-offline
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html
EXPOSE 80

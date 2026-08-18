# Build stage
FROM node:20-alpine AS builder
WORKDIR /app

# Copy npm config for better network resilience
COPY .npmrc .npmrc

COPY package*.json ./
RUN npm install --legacy-peer-deps --prefer-offline

COPY . .
RUN npm run build

# Runtime stage
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html
EXPOSE 80

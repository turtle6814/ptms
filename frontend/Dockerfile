# ---- Build Stage ----
FROM node:22-alpine AS build
WORKDIR /app

# Copy package files first (for layer caching)
COPY package.json package-lock.json ./

# Install dependencies
RUN npm ci

# Copy source and build
COPY . .
RUN npm run build

# ---- Serve Stage ----
FROM nginx:alpine

# Remove default nginx config
RUN rm /etc/nginx/conf.d/default.conf

# Copy custom nginx config
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy built assets from build stage
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]

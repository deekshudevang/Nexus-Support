# M3: Production Deployment Guide

**Milestone:** M3 — Production Integration, Device Validation & Release Engineering  
**Date:** 2026-11-01  
**Scope:** Cloud Gateway, PostgreSQL Database, Redis Cache, and Tactical Command Center Web UI  

---

## 1. System Architecture & Prerequisites

### 1.1 Compute & OS Requirements
- **Host OS**: Linux (Ubuntu 22.04 LTS / Debian 12 / Rocky Linux 9) or hardened container host.
- **Runtimes**:
  - Python 3.12+ (or 3.14)
  - Node.js 20+ (LTS) or Node.js 22
  - PostgreSQL 15+ (PostgreSQL 16 recommended)
  - Redis 7.0+ (for pub/sub caching)
  - Reverse Proxy: NGINX / Caddy with HTTP/2 and WSS support.

### 1.2 Containerized Topology (`docker-compose.yml`)
The project includes a ready-to-use production-grade container topology:
- `db`: `postgres:16-alpine` with persistent volume `postgres_data` and healthcheck.
- `redis`: `redis:7-alpine` with persistent volume `redis_data` and healthcheck.
- `backend`: FastAPI running under Uvicorn with ASGI worker processes.
- `frontend`: Static NGINX serving compiled React/Vite assets from `dist/`.

---

## 2. Environment Configuration

### 2.1 Backend Environment Variables (`.env.production`)
```ini
# Production Operating Environment
ENVIRONMENT=production
APP_ENV=production

# Database Connections (PostgreSQL 16)
DATABASE_URL=postgresql+psycopg2://rescuemesh_prod_user:CHANGE_ME_STRONG_PASSWORD@db:5432/rescuemesh_prod
DATABASE_PATH=meshlink_sync.db

# Redis Caching & Realtime PubSub
REDIS_URL=redis://redis:6379/0

# Perimeter Security & CORS Allowlist (HTTPS ONLY, no localhost or *)
CORS_ORIGINS=https://command.rescuemesh.org,https://eoc.rescuemesh.org

# Gateway Ingestion Credentials (Comma-separated strong 64-char hex strings)
GATEWAY_API_KEYS=gw_prod_9f81a7b6c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a9b8c7d6e5f4a3b2c1d0e9f8

# Operator Token & JWT Signing Secret
OPERATOR_TOKENS=op_prod_1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b
JWT_SECRET_KEY=jwt_prod_secret_f0e1d2c3b4a5968778695a4b3c2d1e0f0e1d2c3b4a5968778695a4b3c2d1e0f
REVOKED_CREDENTIALS=

# Rate Limiting
RATE_LIMIT_REQUESTS=120
RATE_LIMIT_WINDOW_SEC=60
```

> [!CAUTION]
> The backend employs strict fail-closed validation on startup (`validate_production_configuration`). If `ENVIRONMENT=production` and any development secret, wildcard CORS origin, or insecure HTTP endpoint is detected, the server immediately halts with a fatal exception.

---

## 3. Deployment Procedure

### Step 1: Database Provisioning & Migration
```bash
# 1. Start database and cache services
docker-compose up -d db redis

# 2. Execute Alembic migrations to apply latest schema
cd cloud/backend
export DATABASE_URL="postgresql+psycopg2://rescuemesh_prod_user:PASSWORD@localhost:5432/rescuemesh_prod"
alembic upgrade head
```

### Step 2: Backend ASGI Startup
```bash
# Launch FastAPI backend with 4 Uvicorn workers behind local socket
uvicorn main:app \
  --host 0.0.0.0 \
  --port 8000 \
  --workers 4 \
  --proxy-headers \
  --forwarded-allow-ips='*'
```

### Step 3: Frontend Compilation & Web Serving
```bash
cd cloud/frontend
npm ci
npm run build

# Copy dist to webroot
cp -r dist/* /var/www/command-center/
```

### Step 4: Reverse Proxy Configuration (NGINX)
```nginx
server {
    listen 443 ssl http2;
    server_name command.rescuemesh.org;

    ssl_certificate /etc/letsencrypt/live/command.rescuemesh.org/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/command.rescuemesh.org/privkey.pem;

    # Static Web UI
    location / {
        root /var/www/command-center;
        try_files $uri $uri/ /index.html;
    }

    # API Gateway
    location /api/ {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket Realtime Stream
    location /ws/ {
        proxy_pass http://127.0.0.1:8000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "Upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 86400s;
    }

    # Readiness & Liveness Probes
    location /health {
        proxy_pass http://127.0.0.1:8000/health;
    }
    location /health/ready {
        proxy_pass http://127.0.0.1:8000/health/ready;
    }
}
```

---

## 4. Verification & Health Monitoring

Verify live status via cURL:
```bash
# Liveness probe
curl -f https://command.rescuemesh.org/health
# Response: {"status":"ok","environment":"production",...}

# Readiness probe
curl -f https://command.rescuemesh.org/health/ready
# Response: {"status":"READY","database":"OK","database_engine":"postgresql","outbox_lag":0,...}
```

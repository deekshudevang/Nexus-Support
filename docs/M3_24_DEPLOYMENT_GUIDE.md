# NEXUS-SUPPORT M3.24 — PRODUCTION DEPLOYMENT & OPERATIONS GUIDE

**Milestone:** M3.24  
**Date:** 2026-11-03  
**Target Environment:** Linux x86_64 / ARM64 (Ubuntu 24.04 LTS / Debian 12 / Docker Compose v2)  
**Database:** PostgreSQL 16+ with PostGIS 3.4+

---

## 1. Architecture Overview

In production mode (`ENVIRONMENT=production`), the Nexus-Support cloud platform operates as a secure, distributed disaster response coordination system:

```
[ Field Responders (BLE / LoRa) ]
               │
               ▼
   [ Mesh Gateways (Android/Linux) ]
               │
      HTTPS / TLS (X-Gateway-Key)
               │
               ▼
    [ NGINX / Caddy (TLS 1.3 Termination & WSS Proxy) ]
               │
      Reverse Proxy (Port 8000)
               │
               ▼
      [ FastAPI Backend (Uvicorn Workers) ]
        ├── REST API Ingestion & State Machine
        ├── WebSocket Outbox Pub/Sub (/ws/events)
        └── Connection Pool (SQLAlchemy + asyncpg)
               │
               ▼
     [ PostgreSQL 16 + PostGIS 3.4 ]
       ├── Spatial Indexes (GIST)
       ├── Immutable Audit Log (incident_events)
       └── Transactional Outbox (outbox_events)
```

---

## 2. Production Environment Configuration

Create `/etc/nexus-support/production.env` (permissions `0600`, owned by service user):

```ini
# Application Mode
ENVIRONMENT=production
LOG_LEVEL=INFO

# Mandatory PostgreSQL Connection (SQLite is strictly refused via Safety Invariant 9)
DATABASE_URL=postgresql://nexus_app:SUPER_SECRET_STRONG_PASSWORD_HERE@postgres:5432/nexus_support

# Connection Pooling
DB_POOL_SIZE=25
DB_MAX_OVERFLOW=15
DB_POOL_TIMEOUT=30.0
DB_POOL_RECYCLE=1800

# Security Credentials (Must be strong 32+ character secrets)
GATEWAY_API_KEYS=gw_prod_sec_99a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4,gw_prod_sec_backup_1122334455667788
OPERATOR_TOKENS=op_prod_sec_a1b2c3d4e5f67890123456789abcdef0,op_cmd_sec_9876543210fedcba0987654321
JWT_SECRET_KEY=jwt_prod_sec_8877665544332211aabbccddeeff00112233445566778899

# CORS & WebSocket Allowed Origins
CORS_ORIGINS=https://command.nexus-support.org,https://tactical.nexus-support.org

# Rate Limiting
RATE_LIMIT_PER_MINUTE=60
```

---

## 3. Database Initialization & Alembic Migrations

1. **Start PostGIS Container:**
   ```bash
   docker run -d \
     --name nexus-postgres \
     --restart always \
     -e POSTGRES_DB=nexus_support \
     -e POSTGRES_USER=nexus_app \
     -e POSTGRES_PASSWORD=SUPER_SECRET_STRONG_PASSWORD_HERE \
     -v /var/lib/nexus/pgdata:/var/lib/postgresql/data \
     -p 5432:5432 \
     postgis/postgis:16-3.4
   ```

2. **Execute Database Migrations:**
   ```bash
   cd cloud/backend
   alembic upgrade head
   ```
   *Verified Migration Chain: `3b9a1f28e05c` (PostGIS spatial geometry columns and GIST spatial indexes).*

---

## 4. NGINX Reverse Proxy & WebSocket Configuration

`/etc/nginx/sites-available/nexus-support.conf`:
```nginx
upstream nexus_backend {
    server 127.0.0.1:8000;
    keepalive 32;
}

server {
    listen 80;
    server_name command.nexus-support.org;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name command.nexus-support.org;

    ssl_certificate /etc/letsencrypt/live/command.nexus-support.org/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/command.nexus-support.org/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    # Frontend Static Distribution
    location / {
        root /var/www/nexus-frontend/dist;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # REST API
    location /api/ {
        proxy_pass http://nexus_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket Real-Time Alert & Event Stream
    location /ws/ {
        proxy_pass http://nexus_backend;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }
}
```

---

## 5. Backup & Disaster Recovery Procedures

- **Automated Hourly PostGIS Backup:**
  ```bash
  pg_dump -U nexus_app -h localhost -F c -b -v -f /var/backups/nexus/db_$(date +%Y%m%d_%H%M%S).dump nexus_support
  ```
- **Disaster Recovery Restore:**
  ```bash
  # Terminate active connections
  docker exec -i nexus-postgres psql -U nexus_app -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'nexus_support';"
  # Restore schema and data
  docker exec -i nexus-postgres pg_restore -U nexus_app -d nexus_support --clean --if-exists /var/backups/nexus/db_latest.dump
  ```

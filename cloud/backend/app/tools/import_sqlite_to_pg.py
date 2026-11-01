#!/usr/bin/env python3
"""
SQLite to PostgreSQL / Target Database Migration Tool for Nexus Support.
Preserves historical records, handles nullable GPS coordinates, guarantees idempotency (upsert),
and validates row counts and checksums without modifying or deleting the source database.
"""
import argparse
import hashlib
import json
import logging
import os
import sqlite3
import sys
import time
from typing import Any, Dict, List, Optional, Tuple

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("rescuemesh.import")

def compute_file_checksum(filepath: str) -> str:
    """Compute SHA-256 checksum of the source database file."""
    sha256 = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            sha256.update(chunk)
    return sha256.hexdigest()

def inspect_source_schema(sqlite_conn: sqlite3.Connection) -> Dict[str, List[str]]:
    cursor = sqlite_conn.execute("SELECT name FROM sqlite_master WHERE type='table';")
    tables = [row[0] for row in cursor.fetchall() if not row[0].startswith("sqlite_")]
    schema = {}
    for t in tables:
        col_cursor = sqlite_conn.execute(f"PRAGMA table_info({t});")
        schema[t] = [row[1] for row in col_cursor.fetchall()]
    return schema

def migrate_database(
    source_sqlite_path: str,
    target_db_url: Optional[str] = None,
    dry_run: bool = False
) -> Dict[str, Any]:
    if not os.path.exists(source_sqlite_path):
        raise FileNotFoundError(f"Source SQLite database not found: {source_sqlite_path}")

    source_checksum = compute_file_checksum(source_sqlite_path)
    logger.info("Source SQLite: %s (SHA-256: %s)", source_sqlite_path, source_checksum[:16])

    # Connect read-only to source SQLite
    source_uri = f"file:{os.path.abspath(source_sqlite_path)}?mode=ro"
    source_conn = sqlite3.connect(source_uri, uri=True)
    source_conn.row_factory = sqlite3.Row

    # Import target database
    from app.core.database import SessionLocal, engine
    from app.models.sos import SosPacketModel
    from app.models.node import NodeStatusModel
    from app.models.user import UserModel
    from app.models.incident import IncidentModel
    from sqlalchemy.dialects.postgresql import insert as pg_insert
    from sqlalchemy import select

    schema_info = inspect_source_schema(source_conn)
    logger.info("Discovered source tables: %s", list(schema_info.keys()))

    report: Dict[str, Any] = {
        "source_file": source_sqlite_path,
        "source_sha256": source_checksum,
        "timestamp": int(time.time()),
        "dry_run": dry_run,
        "tables": {},
        "status": "success",
        "errors": []
    }

    session = SessionLocal() if not dry_run else None

    try:
        # 1. Migrate SOS Packets
        if "sos_packets" in schema_info:
            sos_cursor = source_conn.execute("SELECT * FROM sos_packets;")
            sos_rows = [dict(row) for row in sos_cursor.fetchall()]
            imported_sos = 0
            skipped_sos = 0

            for r in sos_rows:
                uuid = r.get("uuid")
                if not uuid:
                    skipped_sos += 1
                    continue

                lat = r.get("latitude")
                lon = r.get("longitude")
                # Normalize (0.0, 0.0) with no fix to None
                has_fix = bool(r.get("gpsFixAvailable", 1))
                if not has_fix or (lat == 0.0 and lon == 0.0):
                    lat = None
                    lon = None

                if not dry_run and session:
                    # Idempotent upsert
                    existing = session.query(SosPacketModel).filter_by(uuid=uuid).first()
                    if not existing:
                        sos_item = SosPacketModel(
                            uuid=uuid,
                            senderId=r.get("senderId", "unknown"),
                            senderName=r.get("senderName", "Unknown"),
                            emergencyType=r.get("emergencyType", "OTHER"),
                            severity=int(r.get("severity", 3)),
                            latitude=lat,
                            longitude=lon,
                            gpsFixAvailable=has_fix,
                            message=r.get("message", ""),
                            timestamp=int(r.get("timestamp", 0)),
                            status=r.get("status", "PENDING"),
                            hopCount=int(r.get("hopCount", 0))
                        )
                        session.add(sos_item)
                        imported_sos += 1
                    else:
                        existing.status = r.get("status", existing.status)
                        existing.hopCount = int(r.get("hopCount", existing.hopCount))
                        imported_sos += 1
                else:
                    imported_sos += 1

            report["tables"]["sos_packets"] = {
                "source_count": len(sos_rows),
                "imported": imported_sos,
                "skipped": skipped_sos
            }

        # 2. Migrate Node Status
        if "node_status" in schema_info:
            node_cursor = source_conn.execute("SELECT * FROM node_status;")
            node_rows = [dict(row) for row in node_cursor.fetchall()]
            imported_nodes = 0
            skipped_nodes = 0

            for r in node_rows:
                device_id = r.get("deviceId")
                if not device_id:
                    skipped_nodes += 1
                    continue

                if not dry_run and session:
                    existing = session.query(NodeStatusModel).filter_by(deviceId=device_id).first()
                    if not existing:
                        node_item = NodeStatusModel(
                            deviceId=device_id,
                            displayName=r.get("displayName", "Node"),
                            role=r.get("role", "PEER"),
                            batteryLevel=int(r.get("batteryLevel", 100)),
                            needsHelp=bool(r.get("needsHelp", False)),
                            latitude=float(r.get("latitude", 0.0)),
                            longitude=float(r.get("longitude", 0.0)),
                            timestamp=int(r.get("timestamp", 0))
                        )
                        session.add(node_item)
                    else:
                        existing.batteryLevel = int(r.get("batteryLevel", existing.batteryLevel))
                        existing.needsHelp = bool(r.get("needsHelp", existing.needsHelp))
                        existing.timestamp = int(r.get("timestamp", existing.timestamp))
                    imported_nodes += 1
                else:
                    imported_nodes += 1

            report["tables"]["node_status"] = {
                "source_count": len(node_rows),
                "imported": imported_nodes,
                "skipped": skipped_nodes
            }

        # 3. Migrate Users
        if "users" in schema_info:
            user_cursor = source_conn.execute("SELECT * FROM users;")
            user_rows = [dict(row) for row in user_cursor.fetchall()]
            imported_users = 0
            skipped_users = 0

            for r in user_rows:
                username = r.get("username")
                if not username:
                    skipped_users += 1
                    continue

                if not dry_run and session:
                    existing = session.query(UserModel).filter_by(username=username).first()
                    if not existing:
                        user_item = UserModel(
                            username=username,
                            email=r.get("email"),
                            password_hash=r.get("password_hash", ""),
                            role=r.get("role", "operator"),
                            status=r.get("status", "active"),
                            is_active=bool(r.get("is_active", True)),
                            last_login=r.get("last_login"),
                            token_version=int(r.get("token_version", 1)),
                            created_at=int(r.get("created_at", int(time.time())))
                        )
                        session.add(user_item)
                        imported_users += 1
                else:
                    imported_users += 1

            report["tables"]["users"] = {
                "source_count": len(user_rows),
                "imported": imported_users,
                "skipped": skipped_users
            }

        if not dry_run and session:
            session.commit()
            logger.info("Successfully committed all imported rows to target database.")

    except Exception as e:
        if session:
            session.rollback()
        report["status"] = "error"
        report["errors"].append(str(e))
        logger.error("Migration failed: %s", str(e))
        raise
    finally:
        source_conn.close()
        if session:
            session.close()

    # Re-verify source file was NOT altered (integrity check)
    post_checksum = compute_file_checksum(source_sqlite_path)
    if source_checksum != post_checksum:
        report["status"] = "fatal_source_modified"
        raise RuntimeError("FATAL: Source SQLite database checksum changed during migration!")

    logger.info("Migration finished: %s", json.dumps(report, indent=2))
    return report

def main():
    parser = argparse.ArgumentParser(description="Nexus Support SQLite to Target DB Import Tool")
    parser.add_argument("--source", required=True, help="Path to source SQLite database file")
    parser.add_argument("--dry-run", action="store_true", help="Validate without committing changes")
    parser.add_argument("--output-report", default="migration_report.json", help="Path to save migration report JSON")
    args = parser.parse_args()

    report = migrate_database(args.source, dry_run=args.dry_run)
    with open(args.output_report, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)
    logger.info("Report written to %s", args.output_report)

if __name__ == "__main__":
    main()

import os
import sqlite3
from contextlib import contextmanager
from typing import Generator, Optional
from sqlalchemy import create_engine, MetaData
from sqlalchemy.orm import declarative_base, sessionmaker, Session
from app.core.config import DATABASE_URL, DATABASE_PATH, ENVIRONMENT

# Deterministic Alembic constraint naming convention
convention = {
    "ix": "ix_%(column_0_label)s",
    "uq": "uq_%(table_name)s_%(column_0_name)s",
    "ck": "ck_%(table_name)s_%(constraint_name)s",
    "fk": "fk_%(table_name)s_%(column_0_name)s_%(referred_table_name)s",
    "pk": "pk_%(table_name)s"
}
metadata = MetaData(naming_convention=convention)
Base = declarative_base(metadata=metadata)

if DATABASE_URL.startswith("sqlite"):
    engine = create_engine(
        DATABASE_URL,
        connect_args={"check_same_thread": False, "timeout": 5.0}
    )
else:
    engine = create_engine(
        DATABASE_URL,
        pool_size=int(os.getenv("DB_POOL_SIZE", "20")),
        max_overflow=int(os.getenv("DB_MAX_OVERFLOW", "10")),
        pool_timeout=float(os.getenv("DB_POOL_TIMEOUT", "30.0")),
        pool_pre_ping=True,
        pool_recycle=int(os.getenv("DB_POOL_RECYCLE", "1800")),
    )
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

def get_db_session() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

# Direct SQLite connection helper for high-throughput edge and batch sync
@contextmanager
def get_raw_sqlite(db_path: Optional[str] = None):
    target = db_path or DATABASE_PATH
    conn = sqlite3.connect(target, timeout=5.0)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL;")
    conn.execute("PRAGMA busy_timeout=5000;")
    try:
        yield conn
    finally:
        conn.close()

def init_sqlite_tables(db_path: Optional[str] = None):
    target = db_path or DATABASE_PATH
    conn = sqlite3.connect(target)
    conn.execute("PRAGMA journal_mode=WAL;")
    conn.execute("PRAGMA busy_timeout=5000;")
    conn.execute("""
    CREATE TABLE IF NOT EXISTS sos_packets (
        uuid TEXT PRIMARY KEY,
        senderId TEXT NOT NULL,
        senderName TEXT NOT NULL,
        emergencyType TEXT NOT NULL,
        severity INTEGER NOT NULL,
        latitude REAL,
        longitude REAL,
        gpsFixAvailable INTEGER DEFAULT 1,
        message TEXT NOT NULL,
        timestamp INTEGER NOT NULL,
        status TEXT NOT NULL,
        hopCount INTEGER DEFAULT 0
    );
    """)
    conn.execute("CREATE INDEX IF NOT EXISTS idx_sos_packets_timestamp ON sos_packets(timestamp DESC);")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_sos_packets_severity ON sos_packets(severity);")

    # Column migration if table already exists without gpsFixAvailable
    cursor = conn.execute("PRAGMA table_info(sos_packets);")
    existing_cols = [row[1] for row in cursor.fetchall()]
    if "gpsFixAvailable" not in existing_cols and len(existing_cols) > 0:
        conn.execute("ALTER TABLE sos_packets ADD COLUMN gpsFixAvailable INTEGER DEFAULT 1;")

    conn.execute("""
    CREATE TABLE IF NOT EXISTS node_status (
        deviceId TEXT PRIMARY KEY,
        displayName TEXT NOT NULL,
        role TEXT NOT NULL,
        batteryLevel INTEGER NOT NULL,
        needsHelp BOOLEAN NOT NULL,
        latitude REAL NOT NULL,
        longitude REAL NOT NULL,
        timestamp INTEGER NOT NULL
    );
    """)
    conn.execute("CREATE INDEX IF NOT EXISTS idx_node_status_timestamp ON node_status(timestamp DESC);")

    conn.execute("""
    CREATE TABLE IF NOT EXISTS users (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        username TEXT UNIQUE NOT NULL,
        password_hash TEXT NOT NULL,
        role TEXT NOT NULL,
        is_active BOOLEAN DEFAULT 1,
        created_at INTEGER NOT NULL
    );
    """)
    conn.commit()
    conn.close()


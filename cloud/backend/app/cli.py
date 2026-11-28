#!/usr/bin/env python3
"""
Nexus Support Command Line Management Interface.
Provides operations for administrative bootstrapping and operational tasks.
Usage:
    python -m app.cli create-admin --username <admin> --password <password> --role <super_admin>
"""
import argparse
import getpass
import logging
import os
import sys
import time
from app.core.database import SessionLocal
from app.core.security import hash_password
from app.models.user import UserModel

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("rescuemesh.cli")

_ROLE_ALIASES = {
    "super_admin": "admin",
    "incident_commander": "commander",
    "observer": "viewer",
}


def create_admin(username: str, password: str, email: str = None, role: str = "admin"):
    role = _ROLE_ALIASES.get(role, role)
    if not username or len(username) < 3:
        logger.error("Username must be at least 3 characters.")
        sys.exit(1)
    if not password or len(password) < 8:
        logger.error("Password must be at least 8 characters.")
        sys.exit(1)

    session = SessionLocal()
    try:
        existing = session.query(UserModel).filter_by(username=username).first()
        if existing:
            logger.warning("User '%s' already exists. Updating credentials and elevating to '%s'.", username, role)
            existing.password_hash = hash_password(password)
            existing.role = role
            existing.is_active = True
            existing.token_version += 1
            if email:
                existing.email = email
            session.commit()
            logger.info("Successfully updated administrator account '%s'.", username)
            return

        hashed = hash_password(password)
        new_user = UserModel(
            username=username,
            email=email,
            password_hash=hashed,
            role=role,
            status="active",
            is_active=True,
            created_at=int(time.time()),
            token_version=1
        )
        session.add(new_user)
        session.commit()
        logger.info("Successfully created new administrator account '%s' with role '%s'.", username, role)
    except Exception as e:
        session.rollback()
        logger.error("Failed to create admin user: %s", str(e))
        sys.exit(1)
    finally:
        session.close()

def main():
    parser = argparse.ArgumentParser(description="Nexus Support Admin CLI")
    subparsers = parser.add_subparsers(dest="command", required=True)

    create_admin_parser = subparsers.add_parser("create-admin", help="Create or update an administrative user account")
    create_admin_parser.add_argument("--username", required=False, help="Administrator username")
    create_admin_parser.add_argument("--password", required=False, help="Administrator password (if omitted, prompts securely)")
    create_admin_parser.add_argument("--email", required=False, help="Optional administrator email")
    create_admin_parser.add_argument(
        "--role",
        default="admin",
        choices=["admin", "commander", "dispatcher", "responder", "viewer", "super_admin", "incident_commander", "observer"],
    )

    args = parser.parse_args()

    if args.command == "create-admin":
        username = args.username or input("Enter admin username: ").strip()
        password = args.password
        if not password:
            password = os.getenv("ADMIN_INITIAL_PASSWORD")
        if not password:
            password = getpass.getpass("Enter admin password: ")
        create_admin(username, password, args.email, args.role)

if __name__ == "__main__":
    main()

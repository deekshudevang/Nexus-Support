#!/usr/bin/env python3
"""Nexus-Support Tactical Mesh - Demo Environment Seeding Script.

Idempotently seeds demo entities (organization, dispatcher user, field responders, and sample incidents)
for demonstration and test purposes.
Refuses execution if ENVIRONMENT=production.
"""

import os
import sys
import time

# Ensure cloud/backend is on python path
repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
backend_dir = os.path.join(repo_root, "cloud", "backend")
if backend_dir not in sys.path:
    sys.path.insert(0, backend_dir)

from app.core.database import SessionLocal, Base, engine
from app.core.security import hash_password
from app.models.user import UserModel
from app.models.responder import ResponderModel
from app.models.incident import IncidentModel

def seed_demo_data():
    env = os.getenv("ENVIRONMENT", "development").strip().lower()
    if env == "production":
        print("ERROR: Cannot seed demo data into a PRODUCTION environment!")
        sys.exit(1)

    print(f"[*] Seeding demo environment (ENVIRONMENT={env})...")
    Base.metadata.create_all(bind=engine)
    session = SessionLocal()

    now = int(time.time())
    demo_org = "org-nexus-demo"

    try:
        # 1. Seed Dispatcher User
        dispatcher_username = "demo_dispatcher"
        user = session.query(UserModel).filter(UserModel.username == dispatcher_username).first()
        if not user:
            user = UserModel(
                username=dispatcher_username,
                email="dispatcher@nexus-demo.local",
                password_hash=hash_password("DemoPass123!"),
                role="dispatcher",
                status="active",
                organization_id=demo_org,
                is_active=True,
                created_at=now,
            )
            session.add(user)
            print(f"[+] Created dispatcher user: {dispatcher_username} (pass: DemoPass123!)")
        else:
            print(f"[=] Dispatcher user {dispatcher_username} already exists.")

        session.flush()

        # 2. Seed Responders with assigned mobile devices
        responders_data = [
            {
                "id": "resp-demo-alpha",
                "callsign": "SAR-EAGLE-1",
                "responder_type": "SAR",
                "skills": ["NAVIGATION", "FIRST_AID", "ROPES"],
                "device_id": "node-alpha-phone",
                "lat": 37.7749,
                "lon": -122.4194,
            },
            {
                "id": "resp-demo-bravo",
                "callsign": "MEDIC-HAWK-2",
                "responder_type": "MEDIC",
                "skills": ["ADVANCED_TRAUMA", "TRIAGE", "DEFIB"],
                "device_id": "node-bravo-phone",
                "lat": 37.7833,
                "lon": -122.4167,
            },
        ]

        for r_spec in responders_data:
            resp = session.query(ResponderModel).filter(ResponderModel.id == r_spec["id"]).first()
            if not resp:
                resp = ResponderModel(
                    id=r_spec["id"],
                    callsign=r_spec["callsign"],
                    organization_id=demo_org,
                    responder_type=r_spec["responder_type"],
                    skills=r_spec["skills"],
                    availability="AVAILABLE",
                    device_id=r_spec["device_id"],
                    last_latitude=r_spec["lat"],
                    last_longitude=r_spec["lon"],
                    location_accuracy_m=5.0,
                    last_location_at=now,
                    created_at=now,
                )
                session.add(resp)
                print(f"[+] Created responder: {r_spec['callsign']} ({r_spec['id']}) [Device: {r_spec['device_id']}]")
            else:
                resp.availability = "AVAILABLE"
                resp.last_latitude = r_spec["lat"]
                resp.last_longitude = r_spec["lon"]
                print(f"[=] Responder {r_spec['callsign']} updated to AVAILABLE at ({r_spec['lat']}, {r_spec['lon']}).")

        # 3. Seed Sample Incidents
        incidents_data = [
            {
                "id": "inc-demo-001",
                "title": "Missing Hiker in Sector 4",
                "description": "Solo hiker overdue since 18:00. Last seen near Ridge Trail marker 12. Urgent terrain search.",
                "category": "SEARCH",
                "priority": 1,
                "severity": 8,
                "status": "REPORTED",
                "lat": 37.7752,
                "lon": -122.4180,
            },
            {
                "id": "inc-demo-002",
                "title": "Flash Flood Warning - Creek Crossing",
                "description": "Rising flood waters at low-water crossing. 2 civilian vehicles stranded on access road.",
                "category": "FLOOD",
                "priority": 2,
                "severity": 7,
                "status": "REPORTED",
                "lat": 37.7810,
                "lon": -122.4220,
            },
        ]

        for inc_spec in incidents_data:
            inc = session.query(IncidentModel).filter(IncidentModel.id == inc_spec["id"]).first()
            if not inc:
                inc = IncidentModel(
                    id=inc_spec["id"],
                    title=inc_spec["title"],
                    description=inc_spec["description"],
                    category=inc_spec["category"],
                    priority=inc_spec["priority"],
                    severity=inc_spec["severity"],
                    status=inc_spec["status"],
                    organization_id=demo_org,
                    source="OPERATOR",
                    latitude=inc_spec["lat"],
                    longitude=inc_spec["lon"],
                    version=1,
                    created_at=now,
                    updated_at=now,
                )
                session.add(inc)
                print(f"[+] Created sample incident: {inc_spec['title']} ({inc_spec['id']})")
            else:
                print(f"[=] Incident {inc_spec['id']} already exists.")

        session.commit()
        print("\n[✓] Demo seeding completed successfully and idempotently!")
        print("    Organization: org-nexus-demo")
        print("    Dispatcher:   demo_dispatcher / DemoPass123!")
        print("    Responders:   SAR-EAGLE-1 (node-alpha-phone), MEDIC-HAWK-2 (node-bravo-phone)")
        print("    Incidents:    inc-demo-001 (Sector 4 Search), inc-demo-002 (Creek Crossing Flood)\n")
    except Exception as e:
        session.rollback()
        print(f"[-] Seeding failed: {e}")
        sys.exit(1)
    finally:
        session.close()

if __name__ == "__main__":
    seed_demo_data()

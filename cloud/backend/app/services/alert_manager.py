import asyncio
import json
import logging
from typing import Set
from fastapi import WebSocket

logger = logging.getLogger("rescuemesh.alert_manager")

class AlertConnectionManager:
    def __init__(self):
        self.active_connections: Set[WebSocket] = set()
        self._lock = asyncio.Lock()

    async def connect(self, websocket: WebSocket):
        async with self._lock:
            self.active_connections.add(websocket)

    async def disconnect(self, websocket: WebSocket):
        async with self._lock:
            self.active_connections.discard(websocket)

    async def broadcast_sos(self, packet: dict):
        dead_connections = []
        async with self._lock:
            for connection in self.active_connections:
                try:
                    await connection.send_text(json.dumps({"type": "sos_alert", "packet": packet}))
                except Exception:
                    dead_connections.append(connection)
            for dead in dead_connections:
                self.active_connections.discard(dead)

alert_manager = AlertConnectionManager()


"""
Real-time WebSocket manager for ForgeService.
Allows live updates across multiple Android clients (and web).

Clients can subscribe to specific work orders or get global events.
"""

from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from typing import Dict, Set, Any
import json
import asyncio
from datetime import datetime

router = APIRouter()

# Global connection manager
class ConnectionManager:
    def __init__(self):
        # work_order_id -> set of websockets
        self.active_work_orders: Dict[int, Set[WebSocket]] = {}
        # global listeners (dashboard, list screens)
        self.global_listeners: Set[WebSocket] = set()
        self.lock = asyncio.Lock()

    async def connect_to_work_order(self, websocket: WebSocket, work_order_id: int):
        await websocket.accept()
        async with self.lock:
            if work_order_id not in self.active_work_orders:
                self.active_work_orders[work_order_id] = set()
            self.active_work_orders[work_order_id].add(websocket)

    async def connect_global(self, websocket: WebSocket):
        await websocket.accept()
        async with self.lock:
            self.global_listeners.add(websocket)

    async def disconnect(self, websocket: WebSocket, work_order_id: int | None = None):
        async with self.lock:
            if work_order_id and work_order_id in self.active_work_orders:
                self.active_work_orders[work_order_id].discard(websocket)
                if not self.active_work_orders[work_order_id]:
                    del self.active_work_orders[work_order_id]
            self.global_listeners.discard(websocket)

    async def broadcast_to_work_order(self, work_order_id: int, message: dict):
        """Send update to everyone watching this specific work order"""
        if work_order_id not in self.active_work_orders:
            return

        dead_sockets = set()
        for ws in self.active_work_orders[work_order_id]:
            try:
                await ws.send_json(message)
            except Exception:
                dead_sockets.add(ws)

        # cleanup dead connections
        for ws in dead_sockets:
            self.active_work_orders[work_order_id].discard(ws)

    async def broadcast_global(self, message: dict):
        """Send to all global listeners (order lists, dashboards)"""
        dead = set()
        for ws in self.global_listeners:
            try:
                await ws.send_json(message)
            except Exception:
                dead.add(ws)
        for ws in dead:
            self.global_listeners.discard(ws)

    async def send_event(
        self,
        event_type: str,
        payload: dict,
        work_order_id: int | None = None,
        also_global: bool = True,
    ):
        """Convenience method used by services/routers"""
        msg = {
            "event": event_type,
            "timestamp": datetime.utcnow().isoformat(),
            "payload": payload,
        }
        if work_order_id:
            await self.broadcast_to_work_order(work_order_id, msg)
        if also_global:
            await self.broadcast_global(msg)


manager = ConnectionManager()

# For import in main.py
websocket_router = router


@router.websocket("/work-orders/{work_order_id}")
async def work_order_ws(websocket: WebSocket, work_order_id: int):
    await manager.connect_to_work_order(websocket, work_order_id)
    try:
        while True:
            # Keep connection alive, clients can also send pings if needed
            data = await websocket.receive_text()
            # Echo or handle simple client messages (e.g. "ping")
            if data == "ping":
                await websocket.send_json({"event": "pong"})
    except WebSocketDisconnect:
        await manager.disconnect(websocket, work_order_id)


@router.websocket("/global")
async def global_ws(websocket: WebSocket):
    """For screens that need updates across all orders (dashboard, orders list)"""
    await manager.connect_global(websocket)
    try:
        while True:
            data = await websocket.receive_text()
            if data == "ping":
                await websocket.send_json({"event": "pong"})
    except WebSocketDisconnect:
        await manager.disconnect(websocket)
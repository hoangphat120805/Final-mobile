from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Depends, Query, status
from sqlmodel import Session
from typing import Dict, Tuple
import uuid, json

from app.models import User, Order
from app.api.deps import get_ws_session_and_user

router = APIRouter(prefix="/ws", tags=["websocket-tracking"])


class ConnectionManager:
    def __init__(self):
        # Lưu kết nối theo cặp: {order_id: {"owner": websocket, "collector": websocket}}
        self.active_connections: Dict[str, Dict[str, WebSocket]] = {}

    async def connect(self, websocket: WebSocket, order_id: str, client_type: str):
        await websocket.accept()
        if order_id not in self.active_connections:
            self.active_connections[order_id] = {}
        self.active_connections[order_id][client_type] = websocket
        print(f"Client '{client_type}' for order '{order_id}' connected.")

    def disconnect(self, order_id: str, client_type: str):
        if order_id in self.active_connections and client_type in self.active_connections[order_id]:
            del self.active_connections[order_id][client_type]
            if not self.active_connections[order_id]:
                del self.active_connections[order_id]
        print(f"Client '{client_type}' for order '{order_id}' disconnected.")

    async def broadcast_location_to_owner(self, order_id: str, lat: float, lng: float):
        if order_id in self.active_connections:
            owner_ws = self.active_connections[order_id].get("owner")
            if owner_ws:
                try:
                    await owner_ws.send_json({"lat": lat, "lng": lng})
                except Exception as e:
                    print(f"Error sending to owner of {order_id}: {e}")


manager = ConnectionManager()


def update_collector_location_in_db(db: Session, collector_id: uuid.UUID, lat: float, lng: float):
    collector = db.get(User, collector_id)
    if collector:
        collector.current_location = f'SRID=4326;POINT({lng} {lat})'
        db.add(collector)
        db.commit()
    else:
        print(f"Collector {collector_id} not found in DB for location update.")


@router.websocket("/track/{order_id}/{client_type}")
async def websocket_tracking_endpoint(
    websocket: WebSocket,
    order_id: str,
    client_type: str,  # "owner" or "collector"
    # SỬ DỤNG DEPENDENCY MỚI
    session_and_user: Tuple[Session, User | None] = Depends(get_ws_session_and_user),
):
    db, current_user = session_and_user

    # --- AUTHENTICATION ---
    if not current_user or not db:
        # Dependency đã thất bại, đóng kết nối
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    try:
        # --- AUTHORIZATION ---
        order = db.get(Order, uuid.UUID(order_id))
        if not order:
            await websocket.close(code=status.WS_1008_POLICY_VIOLATION, reason="Order not found")
            return

        is_owner = (client_type == "owner" and order.owner_id == current_user.id)
        is_collector = (client_type == "collector" and order.collector_id == current_user.id)
        if not (is_owner or is_collector):
            await websocket.close(code=status.WS_1008_POLICY_VIOLATION, reason="Unauthorized")
            return

        # --- CONNECTION ---
        await manager.connect(websocket, order_id, client_type)

        # --- MAIN LOOP ---
        while True:
            if client_type == "collector":
                data = await websocket.receive_text()
                try:
                    location_data = json.loads(data)
                    lat, lng = location_data.get("lat"), location_data.get("lng")
                    if lat is not None and lng is not None and order.collector_id:
                        # Dùng session 'db' đã có để cập nhật
                        update_collector_location_in_db(db, order.collector_id, lat, lng)
                        await manager.broadcast_location_to_owner(order_id, lat, lng)
                except (json.JSONDecodeError, AttributeError):
                    pass
            else:
                await websocket.receive_text()
    
    except WebSocketDisconnect:
        manager.disconnect(order_id, client_type)
    
    finally:
        # Đảm bảo session luôn được đóng khi kết nối kết thúc
        if db:
            db.close()
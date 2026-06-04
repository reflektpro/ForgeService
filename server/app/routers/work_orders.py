"""
Work Orders router — the heart of ForgeService.
Includes status transitions, cost recalculation, and real-time broadcasts.
"""

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import select, func
from sqlalchemy.orm import selectinload
from sqlalchemy.ext.asyncio import AsyncSession
from typing import List, Optional

from app.db.database import get_db
from app.models.models import (
    WorkOrder, Vehicle, WorkOrderStatus, WorkOrderItem, WorkOrderPartUsage, Part
)
from app.schemas.schemas import (
    WorkOrderCreate, WorkOrderUpdate, WorkOrderResponse,
    WorkOrderDetail, WorkOrderItemCreate, WorkOrderItemResponse,
    WorkOrderPartUsageCreate, WorkOrderPartUsageResponse,
)
from app.websocket.manager import manager

router = APIRouter()


async def recalculate_totals(work_order: WorkOrder, db: AsyncSession):
    """Recalculate labor + parts cost and update total."""
    # Labor
    labor = await db.execute(
        select(func.coalesce(func.sum(WorkOrderItem.total_price), 0.0))
        .where(WorkOrderItem.work_order_id == work_order.id)
    )
    work_order.labor_cost = labor.scalar_one() or 0.0

    # Parts
    parts = await db.execute(
        select(func.coalesce(func.sum(WorkOrderPartUsage.price_at_time * WorkOrderPartUsage.quantity), 0.0))
        .where(WorkOrderPartUsage.work_order_id == work_order.id)
    )
    work_order.parts_cost = parts.scalar_one() or 0.0

    work_order.total_cost = (work_order.labor_cost + work_order.parts_cost) - work_order.discount
    if work_order.total_cost < 0:
        work_order.total_cost = 0.0


async def broadcast_work_order_update(work_order_id: int, event: str = "work_order.updated"):
    """Helper to notify all connected clients."""
    await manager.send_event(
        event_type=event,
        payload={"work_order_id": work_order_id},
        work_order_id=work_order_id,
    )


@router.get("/", response_model=List[WorkOrderResponse])
async def list_work_orders(
    db: AsyncSession = Depends(get_db),
    status: Optional[WorkOrderStatus] = None,
    limit: int = 100,
):
    stmt = select(WorkOrder).order_by(WorkOrder.created_at.desc())
    if status:
        stmt = stmt.where(WorkOrder.status == status)
    result = await db.execute(stmt.limit(limit))
    return result.scalars().all()


@router.post("/", response_model=WorkOrderResponse, status_code=201)
async def create_work_order(payload: WorkOrderCreate, db: AsyncSession = Depends(get_db)):
    # Validate vehicle exists
    vehicle = await db.get(Vehicle, payload.vehicle_id)
    if not vehicle:
        raise HTTPException(404, "Vehicle not found")

    wo = WorkOrder(**payload.model_dump())
    db.add(wo)
    await db.commit()
    await db.refresh(wo)

    # Notify everyone
    await manager.send_event("work_order.created", {"work_order_id": wo.id}, also_global=True)
    return wo


@router.get("/{work_order_id}", response_model=WorkOrderDetail)
async def get_work_order_detail(work_order_id: int, db: AsyncSession = Depends(get_db)):
    stmt = (
        select(WorkOrder)
        .options(
            selectinload(WorkOrder.vehicle).selectinload(Vehicle.client),
            selectinload(WorkOrder.items),
            selectinload(WorkOrder.parts_usages).selectinload(WorkOrderPartUsage.part),
            selectinload(WorkOrder.photos),
            selectinload(WorkOrder.payments),
        )
        .where(WorkOrder.id == work_order_id)
    )
    result = await db.execute(stmt)
    wo = result.scalar_one_or_none()
    if not wo:
        raise HTTPException(404, "Work order not found")
    return wo


@router.patch("/{work_order_id}", response_model=WorkOrderResponse)
async def update_work_order(
    work_order_id: int, payload: WorkOrderUpdate, db: AsyncSession = Depends(get_db)
):
    wo = await db.get(WorkOrder, work_order_id)
    if not wo:
        raise HTTPException(404, "Work order not found")

    old_status = wo.status

    for field, value in payload.model_dump(exclude_unset=True).items():
        setattr(wo, field, value)

    # If status changed to completed, set completed_at
    if payload.status == WorkOrderStatus.COMPLETED and not wo.completed_at:
        from datetime import datetime, timezone
        wo.completed_at = datetime.now(timezone.utc)

    await recalculate_totals(wo, db)
    await db.commit()
    await db.refresh(wo)

    # Real-time notification
    event = "work_order.status_changed" if payload.status and payload.status != old_status else "work_order.updated"
    await broadcast_work_order_update(wo.id, event)

    return wo


# ===================== ITEMS (работы) =====================

@router.post("/{work_order_id}/items", response_model=WorkOrderItemResponse, status_code=201)
async def add_work_item(
    work_order_id: int, payload: WorkOrderItemCreate, db: AsyncSession = Depends(get_db)
):
    wo = await db.get(WorkOrder, work_order_id)
    if not wo:
        raise HTTPException(404, "Work order not found")

    item = WorkOrderItem(
        work_order_id=work_order_id,
        **payload.model_dump(),
        total_price=payload.hours * payload.price_per_hour,
    )
    db.add(item)

    await recalculate_totals(wo, db)
    await db.commit()
    await db.refresh(item)

    await broadcast_work_order_update(work_order_id)
    return item


@router.delete("/items/{item_id}", status_code=204)
async def delete_work_item(item_id: int, db: AsyncSession = Depends(get_db)):
    item = await db.get(WorkOrderItem, item_id)
    if not item:
        return

    wo = await db.get(WorkOrder, item.work_order_id)
    await db.delete(item)

    if wo:
        await recalculate_totals(wo, db)
        await db.commit()
        await broadcast_work_order_update(wo.id)
    else:
        await db.commit()


# ===================== PARTS USAGE (списание запчастей) =====================

@router.post("/{work_order_id}/parts", response_model=WorkOrderPartUsageResponse, status_code=201)
async def add_part_usage(
    work_order_id: int, payload: WorkOrderPartUsageCreate, db: AsyncSession = Depends(get_db)
):
    wo = await db.get(WorkOrder, work_order_id)
    if not wo:
        raise HTTPException(404, "Work order not found")

    part = await db.get(Part, payload.part_id)
    if not part:
        raise HTTPException(404, "Part not found")

    if part.quantity < payload.quantity:
        raise HTTPException(400, f"Not enough stock. Available: {part.quantity}")

    usage = WorkOrderPartUsage(
        work_order_id=work_order_id,
        part_id=payload.part_id,
        quantity=payload.quantity,
        price_at_time=part.sell_price,
    )
    db.add(usage)

    # Decrease stock
    part.quantity -= payload.quantity

    await recalculate_totals(wo, db)
    await db.commit()
    await db.refresh(usage)

    await broadcast_work_order_update(work_order_id, "work_order.part_added")
    return usage


# ===================== STATUS TRANSITIONS (удобные кнопки) =====================

@router.post("/{work_order_id}/advance-status", response_model=WorkOrderResponse)
async def advance_status(work_order_id: int, db: AsyncSession = Depends(get_db)):
    """Convenience endpoint to move to the next logical status."""
    wo = await db.get(WorkOrder, work_order_id)
    if not wo:
        raise HTTPException(404, "Work order not found")

    transitions = {
        WorkOrderStatus.NEW: WorkOrderStatus.DIAGNOSTICS,
        WorkOrderStatus.DIAGNOSTICS: WorkOrderStatus.AWAITING_APPROVAL,
        WorkOrderStatus.AWAITING_APPROVAL: WorkOrderStatus.IN_PROGRESS,
        WorkOrderStatus.IN_PROGRESS: WorkOrderStatus.QUALITY_CONTROL,
        WorkOrderStatus.QUALITY_CONTROL: WorkOrderStatus.READY,
        WorkOrderStatus.READY: WorkOrderStatus.COMPLETED,
    }

    next_status = transitions.get(wo.status)
    if next_status:
        wo.status = next_status
        if next_status == WorkOrderStatus.COMPLETED and not wo.completed_at:
            from datetime import datetime, timezone
            wo.completed_at = datetime.now(timezone.utc)

    await recalculate_totals(wo, db)
    await db.commit()
    await db.refresh(wo)

    await broadcast_work_order_update(wo.id, "work_order.status_changed")
    return wo
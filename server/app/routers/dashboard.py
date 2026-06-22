"""
Dashboard & Analytics — the "executive" view.
"""

from fastapi import APIRouter, Depends
from sqlalchemy import select, func, or_
from sqlalchemy.ext.asyncio import AsyncSession
from datetime import datetime, timedelta, timezone

from app.db.database import get_db
from app.models.models import WorkOrder, WorkOrderStatus, Part, Bay
from app.schemas.schemas import DashboardStats

router = APIRouter()

ACTIVE_STATUSES = [
    WorkOrderStatus.NEW,
    WorkOrderStatus.DIAGNOSTICS,
    WorkOrderStatus.AWAITING_APPROVAL,
    WorkOrderStatus.IN_PROGRESS,
    WorkOrderStatus.AWAITING_PARTS,
    WorkOrderStatus.QUALITY_CONTROL,
    WorkOrderStatus.READY,
]


def _month_ago_utc() -> datetime:
    """Naive UTC datetime — safe for Postgres TIMESTAMP WITHOUT TIME ZONE columns."""
    return (datetime.now(timezone.utc) - timedelta(days=30)).replace(tzinfo=None)


@router.get("/stats", response_model=DashboardStats)
async def get_dashboard_stats(db: AsyncSession = Depends(get_db)):
    month_ago = _month_ago_utc()

    # Active orders
    active = await db.execute(
        select(func.count(WorkOrder.id)).where(WorkOrder.status.in_(ACTIVE_STATUSES))
    )
    active_orders = int(active.scalar_one() or 0)

    # Completed this month (rough)
    completed = await db.execute(
        select(func.count(WorkOrder.id)).where(
            WorkOrder.status == WorkOrderStatus.COMPLETED,
            WorkOrder.completed_at >= month_ago,
        )
    )
    today_completed = int(completed.scalar_one() or 0)

    # Revenue this month (very simplified)
    revenue = await db.execute(
        select(func.coalesce(func.sum(WorkOrder.total_cost), 0.0)).where(
            WorkOrder.completed_at.is_not(None),
            WorkOrder.completed_at >= month_ago,
        )
    )
    revenue_month = float(revenue.scalar_one() or 0.0)

    # Average check (last 30 completed or all if few)
    avg = await db.execute(
        select(func.coalesce(func.avg(WorkOrder.total_cost), 0.0)).where(
            WorkOrder.status == WorkOrderStatus.COMPLETED
        )
    )
    avg_check = float(avg.scalar_one() or 0.0)

    # Low stock
    low_stock = await db.execute(
        select(func.count(Part.id)).where(Part.quantity <= Part.min_quantity)
    )
    low_stock_parts = int(low_stock.scalar_one() or 0)

    # Bays: occupied if an active work order is assigned OR bay.current_work_order_id is set
    active_assigned_bay_ids = (
        select(WorkOrder.assigned_bay_id)
        .where(
            WorkOrder.status.in_(ACTIVE_STATUSES),
            WorkOrder.assigned_bay_id.is_not(None),
        )
        .distinct()
    )

    bays_total = await db.execute(
        select(func.count(Bay.id)).where(Bay.is_active.is_(True))
    )
    bays_total = int(bays_total.scalar_one() or 0)

    bays_occupied = await db.execute(
        select(func.count(Bay.id)).where(
            Bay.is_active.is_(True),
            or_(
                Bay.current_work_order_id.is_not(None),
                Bay.id.in_(active_assigned_bay_ids),
            ),
        )
    )
    bays_occupied = int(bays_occupied.scalar_one() or 0)

    return DashboardStats(
        active_orders=active_orders,
        today_completed=today_completed,
        revenue_month=round(revenue_month, 2),
        avg_check=round(avg_check, 2),
        low_stock_parts=low_stock_parts,
        bays_occupied=bays_occupied,
        bays_total=bays_total,
    )
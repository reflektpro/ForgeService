"""
PDF generation endpoints.
"""

from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.responses import FileResponse
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from datetime import datetime, timedelta

from app.db.database import get_db
from app.routers.work_orders import get_work_order_detail
from app.pdf.generator import generate_work_order_pdf, generate_report_pdf
from app.models.models import WorkOrder, WorkOrderStatus, Part

router = APIRouter()


@router.get("/work-order/{work_order_id}")
async def generate_work_order_pdf_endpoint(work_order_id: int, db: AsyncSession = Depends(get_db)):
    # Get full data
    wo = await get_work_order_detail(work_order_id, db)

    # Prepare simple dict for generator
    data = {
        "id": wo.id,
        "status": wo.status,
        "labor_cost": wo.labor_cost,
        "parts_cost": wo.parts_cost,
        "total_cost": wo.total_cost,
        "vehicle": {
            "make": wo.vehicle.make,
            "model": wo.vehicle.model,
            "year": wo.vehicle.year,
            "license_plate": wo.vehicle.license_plate,
        },
        "client": {
            "full_name": wo.vehicle.client.full_name,
            "phone": wo.vehicle.client.phone,
        },
        "items": [
            {"description": item.description, "total_price": item.total_price}
            for item in wo.items
        ],
    }

    pdf_path = generate_work_order_pdf(data)

    return FileResponse(
        pdf_path,
        media_type="application/pdf",
        filename=f"forge_zn_{work_order_id}.pdf"
    )


@router.get("/report")
async def generate_report_pdf_endpoint(days: int = Query(30, ge=1, le=365), db: AsyncSession = Depends(get_db)):
    now = datetime.now()
    since = now - timedelta(days=days)

    # Summary stats (similar to dashboard)
    active = await db.execute(
        select(func.count(WorkOrder.id)).where(
            WorkOrder.status.in_([
                WorkOrderStatus.NEW,
                WorkOrderStatus.DIAGNOSTICS,
                WorkOrderStatus.AWAITING_APPROVAL,
                WorkOrderStatus.IN_PROGRESS,
                WorkOrderStatus.AWAITING_PARTS,
                WorkOrderStatus.QUALITY_CONTROL,
                WorkOrderStatus.READY,
            ])
        )
    )
    active_orders = active.scalar_one() or 0

    completed = await db.execute(
        select(func.count(WorkOrder.id)).where(
            WorkOrder.status == WorkOrderStatus.COMPLETED,
            WorkOrder.completed_at >= since,
        )
    )
    orders_count = completed.scalar_one() or 0

    revenue = await db.execute(
        select(func.coalesce(func.sum(WorkOrder.total_cost), 0.0)).where(
            WorkOrder.completed_at >= since
        )
    )
    total_revenue = revenue.scalar_one() or 0.0

    avg = await db.execute(
        select(func.coalesce(func.avg(WorkOrder.total_cost), 0.0)).where(
            WorkOrder.status == WorkOrderStatus.COMPLETED
        )
    )
    avg_check = avg.scalar_one() or 0.0

    low_stock = await db.execute(
        select(func.count(Part.id)).where(Part.quantity <= Part.min_quantity)
    )
    low_stock_count = low_stock.scalar_one() or 0

    # Top services (simplified demo - in real would aggregate from items)
    top_services = [
        ("ТО-1 / Регламентное", 14),
        ("Замена колодок (перед)", 9),
        ("Диагностика ходовой", 7),
        ("Замена масла + фильтр", 6),
        ("Свечи зажигания", 4),
    ]

    # Recent closed
    recent = await db.execute(
        select(WorkOrder).where(
            WorkOrder.status == WorkOrderStatus.COMPLETED,
            WorkOrder.completed_at >= since
        ).order_by(WorkOrder.completed_at.desc()).limit(5)
    )
    recent_orders = [
        {"id": wo.id, "total": wo.total_cost}
        for wo in recent.scalars().all()
    ]

    report_data = {
        "period_days": days,
        "revenue": total_revenue,
        "orders_count": orders_count,
        "avg_check": avg_check,
        "low_stock": low_stock_count,
        "top_services": top_services,
        "recent_orders": recent_orders,
    }

    pdf_path = generate_report_pdf(report_data)

    return FileResponse(
        pdf_path,
        media_type="application/pdf",
        filename=f"forge_report_{days}d.pdf"
    )

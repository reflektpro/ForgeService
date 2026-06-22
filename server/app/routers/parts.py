"""
Parts / Warehouse router.
"""

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import select, and_
from sqlalchemy.ext.asyncio import AsyncSession
from typing import List

from app.db.database import get_db
from app.models.models import Part, WorkOrderPartUsage
from app.schemas.schemas import PartCreate, PartUpdate, PartResponse, PartWithStockWarning

router = APIRouter()


@router.get("", response_model=List[PartWithStockWarning])
async def list_parts(
    db: AsyncSession = Depends(get_db),
    q: str = "",
    low_stock_only: bool = False,
    limit: int = 200,
):
    stmt = select(Part).order_by(Part.name)
    if q:
        stmt = stmt.where(Part.name.ilike(f"%{q}%") | Part.part_number.ilike(f"%{q}%"))
    if low_stock_only:
        stmt = stmt.where(Part.quantity <= Part.min_quantity)

    result = await db.execute(stmt.limit(limit))
    parts = result.scalars().all()

    enriched = []
    for p in parts:
        item = PartWithStockWarning.model_validate(p)
        item.is_low_stock = p.quantity <= p.min_quantity
        enriched.append(item)
    return enriched


@router.post("", response_model=PartResponse, status_code=201)
async def create_part(payload: PartCreate, db: AsyncSession = Depends(get_db)):
    part = Part(**payload.model_dump())
    db.add(part)
    await db.commit()
    await db.refresh(part)
    return part


@router.patch("/{part_id}", response_model=PartResponse)
async def update_part(part_id: int, payload: PartUpdate, db: AsyncSession = Depends(get_db)):
    part = await db.get(Part, part_id)
    if not part:
        raise HTTPException(404, "Part not found")

    for field, value in payload.model_dump(exclude_unset=True).items():
        setattr(part, field, value)

    await db.commit()
    await db.refresh(part)
    return part


@router.post("/{part_id}/add-stock")
async def add_stock(part_id: int, quantity: int = Query(..., gt=0), db: AsyncSession = Depends(get_db)):
    part = await db.get(Part, part_id)
    if not part:
        raise HTTPException(404, "Part not found")

    part.quantity += quantity
    await db.commit()
    await db.refresh(part)
    return {"part_id": part.id, "new_quantity": part.quantity}


@router.post("/{part_id}/subtract-stock")
async def subtract_stock(part_id: int, quantity: int = Query(..., gt=0), db: AsyncSession = Depends(get_db)):
    part = await db.get(Part, part_id)
    if not part:
        raise HTTPException(404, "Part not found")

    if part.quantity < quantity:
        raise HTTPException(400, "Not enough stock")

    part.quantity -= quantity
    await db.commit()
    await db.refresh(part)
    return {"part_id": part.id, "new_quantity": part.quantity}

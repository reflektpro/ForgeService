"""
Service Bays router (visual management of service positions).
"""

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from typing import List

from app.db.database import get_db
from app.models.models import Bay
from app.schemas.schemas import BayBase, BayResponse

router = APIRouter()


@router.get("", response_model=List[BayResponse])
async def list_bays(db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(Bay).order_by(Bay.name))
    return result.scalars().all()


@router.post("", response_model=BayResponse, status_code=201)
async def create_bay(payload: BayBase, db: AsyncSession = Depends(get_db)):
    bay = Bay(**payload.model_dump())
    db.add(bay)
    await db.commit()
    await db.refresh(bay)
    return bay


@router.patch("/{bay_id}/assign/{work_order_id}", response_model=BayResponse)
async def assign_bay(bay_id: int, work_order_id: int, db: AsyncSession = Depends(get_db)):
    bay = await db.get(Bay, bay_id)
    if not bay:
        raise HTTPException(404, "Bay not found")
    bay.current_work_order_id = work_order_id
    await db.commit()
    await db.refresh(bay)
    return bay
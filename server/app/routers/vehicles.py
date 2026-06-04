"""
Vehicles router.
"""

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import selectinload
from sqlalchemy.ext.asyncio import AsyncSession
from typing import List

from app.db.database import get_db
from app.models.models import Vehicle, Client
from app.schemas.schemas import VehicleCreate, VehicleUpdate, VehicleResponse, VehicleWithClient

router = APIRouter()


@router.get("/", response_model=List[VehicleResponse])
async def list_vehicles(db: AsyncSession = Depends(get_db), q: str = "", limit: int = 100):
    stmt = select(Vehicle).order_by(Vehicle.license_plate)
    if q:
        stmt = stmt.where(
            Vehicle.license_plate.ilike(f"%{q}%") |
            Vehicle.make.ilike(f"%{q}%") |
            Vehicle.model.ilike(f"%{q}%") |
            Vehicle.vin.ilike(f"%{q}%")
        )
    result = await db.execute(stmt.limit(limit))
    return result.scalars().all()


@router.post("/", response_model=VehicleResponse, status_code=201)
async def create_vehicle(payload: VehicleCreate, db: AsyncSession = Depends(get_db)):
    # Ensure client exists
    client = await db.get(Client, payload.client_id)
    if not client:
        raise HTTPException(404, "Client not found")

    vehicle = Vehicle(**payload.model_dump())
    db.add(vehicle)
    await db.commit()
    await db.refresh(vehicle)
    return vehicle


@router.get("/{vehicle_id}", response_model=VehicleWithClient)
async def get_vehicle(vehicle_id: int, db: AsyncSession = Depends(get_db)):
    stmt = (
        select(Vehicle)
        .options(selectinload(Vehicle.client))
        .where(Vehicle.id == vehicle_id)
    )
    result = await db.execute(stmt)
    vehicle = result.scalar_one_or_none()
    if not vehicle:
        raise HTTPException(404, "Vehicle not found")
    return vehicle


@router.patch("/{vehicle_id}", response_model=VehicleResponse)
async def update_vehicle(vehicle_id: int, payload: VehicleUpdate, db: AsyncSession = Depends(get_db)):
    vehicle = await db.get(Vehicle, vehicle_id)
    if not vehicle:
        raise HTTPException(404, "Vehicle not found")

    for field, value in payload.model_dump(exclude_unset=True).items():
        setattr(vehicle, field, value)

    await db.commit()
    await db.refresh(vehicle)
    return vehicle
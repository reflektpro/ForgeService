"""
Appointments / предварительная запись.
"""

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from typing import List

from app.db.database import get_db
from app.models.models import Appointment
from app.schemas.schemas import AppointmentCreate, AppointmentResponse

router = APIRouter()


@router.get("/", response_model=List[AppointmentResponse])
async def list_appointments(db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(Appointment).order_by(Appointment.scheduled_at))
    return result.scalars().all()


@router.post("/", response_model=AppointmentResponse, status_code=201)
async def create_appointment(payload: AppointmentCreate, db: AsyncSession = Depends(get_db)):
    appt = Appointment(**payload.model_dump())
    db.add(appt)
    await db.commit()
    await db.refresh(appt)
    return appt
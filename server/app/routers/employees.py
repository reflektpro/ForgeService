"""
Employees router.
"""

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from typing import List

from app.db.database import get_db
from app.models.models import Employee
from app.schemas.schemas import EmployeeCreate, EmployeeResponse

router = APIRouter()


@router.get("", response_model=List[EmployeeResponse])
async def list_employees(db: AsyncSession = Depends(get_db), active_only: bool = True):
    stmt = select(Employee)
    if active_only:
        stmt = stmt.where(Employee.is_active)
    result = await db.execute(stmt.order_by(Employee.full_name))
    return result.scalars().all()


@router.post("", response_model=EmployeeResponse, status_code=201)
async def create_employee(payload: EmployeeCreate, db: AsyncSession = Depends(get_db)):
    emp = Employee(**payload.model_dump())
    db.add(emp)
    await db.commit()
    await db.refresh(emp)
    return emp
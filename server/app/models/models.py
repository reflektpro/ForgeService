"""
ForgeService Domain Models
Rich, professional data model for a real auto service CRM.
"""

from datetime import datetime
from enum import Enum as PyEnum
from typing import Optional, List

from sqlalchemy import (
    String, Integer, Float, DateTime, Boolean, Text, ForeignKey, Index
)
from sqlalchemy.orm import Mapped, mapped_column, relationship
from sqlalchemy.sql import func

from app.db.database import Base


# ===================== ENUMS =====================

class WorkOrderStatus(str, PyEnum):
    NEW = "new"
    DIAGNOSTICS = "diagnostics"
    AWAITING_APPROVAL = "awaiting_approval"
    IN_PROGRESS = "in_progress"
    AWAITING_PARTS = "awaiting_parts"
    QUALITY_CONTROL = "quality_control"
    READY = "ready"
    COMPLETED = "completed"
    CANCELLED = "cancelled"


class PhotoCategory(str, PyEnum):
    INTAKE = "intake"           # Приёмка
    DEFECT = "defect"           # Выявленные дефекты
    DURING = "during"           # В процессе ремонта
    AFTER = "after"             # После ремонта / результат
    OTHER = "other"


class EmployeeRole(str, PyEnum):
    ADMIN = "admin"
    MANAGER = "manager"
    RECEPTIONIST = "receptionist"
    MECHANIC = "mechanic"


class TransactionType(str, PyEnum):
    IN = "in"           # Приход
    OUT = "out"         # Расход (списание в ЗН)
    ADJUST = "adjust"   # Корректировка / инвентаризация


# ===================== MODELS =====================

class Client(Base):
    __tablename__ = "clients"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    full_name: Mapped[str] = mapped_column(String(150), nullable=False, index=True)
    phone: Mapped[str] = mapped_column(String(30), nullable=False, index=True)
    email: Mapped[Optional[str]] = mapped_column(String(120))
    notes: Mapped[Optional[str]] = mapped_column(Text)

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now()
    )

    # Relationships
    vehicles: Mapped[List["Vehicle"]] = relationship(
        "Vehicle", back_populates="client", cascade="all, delete-orphan"
    )


class Vehicle(Base):
    __tablename__ = "vehicles"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    client_id: Mapped[int] = mapped_column(ForeignKey("clients.id"), nullable=False)

    # Core vehicle data (very detailed for realism)
    make: Mapped[str] = mapped_column(String(60), nullable=False)          # Марка
    model: Mapped[str] = mapped_column(String(80), nullable=False)         # Модель
    year: Mapped[int] = mapped_column(Integer, nullable=False)
    vin: Mapped[Optional[str]] = mapped_column(String(30), unique=True, index=True)
    license_plate: Mapped[str] = mapped_column(String(20), nullable=False, index=True)  # Гос. номер

    engine: Mapped[Optional[str]] = mapped_column(String(80))              # Двигатель (1.6 16v и т.д.)
    transmission: Mapped[Optional[str]] = mapped_column(String(40))
    drive_type: Mapped[Optional[str]] = mapped_column(String(20))           # Передний / Полный и т.д.
    color: Mapped[Optional[str]] = mapped_column(String(40))

    current_mileage: Mapped[int] = mapped_column(Integer, default=0)        # Текущий пробег (км)

    notes: Mapped[Optional[str]] = mapped_column(Text)
    photo_path: Mapped[Optional[str]] = mapped_column(String(255))          # Главное фото авто

    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())

    # Relationships
    client: Mapped["Client"] = relationship("Client", back_populates="vehicles")
    work_orders: Mapped[List["WorkOrder"]] = relationship(
        "WorkOrder", back_populates="vehicle", cascade="all, delete-orphan"
    )
    history_records: Mapped[List["VehicleHistory"]] = relationship(
        "VehicleHistory", back_populates="vehicle", cascade="all, delete-orphan"
    )


class VehicleHistory(Base):
    """История изменений по автомобилю (пробег, собственник и т.д.)"""
    __tablename__ = "vehicle_history"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    vehicle_id: Mapped[int] = mapped_column(ForeignKey("vehicles.id"), nullable=False)

    event_type: Mapped[str] = mapped_column(String(30))   # mileage_update, owner_change, etc.
    mileage: Mapped[Optional[int]]
    description: Mapped[str] = mapped_column(Text)
    recorded_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

    vehicle: Mapped["Vehicle"] = relationship("Vehicle", back_populates="history_records")


class Employee(Base):
    __tablename__ = "employees"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    full_name: Mapped[str] = mapped_column(String(120), nullable=False)
    phone: Mapped[Optional[str]] = mapped_column(String(30))
    role: Mapped[EmployeeRole] = mapped_column(String(30), default=EmployeeRole.MECHANIC)
    hourly_rate: Mapped[Optional[float]] = mapped_column(Float)  # Ставка за час (для расчётов)

    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


class Bay(Base):
    """Сервисный бокс"""
    __tablename__ = "bays"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    name: Mapped[str] = mapped_column(String(50), nullable=False)  # "Бокс 1", "Подъёмник 2"
    description: Mapped[Optional[str]] = mapped_column(String(200))
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)

    # Relationship: current work order in this bay (simplified)
    current_work_order_id: Mapped[Optional[int]] = mapped_column(ForeignKey("work_orders.id"), nullable=True)


class WorkOrder(Base):
    __tablename__ = "work_orders"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    vehicle_id: Mapped[int] = mapped_column(ForeignKey("vehicles.id"), nullable=False)
    created_by_id: Mapped[Optional[int]] = mapped_column(ForeignKey("employees.id"))
    assigned_bay_id: Mapped[Optional[int]] = mapped_column(ForeignKey("bays.id"))

    status: Mapped[WorkOrderStatus] = mapped_column(
        String(30), default=WorkOrderStatus.NEW, index=True
    )

    # Intake information (приёмка)
    intake_mileage: Mapped[Optional[int]]
    intake_notes: Mapped[Optional[str]] = mapped_column(Text)
    intake_date: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

    # Financials
    labor_cost: Mapped[float] = mapped_column(Float, default=0.0)      # Стоимость работ
    parts_cost: Mapped[float] = mapped_column(Float, default=0.0)      # Стоимость запчастей
    total_cost: Mapped[float] = mapped_column(Float, default=0.0)      # Итого к оплате
    discount: Mapped[float] = mapped_column(Float, default=0.0)

    notes: Mapped[Optional[str]] = mapped_column(Text)                 # Общие заметки по заказу

    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())
    completed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)

    # Relationships
    vehicle: Mapped["Vehicle"] = relationship("Vehicle", back_populates="work_orders")
    items: Mapped[List["WorkOrderItem"]] = relationship(
        "WorkOrderItem", back_populates="work_order", cascade="all, delete-orphan"
    )
    parts_usages: Mapped[List["WorkOrderPartUsage"]] = relationship(
        "WorkOrderPartUsage", back_populates="work_order", cascade="all, delete-orphan"
    )
    photos: Mapped[List["Photo"]] = relationship(
        "Photo", back_populates="work_order", cascade="all, delete-orphan"
    )
    payments: Mapped[List["Payment"]] = relationship(
        "Payment", back_populates="work_order", cascade="all, delete-orphan"
    )


class WorkOrderItem(Base):
    """Конкретная работа, выполненная в рамках заказ-наряда"""
    __tablename__ = "work_order_items"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    work_order_id: Mapped[int] = mapped_column(ForeignKey("work_orders.id"), nullable=False)
    employee_id: Mapped[Optional[int]] = mapped_column(ForeignKey("employees.id"))

    description: Mapped[str] = mapped_column(String(300), nullable=False)  # "Замена передних тормозных колодок"
    hours: Mapped[float] = mapped_column(Float, default=1.0)             # Нормочасы
    price_per_hour: Mapped[float] = mapped_column(Float, default=0.0)
    total_price: Mapped[float] = mapped_column(Float, default=0.0)

    status: Mapped[str] = mapped_column(String(30), default="pending")    # pending / in_progress / done

    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

    work_order: Mapped["WorkOrder"] = relationship("WorkOrder", back_populates="items")


class Part(Base):
    """Запчасть на складе"""
    __tablename__ = "parts"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    name: Mapped[str] = mapped_column(String(150), nullable=False, index=True)
    part_number: Mapped[Optional[str]] = mapped_column(String(60), index=True)  # Артикул
    unit: Mapped[str] = mapped_column(String(20), default="шт")                # шт, л, м и т.д.

    quantity: Mapped[int] = mapped_column(Integer, default=0)
    min_quantity: Mapped[int] = mapped_column(Integer, default=2)              # Минимальный остаток

    purchase_price: Mapped[float] = mapped_column(Float, default=0.0)
    sell_price: Mapped[float] = mapped_column(Float, default=0.0)

    supplier: Mapped[Optional[str]] = mapped_column(String(120))
    notes: Mapped[Optional[str]] = mapped_column(Text)

    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())

    # Index for low stock queries
    __table_args__ = (Index("idx_parts_low_stock", "quantity", "min_quantity"),)


class WorkOrderPartUsage(Base):
    """Списание запчасти в конкретный заказ-наряд"""
    __tablename__ = "work_order_part_usages"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    work_order_id: Mapped[int] = mapped_column(ForeignKey("work_orders.id"), nullable=False)
    part_id: Mapped[int] = mapped_column(ForeignKey("parts.id"), nullable=False)

    quantity: Mapped[int] = mapped_column(Integer, default=1)
    price_at_time: Mapped[float] = mapped_column(Float)   # Цена на момент списания

    work_order: Mapped["WorkOrder"] = relationship("WorkOrder", back_populates="parts_usages")
    part: Mapped["Part"] = relationship("Part")


class Photo(Base):
    """Фотография, привязанная к заказ-наряду (с категорией)"""
    __tablename__ = "photos"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    work_order_id: Mapped[int] = mapped_column(ForeignKey("work_orders.id"), nullable=False)

    file_path: Mapped[str] = mapped_column(String(255), nullable=False)  # Относительный путь
    category: Mapped[PhotoCategory] = mapped_column(String(30), default=PhotoCategory.OTHER)
    description: Mapped[Optional[str]] = mapped_column(String(200))

    taken_by_id: Mapped[Optional[int]] = mapped_column(ForeignKey("employees.id"))
    taken_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

    work_order: Mapped["WorkOrder"] = relationship("WorkOrder", back_populates="photos")
    annotations: Mapped[List["PhotoAnnotation"]] = relationship(
        "PhotoAnnotation", back_populates="photo", cascade="all, delete-orphan"
    )


class PhotoAnnotation(Base):
    """Простые аннотации на фото (для пафоса)"""
    __tablename__ = "photo_annotations"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    photo_id: Mapped[int] = mapped_column(ForeignKey("photos.id"), nullable=False)

    annotation_type: Mapped[str] = mapped_column(String(20))  # arrow, circle, text, highlight
    x: Mapped[float] = mapped_column(Float)
    y: Mapped[float] = mapped_column(Float)
    width: Mapped[Optional[float]]
    height: Mapped[Optional[float]]
    text: Mapped[Optional[str]] = mapped_column(String(200))
    color: Mapped[str] = mapped_column(String(20), default="#FF3B30")

    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

    photo: Mapped["Photo"] = relationship("Photo", back_populates="annotations")


class Payment(Base):
    __tablename__ = "payments"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    work_order_id: Mapped[int] = mapped_column(ForeignKey("work_orders.id"), nullable=False)

    amount: Mapped[float] = mapped_column(Float, nullable=False)
    method: Mapped[str] = mapped_column(String(30), default="cash")  # cash, card, transfer
    paid_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    notes: Mapped[Optional[str]]

    work_order: Mapped["WorkOrder"] = relationship("WorkOrder", back_populates="payments")


class Appointment(Base):
    """Предварительная запись / запись на ТО"""
    __tablename__ = "appointments"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    vehicle_id: Mapped[int] = mapped_column(ForeignKey("vehicles.id"), nullable=False)
    client_id: Mapped[int] = mapped_column(ForeignKey("clients.id"), nullable=False)

    scheduled_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    duration_minutes: Mapped[int] = mapped_column(Integer, default=60)
    service_type: Mapped[str] = mapped_column(String(120))
    notes: Mapped[Optional[str]] = mapped_column(Text)

    status: Mapped[str] = mapped_column(String(30), default="scheduled")  # scheduled, confirmed, done, cancelled

    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


# ===================== INDEXES FOR PERFORMANCE =====================

Index("idx_work_orders_status", WorkOrder.status)
Index("idx_work_orders_vehicle", WorkOrder.vehicle_id)
Index("idx_photos_work_order", Photo.work_order_id)
Index("idx_parts_name", Part.name)
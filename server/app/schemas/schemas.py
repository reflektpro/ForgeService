"""
Pydantic schemas for ForgeService API (v2).
Request and Response models.
"""

from datetime import datetime
from typing import Optional, List, Literal
from pydantic import BaseModel, Field, ConfigDict

from app.models.models import WorkOrderStatus, PhotoCategory, EmployeeRole


# ===================== BASE =====================

class ORMBase(BaseModel):
    model_config = ConfigDict(from_attributes=True)


# ===================== CLIENT =====================

class ClientBase(BaseModel):
    full_name: str
    phone: str
    email: Optional[str] = None
    notes: Optional[str] = None


class ClientCreate(ClientBase):
    pass


class ClientUpdate(BaseModel):
    full_name: Optional[str] = None
    phone: Optional[str] = None
    email: Optional[str] = None
    notes: Optional[str] = None


class ClientResponse(ORMBase, ClientBase):
    id: int
    created_at: datetime
    updated_at: datetime


# ===================== VEHICLE =====================

class VehicleBase(BaseModel):
    make: str
    model: str
    year: int
    license_plate: str
    vin: Optional[str] = None
    engine: Optional[str] = None
    transmission: Optional[str] = None
    drive_type: Optional[str] = None
    color: Optional[str] = None
    current_mileage: int = 0
    notes: Optional[str] = None


class VehicleCreate(VehicleBase):
    client_id: int


class VehicleUpdate(BaseModel):
    current_mileage: Optional[int] = None
    notes: Optional[str] = None
    color: Optional[str] = None
    # allow updating main photo path from mobile
    photo_path: Optional[str] = None


class VehicleResponse(ORMBase, VehicleBase):
    id: int
    client_id: int
    photo_path: Optional[str] = None
    created_at: datetime
    updated_at: datetime


class VehicleWithClient(VehicleResponse):
    client: ClientResponse


# ===================== EMPLOYEE =====================

class EmployeeBase(BaseModel):
    full_name: str
    phone: Optional[str] = None
    role: EmployeeRole = EmployeeRole.MECHANIC
    hourly_rate: Optional[float] = None


class EmployeeCreate(EmployeeBase):
    pass


class EmployeeResponse(ORMBase, EmployeeBase):
    id: int
    is_active: bool


# ===================== BAY =====================

class BayBase(BaseModel):
    name: str
    description: Optional[str] = None


class BayResponse(ORMBase, BayBase):
    id: int
    is_active: bool
    current_work_order_id: Optional[int] = None


# ===================== WORK ORDER =====================

class WorkOrderBase(BaseModel):
    vehicle_id: int
    status: WorkOrderStatus = WorkOrderStatus.NEW
    intake_mileage: Optional[int] = None
    intake_notes: Optional[str] = None
    notes: Optional[str] = None
    assigned_bay_id: Optional[int] = None


class WorkOrderCreate(WorkOrderBase):
    created_by_id: Optional[int] = None


class WorkOrderUpdate(BaseModel):
    status: Optional[WorkOrderStatus] = None
    intake_notes: Optional[str] = None
    notes: Optional[str] = None
    assigned_bay_id: Optional[int] = None
    labor_cost: Optional[float] = None
    parts_cost: Optional[float] = None
    discount: Optional[float] = None


class WorkOrderResponse(ORMBase):
    id: int
    vehicle_id: int
    status: WorkOrderStatus
    intake_mileage: Optional[int]
    intake_notes: Optional[str]
    labor_cost: float
    parts_cost: float
    total_cost: float
    discount: float
    notes: Optional[str]
    assigned_bay_id: Optional[int]
    created_at: datetime
    updated_at: datetime
    completed_at: Optional[datetime]


class WorkOrderDetail(WorkOrderResponse):
    """Full work order with relations — heavy but very useful"""
    vehicle: VehicleWithClient
    items: List["WorkOrderItemResponse"] = []
    parts_usages: List["WorkOrderPartUsageResponse"] = []
    photos: List["PhotoResponse"] = []
    payments: List["PaymentResponse"] = []


# ===================== WORK ORDER ITEM =====================

class WorkOrderItemCreate(BaseModel):
    description: str
    hours: float = 1.0
    price_per_hour: float = 0.0
    employee_id: Optional[int] = None


class WorkOrderItemResponse(ORMBase):
    id: int
    work_order_id: int
    description: str
    hours: float
    price_per_hour: float
    total_price: float
    status: str
    employee_id: Optional[int] = None


# ===================== PARTS =====================

class PartBase(BaseModel):
    name: str
    part_number: Optional[str] = None
    unit: str = "шт"
    quantity: int = 0
    min_quantity: int = 2
    purchase_price: float = 0.0
    sell_price: float = 0.0
    supplier: Optional[str] = None
    notes: Optional[str] = None


class PartCreate(PartBase):
    pass


class PartUpdate(BaseModel):
    quantity: Optional[int] = None
    sell_price: Optional[float] = None
    min_quantity: Optional[int] = None
    notes: Optional[str] = None


class PartResponse(ORMBase, PartBase):
    id: int
    created_at: datetime
    updated_at: datetime


class PartWithStockWarning(PartResponse):
    is_low_stock: bool = False


# ===================== WORK ORDER PART USAGE =====================

class WorkOrderPartUsageCreate(BaseModel):
    part_id: int
    quantity: int = 1


class WorkOrderPartUsageResponse(ORMBase):
    id: int
    work_order_id: int
    part_id: int
    quantity: int
    price_at_time: float
    part: Optional[PartResponse] = None


# ===================== PHOTO =====================

class PhotoResponse(ORMBase):
    id: int
    work_order_id: int
    file_path: str
    category: PhotoCategory
    description: Optional[str]
    taken_at: datetime


class PhotoCreate(BaseModel):
    work_order_id: int
    category: PhotoCategory = PhotoCategory.OTHER
    description: Optional[str] = None


# ===================== ANNOTATION =====================

class PhotoAnnotationCreate(BaseModel):
    annotation_type: Literal["arrow", "circle", "text", "highlight"]
    x: float
    y: float
    width: Optional[float] = None
    height: Optional[float] = None
    text: Optional[str] = None
    color: str = "#FF3B30"


class PhotoAnnotationResponse(ORMBase, PhotoAnnotationCreate):
    id: int
    photo_id: int
    created_at: datetime


# ===================== PAYMENT =====================

class PaymentCreate(BaseModel):
    amount: float
    method: str = "cash"
    notes: Optional[str] = None


class PaymentResponse(ORMBase):
    id: int
    work_order_id: int
    amount: float
    method: str
    paid_at: datetime
    notes: Optional[str]


# ===================== APPOINTMENT =====================

class AppointmentCreate(BaseModel):
    vehicle_id: int
    client_id: int
    scheduled_at: datetime
    duration_minutes: int = 60
    service_type: str
    notes: Optional[str] = None


class AppointmentResponse(ORMBase):
    id: int
    vehicle_id: int
    client_id: int
    scheduled_at: datetime
    duration_minutes: int
    service_type: str
    status: str
    notes: Optional[str]


# ===================== DASHBOARD =====================

class DashboardStats(BaseModel):
    active_orders: int
    today_completed: int
    revenue_month: float
    avg_check: float
    low_stock_parts: int
    bays_occupied: int
    bays_total: int


# Update forward refs for nested models
WorkOrderDetail.model_rebuild()
VehicleWithClient.model_rebuild()
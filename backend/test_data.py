import asyncio
from fastapi.testclient import TestClient
from main import app
from auth import hash_password, create_business_access_token
from models import Business, Plan
from database import SessionLocal

client = TestClient(app)
db = SessionLocal()

# Create business
import uuid
unique = uuid.uuid4().hex[:8]
biz = Business(company_name='Test Co', email=f'test_{unique}@biz.com', password_hash=hash_password('pass123'))
db.add(biz); db.commit(); db.refresh(biz)
biz.balance_cents = 2000
db.commit()
print(f'Business: {biz.company_name}, balance: {biz.balance_cents}')

# Create sponsored plans
for i in range(3):
    plan = Plan(
        title=f'Plan Test {i+1}',
        description='Descripción de prueba',
        location='Ubicación Test',
        price='10€',
        city='BARCELONA',
        plan_type='AMBOS',
        duration='2h',
        category='Test',
        emoji='🏷️',
        tags='["tag1", "tag2"]',
        business_id=biz.id,
        is_sponsored=True,
        budget_cents=500,
        spent_cents=100 + i*50,
        cost_per_like_cents=10,
        is_active=i < 2,
    )
    db.add(plan)
db.commit()
print(f'Created {db.query(Plan).filter(Plan.business_id==biz.id).count()} plans')

# Test stats endpoint
token = create_business_access_token(biz.id)
resp = client.get('/api/business/stats', headers={'Authorization': f'Bearer {token}'})
print('Stats:', resp.json())

# Test plans list
resp = client.get('/api/business/plans', headers={'Authorization': f'Bearer {token}'})
plans = resp.json()
for p in plans:
    print(f'  Plan: {p["title"]}, active={p["is_active"]}, spent={p["spent_cents"]}, budget={p["budget_cents"]}, likes_rem={p["likes_remaining"]}')

db.close()
print('Test data ready')
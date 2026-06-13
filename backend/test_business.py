#!/usr/bin/env python3
"""Quick test of business API endpoints."""
import json, urllib.request

BASE = "http://localhost:8000"

def api(method, path, data=None, token=None):
    body = json.dumps(data).encode() if data else None
    req = urllib.request.Request(f"{BASE}{path}", data=body, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    resp = urllib.request.urlopen(req)
    return json.loads(resp.read())

# Login
r = api("POST", "/api/business/login", {"email": "test@rest.com", "password": "pass123"})
token = r["access_token"]
print(f"✅ Logged in as {r['business']['company_name']}")

# Top-up
r = api("POST", "/api/business/top-up", {"amount_cents": 1000}, token)
print(f"💰 Balance: {r['new_balance_cents']} cents")

# Create sponsored plan
r = api("POST", "/api/business/plans", {
    "title": "Cena romántica en el castillo",
    "description": "Cena para dos en terraza con vistas. Menú degustación + vino.",
    "location": "Castillo de la Atalaya",
    "price": "25€", "city": "VILLENA", "plan_type": "PAREJA",
    "duration": "3h", "category": "Gastronomía", "emoji": "🍷",
    "tags": ["comida", "romantico", "vistas"],
    "budget_cents": 500, "cost_per_like_cents": 10,
}, token)
print(f"✅ Plan created: {r['title']} (likes remaining: {r['likes_remaining']})")

# Stats
r = api("GET", "/api/business/stats", token=token)
print(f"📊 Stats: {r['active_plans']} active, {r['total_likes']} likes, {r['total_spent_cents']}¢ spent")

# List plans
plans = api("GET", "/api/business/plans", token=token)
print(f"📋 Plans: {len(plans)} total")
for p in plans:
    print(f"   {p['emoji']} {p['title']} — {p['likes_remaining']} likes left")

# Now simulate a like from a regular user
r2 = api("POST", "/api/register", {"username": "testuser", "email": "u@u.com", "password": "pass"})
user_token = r2["access_token"]
print(f"\n👤 User registered: {r2['user']['username']}")

# User likes the sponsored plan
plan_id = plans[0]["id"]
r = api("POST", f"/api/favorites/{plan_id}", token=user_token)
print(f"❤️ User liked plan: {r['status']}")

# Check stats after like
r = api("GET", "/api/business/stats", token=token)
print(f"📊 After 1 like: {r['total_likes']} likes, {r['total_spent_cents']}¢ spent")

print("\n✅ All business API tests passed!")

#!/bin/bash

# Configuration
BASE_URL="https://economato.servehttp.com"
USERNAME="Admin"
PASSWORD="Test123"

echo "Logging in to $BASE_URL..."

# Login and get token
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"name\": \"$USERNAME\", \"password\": \"$PASSWORD\"}")

TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.token')

if [ "$TOKEN" == "null" ] || [ -z "$TOKEN" ]; then
  echo "Login failed. Response:"
  echo $LOGIN_RESPONSE
  exit 1
fi

echo "Login successful!"

# 1. Rebuild Ledger & Sync Stock (The "Salvation" command)
echo "RECONSTRUYENDO blockchain y sincronizando stocks desde cero..."
SYNC_RESPONSE=$(curl -s -X POST "$BASE_URL/api/admin/blockchain/rebuild-all" \
  -H "Authorization: Bearer $TOKEN")

echo "Rebuild response: $SYNC_RESPONSE"

# 2. Delete All Weekly Plans
echo "Deleting all weekly plans and notifications..."
DELETE_RESPONSE=$(curl -s -X DELETE "$BASE_URL/api/weekly-plans" \
  -H "Authorization: Bearer $TOKEN" \
  -o /dev/null -w "%{http_code}")

if [ "$DELETE_RESPONSE" == "204" ]; then
  echo "All weekly plans deleted successfully (204 No Content)"
else
  echo "Error deleting weekly plans. HTTP Code: $DELETE_RESPONSE"
fi

echo "Maintenance operation completed."

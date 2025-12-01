#!/bin/bash

# Test script for Notified API
# This script demonstrates the functionality of the personalized notification system

API_BASE_URL="http://localhost:8080/api"

echo "=================================================="
echo "Notified - Personalized Notification System Test"
echo "=================================================="
echo ""

# Wait for services to be ready
echo "Waiting for services to be ready..."
sleep 5

# Test 1: Create a user preference
echo "1. Creating user preference for user123 (EMAIL, WHATSAPP, APP)..."
curl -X POST "${API_BASE_URL}/preferences" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user123",
    "email": "adityakl1509@gmail.com",
    "phoneNumber": "+919148053287",
    "preference": "Barca",
    "enabledChannels": ["EMAIL", "WHATSAPP", "APP"]
  }' | jq '.'
echo ""
echo ""

# Test 2: Get user preference
echo "2. Retrieving user preference for user123..."
curl -X GET "${API_BASE_URL}/preferences/user123" | jq '.'
echo ""
echo ""

# Test 3: Send a notification
echo "3. Sending notification to user123..."
curl -X POST "${API_BASE_URL}/notifications" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user123",
    "subject": "Welcome to Notified!",
    "message": "Thank you for using our personalized notification system. This is a test notification."
  }' | jq '.'
echo ""
echo ""

# Test 4: Get notifications for user
echo "4. Retrieving all notifications for user123..."
curl -X GET "${API_BASE_URL}/notifications/user/user123" | jq '.'
echo ""
echo ""

# Test 5: Create another user preference with different channels
echo "5. Creating user preference for user456 (EMAIL, WHATSAPP)..."
curl -X POST "${API_BASE_URL}/preferences" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user456",
    "email": "kalasapuraamogh@gmail.com",
    "phoneNumber": "+9876543210",
    "preference": "Mia",
    "enabledChannels": ["EMAIL", "WHATSAPP"]
  }' | jq '.'
echo ""
echo ""

# Test 6: Send notification to second user
echo "6. Sending notification to user456..."
curl -X POST "${API_BASE_URL}/notifications" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user456",
    "subject": "Email Only Test",
    "message": "This notification should only be sent via email."
  }' | jq '.'
echo ""
echo ""

# Test 7: Get all preferences
echo "7. Retrieving all user preferences..."
curl -X GET "${API_BASE_URL}/preferences" | jq '.'
echo ""
echo ""

# Test 8: Get all notifications
echo "8. Retrieving all notifications..."
curl -X GET "${API_BASE_URL}/notifications" | jq '.'
echo ""
echo ""

# Test 9: Update user preference
echo "9. Updating user123 preference (remove WHATSAPP, keep EMAIL, APP)..."
curl -X PUT "${API_BASE_URL}/preferences/user123" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user123@example.com",
    "phoneNumber": "+1234567890",
    "enabledChannels": ["EMAIL", "APP"]
  }' | jq '.'
echo ""
echo ""

# Test 10: Send another notification to see updated preferences
echo "10. Sending another notification to user123 with updated preferences..."
curl -X POST "${API_BASE_URL}/notifications" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user123",
    "subject": "Updated Preferences Test",
    "message": "This notification should not be sent via WHATSAPP anymore."
  }' | jq '.'
echo ""
echo ""

echo "=================================================="
echo "Test completed!"
echo "=================================================="
echo ""
echo "Check the service logs to see notification delivery attempts."
echo "To view logs:"
echo "  - Notification Service: docker logs notification-service"
echo "  - User Preference Service: docker logs user-preference-service"
echo ""

# Quick Start Guide

Get up and running with Notified in 5 minutes!

## Prerequisites

- Docker and Docker Compose installed
- Basic knowledge of REST APIs

## Steps

### 1. Clone and Start

```bash
# Clone the repository
git clone https://github.com/Amoghk04/Notified.git
cd Notified

# Start all services
docker-compose up -d --build
```

### 2. Wait for Services

Wait about 60 seconds for all services to start and register with Eureka.

Check status:
```bash
docker-compose ps
```

All services should show "Up" status.

### 3. Verify Eureka Dashboard

Open http://localhost:8761 in your browser.

You should see:
- API-GATEWAY
- USER-PREFERENCE-SERVICE
- NOTIFICATION-SERVICE

registered under "Instances currently registered with Eureka".

### 4. Run Test Script

```bash
chmod +x test-api.sh
./test-api.sh
```

This will create users, set preferences, and send notifications.

### 5. View Logs

```bash
# View notification service logs to see delivery attempts
docker-compose logs notification-service

# View user preference service logs
docker-compose logs user-preference-service
```

## Manual Testing

### Create a User Preference

```bash
curl -X POST http://localhost:8080/api/preferences \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "john_doe",
    "email": "john@example.com",
    "phoneNumber": "+1234567890",
    "enabledChannels": ["EMAIL", "APP"]
  }'
```

### Send a Notification

```bash
curl -X POST http://localhost:8080/api/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "john_doe",
    "subject": "Hello!",
    "message": "This is your first notification!"
  }'
```

### View Notifications

```bash
curl http://localhost:8080/api/notifications/user/john_doe
```

### Update Preferences

```bash
curl -X PUT http://localhost:8080/api/preferences/john_doe \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "phoneNumber": "+1234567890",
    "enabledChannels": ["SMS", "APP"]
  }'
```

## Enable Email Notifications

1. Create `.env` file:
```bash
cp .env.example .env
```

2. Edit `.env` and add your Gmail credentials:
```
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password
```

3. Restart notification service:
```bash
docker-compose restart notification-service
```

## What's Happening Behind the Scenes?

1. **Request Flow**: Your request → API Gateway (8080) → Service Discovery (Eureka) → Target Service
2. **User Preference Service**: Stores user notification preferences in MongoDB
3. **Notification Service**: 
   - Receives notification request
   - Calls User Preference Service to get preferences
   - Sends notification via enabled channels (Email/SMS/App)
   - Stores notification record in MongoDB

## Cleanup

```bash
# Stop all services
docker-compose down

# Remove volumes (deletes all data)
docker-compose down -v
```

## Next Steps

- Read the full [README.md](README.md) for detailed documentation
- Explore the API endpoints
- Try adding more users and notification preferences
- Check service logs to understand the flow
- Customize the notification channels

## Troubleshooting

**Services not starting?**
- Check Docker logs: `docker-compose logs`
- Ensure ports 8080, 8081, 8082, 8761, 27017 are available

**Eureka dashboard empty?**
- Wait 60 seconds for service registration
- Check service logs for connection errors

**Notifications not sending?**
- Verify user preference exists: `curl http://localhost:8080/api/preferences/{userId}`
- Check notification-service logs: `docker-compose logs notification-service`
- Ensure at least one channel is enabled in user preferences

**Email not working?**
- Configure email credentials in `.env` file
- For Gmail, use App Password, not regular password
- Check notification-service logs for SMTP errors

## Support

For more help, see [README.md](README.md) or open an issue on GitHub.

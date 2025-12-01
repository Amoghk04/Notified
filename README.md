# Notified
Personalized Notification System - Spring Boot Microservices

## Overview

Notified is a scalable, decoupled microservices architecture for managing and delivering personalized notifications. The system uses Spring Boot microservices with MongoDB for data persistence, Eureka for service discovery, and an API Gateway for centralized request routing.

## Architecture

The system consists of four main microservices:

1. **Eureka Server** (Port 8761) - Service Discovery
2. **API Gateway** (Port 8080) - Request Routing
3. **User Preference Service** (Port 8081) - Manages user notification preferences
4. **Notification Service** (Port 8082) - Sends notifications via email/WhatsApp/app

### Architecture Flow

```
Client → API Gateway → [User Preference Service | Notification Service]
                                ↑                         ↑
                                └─────── Eureka ──────────┘
```

## Prerequisites

### Option 1: Docker (Recommended)
- Docker and Docker Compose installed

### Option 2: Local Development
- Java 17 or higher
- Maven 3.6+
- MongoDB (running on localhost:27017)
- SMTP server credentials (optional, for email notifications)

## Getting Started

### Option 1: Using Docker Compose (Recommended)

#### 1. Clone the Repository

```bash
git clone https://github.com/Amoghk04/Notified.git
cd Notified
```

#### 2. Build the Project

```bash

## Quick Run Cheatsheet

```bash
# 1. Build all modules
mvn clean package -DskipTests

# 2. (Option A) Run everything with Docker Compose
docker compose up -d --build

# 2. (Option B) Run locally without Docker
# Start MongoDB (if not already running)
# Build all services with Maven

# Start Eureka (wait until http://localhost:8761 is reachable)
(cd eureka-server && mvn spring-boot:run)

# In new terminals (order after Eureka starts)
(cd api-gateway && mvn spring-boot:run)
(cd user-preference-service && mvn spring-boot:run)
MAIL_USERNAME=your-email@gmail.com MAIL_PASSWORD=your-app-password \
  (cd notification-service && mvn spring-boot:run)

# Verify registration
open http://localhost:8761 || xdg-open http://localhost:8761 2>/dev/null || echo "Open http://localhost:8761"

# Test endpoints via gateway
curl http://localhost:8080/api/preferences
```

## Module Summary

| Module | Port | Purpose | Key Dependencies |
|--------|------|---------|------------------|
| eureka-server | 8761 | Service discovery registry | spring-cloud-starter-netflix-eureka-server |
| api-gateway | 8080 | Single entry / routing / LB | spring-cloud-starter-gateway, eureka client |
| user-preference-service | 8081 | Manage user notification preferences | web, data-mongodb, validation, eureka client |
| notification-service | 8082 | Send and persist notifications (multi-channel) | web, data-mongodb, openfeign, mail, validation, eureka client |

Environment variables (runtime configurable):
- `MAIL_USERNAME` / `MAIL_PASSWORD`: SMTP credentials for email channel (notification-service)
- `SPRING_DATA_MONGODB_HOST`: Overridden in Docker to point services to `mongodb`
- `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`: Set in containers to internal Eureka URL

## Additional Endpoints (Beyond Prior List)

Notification Service also exposes:

```bash
GET /api/notifications/{id}        # Fetch single notification by ID (through gateway)
```

Internally, the Notification Service uses an OpenFeign client:

```java
@FeignClient(name = "user-preference-service")
interface UserPreferenceClient {
    @GetMapping("/preferences/{userId}")
    UserPreference getUserPreference(@PathVariable("userId") String userId);
}
```

This call is resolved via Eureka service discovery and load-balanced automatically.
mvn clean package -DskipTests
```

#### 3. Start All Services with Docker Compose

```bash
# Start all services
docker-compose up

# Or run in detached mode
docker-compose up -d
```

This will start:
- MongoDB (port 27017)
- Eureka Server (port 8761)
- API Gateway (port 8080)
- User Preference Service (port 8081)
- Notification Service (port 8082)

#### 4. Verify Services

Check that all services are running:
```bash
docker-compose ps
```

Access Eureka Dashboard:
```bash
open http://localhost:8761
```

#### 5. Test the System

Run the automated test script:
```bash
./test-api.sh
```

#### 6. Stop Services

```bash
docker-compose down

# To remove volumes as well
docker-compose down -v
```

### Option 2: Local Development Setup

#### 1. Clone the Repository

```bash
git clone https://github.com/Amoghk04/Notified.git
cd Notified
```

#### 2. Build the Project

```bash
mvn clean install
```

#### 3. Start MongoDB

Ensure MongoDB is running on localhost:27017:

```bash
# Using Docker
docker run -d -p 27017:27017 --name mongodb mongo:latest

# Or start your local MongoDB instance
mongod
```

### 4. Start the Services

Start services in the following order:

#### Start Eureka Server
```bash
cd eureka-server
mvn spring-boot:run
```
Access at: http://localhost:8761

#### Start API Gateway
```bash
cd api-gateway
mvn spring-boot:run
```
Access at: http://localhost:8080

#### Start User Preference Service
```bash
cd user-preference-service
mvn spring-boot:run
```
Running on: http://localhost:8081

#### Start Notification Service
```bash
cd notification-service
mvn spring-boot:run
```
Running on: http://localhost:8082

## API Endpoints

All requests should go through the API Gateway (http://localhost:8080).

### User Preference Service

#### Get all preferences
```bash
GET http://localhost:8080/api/preferences
```

#### Get preference by user ID
```bash
GET http://localhost:8080/api/preferences/{userId}
```

#### Create preference
```bash
POST http://localhost:8080/api/preferences
Content-Type: application/json

{
  "userId": "user123",
  "email": "user@example.com",
  "phoneNumber": "+1234567890",
  "enabledChannels": ["EMAIL", "APP"]
}
```

#### Update preference
```bash
PUT http://localhost:8080/api/preferences/{userId}
Content-Type: application/json

{
  "email": "newemail@example.com",
  "phoneNumber": "+1234567890",
  "enabledChannels": ["EMAIL", "SMS", "APP"]
}
```

#### Delete preference
```bash
DELETE http://localhost:8080/api/preferences/{userId}
```

### Notification Service

#### Get all notifications
```bash
GET http://localhost:8080/api/notifications
```

#### Get notifications by user ID
```bash
GET http://localhost:8080/api/notifications/user/{userId}
```

#### Get notification by ID
```bash
GET http://localhost:8080/api/notifications/{id}
```

#### Send notification
```bash
POST http://localhost:8080/api/notifications
Content-Type: application/json

{
  "userId": "user123",
  "subject": "Welcome!",
  "message": "Welcome to our service!"
}
```

#### Delete notification
```bash
DELETE http://localhost:8080/api/notifications/{id}
```

## How It Works

1. **Service Discovery**: All services register with Eureka Server on startup
2. **Request Routing**: API Gateway routes requests to appropriate services using Eureka for service lookup
3. **Preference Check**: When sending a notification, the Notification Service calls User Preference Service to fetch user preferences
4. **Delivery**: Notifications are delivered via enabled channels (Email/SMS/App) based on user preferences
5. **Persistence**: Both services store data in separate MongoDB databases

## Configuration

### Email Configuration (Optional)

To enable email notifications, set the following environment variables:

**For Docker Compose:**
Create a `.env` file in the root directory:
```bash
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password
```

**For Local Development:**
```bash
export MAIL_USERNAME=your-email@gmail.com
export MAIL_PASSWORD=your-app-password
```

Or update `notification-service/src/main/resources/application.yml`:

```yaml
spring:
  mail:
    username: your-email@gmail.com
    password: your-app-password
    properties:
      mail:
        smtp:
          connectiontimeout: 5000
          timeout: 5000
          writetimeout: 5000
```

**Note:** For Gmail, you need to use an "App Password" rather than your regular password. Generate one at: https://myaccount.google.com/apppasswords

**Service Behavior Update:** Email sending now fails fast when the mail subsystem is misconfigured (no silent fallback). SMTP timeouts added to reduce request blocking.

### MongoDB Configuration

Default MongoDB connection:
- Host: localhost
- Port: 27017
- User Preference DB: user_preferences_db
- Notification DB: notifications_db

To change, update the respective `application.yml` files.

## Extending the System

The system is designed to be easily extended:

### WhatsApp Channel Integration

To enable WhatsApp notifications via Twilio:
1. Sign up for Twilio and enable the WhatsApp sandbox (or production approval).
2. Obtain `Account SID`, `Auth Token`, and a WhatsApp-enabled from number (e.g. `whatsapp:+14155238886`).
3. Set environment variables before starting `notification-service`:
  ```bash
  export TWILIO_ACCOUNT_SID=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
  export TWILIO_AUTH_TOKEN=your_auth_token
  export TWILIO_WHATSAPP_FROM=whatsapp:+14155238886
  ```
4. Ensure user preferences include `"WHATSAPP"` in `enabledChannels` and a `phoneNumber` (E.164 format, e.g. `+1234567890`).
5. Send notifications normally; if credentials are missing the service logs a simulated send.

### Adding New Notification Channels

1. Add new channel enum to `NotificationChannel` (both preference and notification models)
2. Implement the channel logic in `NotificationChannelService`
3. Update user preferences to include the new channel

### Adding New Services

1. Create a new module in the parent POM
2. Add Eureka client dependency
3. Register with Eureka
4. Add routes in API Gateway configuration

## Technology Stack

- **Spring Boot 3.2.0** - Application framework
- **Spring Cloud 2023.0.0** - Microservices framework
- **Netflix Eureka** - Service discovery
- **Spring Cloud Gateway** - API Gateway
- **Spring Data MongoDB** - Data persistence
- **OpenFeign** - Inter-service communication
- **Spring Mail** - Email notifications
- **Maven** - Build tool

## Project Structure

```
Notified/
├── eureka-server/              # Service Discovery
├── api-gateway/                # API Gateway
├── user-preference-service/    # User Preferences Management
├── notification-service/       # Notification Delivery
└── pom.xml                     # Parent POM
```

## Testing

### Automated Testing

Use the provided test script:
```bash
./test-api.sh
```

This script will:
- Create multiple user preferences with different channel settings
- Send notifications to test users
- Update preferences and verify changes
- Display all data from the system

### Manual Testing

The system can be tested using curl, Postman, or any HTTP client:

```bash
# 1. Create a user preference
curl -X POST http://localhost:8080/api/preferences \
  -H "Content-Type: application/json" \
  -d '{"userId":"user123","email":"user@example.com","enabledChannels":["EMAIL","APP"]}'

# 2. Send a notification
curl -X POST http://localhost:8080/api/notifications \
  -H "Content-Type: application/json" \
  -d '{"userId":"user123","subject":"Test","message":"Hello World!"}'

# 3. Check notifications
curl http://localhost:8080/api/notifications/user/user123
```

### Viewing Logs

**Docker Compose:**
```bash
# View all logs
docker-compose logs -f

# View specific service logs
docker-compose logs -f notification-service
docker-compose logs -f user-preference-service
```

**Local Development:**
Check the console output of each service.

## Key Features

### 🎯 Decoupled Architecture
- Independent microservices with clear responsibilities
- Services can be developed, deployed, and scaled independently
- Easy to maintain and update individual components

### 🔄 Service Discovery
- Automatic service registration with Eureka
- Dynamic service lookup
- No hardcoded service URLs

### 🚪 Centralized API Gateway
- Single entry point for all client requests
- Request routing based on paths
- Load balancing across service instances

### 💾 Separate Data Stores
- Each service has its own MongoDB database
- Data isolation and independence
- Optimized data models per service

### 📢 Multi-Channel Notification Support
- Email notifications (via SMTP)
- WhatsApp notifications (via Twilio API)
- App push notifications (extensible with FCM)

### ⚙️ Personalized Preferences
- User-specific notification settings
- Channel-level preferences (enable/disable)
- Easy preference management via REST API

### 📈 Scalable Design
- Horizontal scaling of individual services
- Stateless services for easy replication
- MongoDB for high-volume data storage

### 🔌 Easy Extension
- Add new notification channels without modifying existing code
- Plug in new services easily
- Well-defined service contracts

## Benefits

1. **Maintainability**: Each service is focused on a single responsibility
2. **Scalability**: Scale services independently based on load
3. **Resilience**: Failure of one service doesn't bring down the entire system
4. **Technology Flexibility**: Different services can use different technologies if needed
5. **Team Autonomy**: Different teams can work on different services independently
6. **Easy Testing**: Services can be tested in isolation
7. **Deployment Flexibility**: Deploy services independently with zero downtime

## Troubleshooting

### Services Not Registering with Eureka
- Ensure Eureka Server is running and accessible
- Check that `eureka.client.service-url.defaultZone` is correctly configured
- Wait 30-60 seconds for services to register after startup

### MongoDB Connection Issues
- Verify MongoDB is running: `docker ps` or `mongosh`
- Check MongoDB connection strings in application.yml files
- Ensure ports are not blocked by firewall

### Email Not Sending
- Verify MAIL_USERNAME and MAIL_PASSWORD are set correctly
- For Gmail, use App Password, not regular password
- Check notification-service logs for SMTP errors

### API Gateway Not Routing Requests
- Verify all services are registered in Eureka (check http://localhost:8761)
- Check API Gateway logs for routing errors
- Ensure correct path prefixes (/api/preferences, /api/notifications)

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Support

For issues and questions:
- Open an issue on GitHub
- Check the documentation
- Review the logs for detailed error messages

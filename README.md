# Notified
Personalized Notification System - Spring Boot Microservices

## Overview

Notified is a scalable, decoupled microservices architecture for managing and delivering personalized notifications. The system uses Spring Boot microservices with MongoDB for data persistence, Eureka for service discovery, and an API Gateway for centralized request routing.

## Architecture

The system consists of four main microservices:

1. **Eureka Server** (Port 8761) - Service Discovery
2. **API Gateway** (Port 8080) - Request Routing
3. **User Preference Service** (Port 8081) - Manages user notification preferences
4. **Notification Service** (Port 8082) - Sends notifications via email/SMS/app

### Architecture Flow

```
Client → API Gateway → [User Preference Service | Notification Service]
                                ↑                         ↑
                                └─────── Eureka ──────────┘
```

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- MongoDB (running on localhost:27017)
- SMTP server credentials (optional, for email notifications)

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/Amoghk04/Notified.git
cd Notified
```

### 2. Build the Project

```bash
mvn clean install
```

### 3. Start MongoDB

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
  "emailEnabled": true,
  "smsEnabled": false,
  "appEnabled": true
}
```

#### Update preference
```bash
PUT http://localhost:8080/api/preferences/{userId}
Content-Type: application/json

{
  "email": "newemail@example.com",
  "emailEnabled": true,
  "smsEnabled": true,
  "appEnabled": true
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
```

### MongoDB Configuration

Default MongoDB connection:
- Host: localhost
- Port: 27017
- User Preference DB: user_preferences_db
- Notification DB: notifications_db

To change, update the respective `application.yml` files.

## Extending the System

The system is designed to be easily extended:

### Adding New Notification Channels

1. Add new channel enum to `NotificationChannel`
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

The system can be tested using curl, Postman, or any HTTP client:

```bash
# 1. Create a user preference
curl -X POST http://localhost:8080/api/preferences \
  -H "Content-Type: application/json" \
  -d '{"userId":"user123","email":"user@example.com","emailEnabled":true}'

# 2. Send a notification
curl -X POST http://localhost:8080/api/notifications \
  -H "Content-Type: application/json" \
  -d '{"userId":"user123","subject":"Test","message":"Hello World!"}'

# 3. Check notifications
curl http://localhost:8080/api/notifications/user/user123
```

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

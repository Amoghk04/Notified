# Notified System Architecture

## Overview

Notified is a microservices-based personalized notification system built with Spring Boot. It demonstrates key microservices patterns including service discovery, API gateway, and inter-service communication.

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                          Client Layer                            │
│  (Web Apps, Mobile Apps, External Systems, APIs)                │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                │ HTTP/REST
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                       API Gateway (8080)                         │
│  • Single entry point                                            │
│  • Request routing                                               │
│  • Load balancing                                                │
└────────┬──────────────────────────────────────┬─────────────────┘
         │                                      │
         │ Routes:                              │ Routes:
         │ /api/preferences/**                  │ /api/notifications/**
         │                                      │
         ▼                                      ▼
┌──────────────────────────┐          ┌──────────────────────────┐
│  User Preference Service │          │  Notification Service    │
│        (8081)            │◄─────────│        (8082)            │
│                          │  Feign   │                          │
│  • User preferences      │  Client  │  • Send notifications    │
│  • Channel settings      │          │  • Email/SMS/App         │
│  • MongoDB storage       │          │  • MongoDB storage       │
└──────────┬───────────────┘          └─────────┬────────────────┘
           │                                    │
           │ Registers with                     │ Registers with
           │                                    │
           └────────────────┬───────────────────┘
                            │
                            ▼
                ┌───────────────────────┐
                │  Eureka Server (8761) │
                │  • Service discovery  │
                │  • Service registry   │
                │  • Health monitoring  │
                └───────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                        Data Layer                                │
│                                                                   │
│  ┌─────────────────────┐       ┌──────────────────────┐         │
│  │ MongoDB             │       │ MongoDB              │         │
│  │ user_preferences_db │       │ notifications_db     │         │
│  │ (27017)             │       │ (27017)              │         │
│  └─────────────────────┘       └──────────────────────┘         │
└─────────────────────────────────────────────────────────────────┘
```

## Component Details

### 1. Eureka Server (Port 8761)

**Purpose**: Service discovery and registry

**Responsibilities**:
- Maintain registry of all microservices
- Provide service locations to clients
- Health check monitoring
- Load balancing support

**Technology**: Netflix Eureka Server

**Key Features**:
- Self-preservation mode
- Service heartbeat
- Dynamic service registration/deregistration

### 2. API Gateway (Port 8080)

**Purpose**: Single entry point for all client requests

**Responsibilities**:
- Route requests to appropriate services
- Load balance across service instances
- Service discovery integration
- Request/response transformation (if needed)

**Technology**: Spring Cloud Gateway

**Routes Configuration**:
```yaml
/api/preferences/** → user-preference-service
/api/notifications/** → notification-service
```

**Key Features**:
- Dynamic routing using Eureka
- Path-based routing
- Load balancing
- Filter support

### 3. User Preference Service (Port 8081)

**Purpose**: Manage user notification preferences

**Responsibilities**:
- Store user notification preferences
- Manage channel enable/disable settings
- Store contact information (email, phone)
- Provide preference lookup API

**Technology**: Spring Boot, Spring Data MongoDB

**Data Model**:
```java
UserPreference {
    id: String
    userId: String
    email: String
    phoneNumber: String
    emailEnabled: boolean
    smsEnabled: boolean
    appEnabled: boolean
    enabledChannels: Set<NotificationChannel>
}
```

**API Endpoints**:
- `GET /preferences` - Get all preferences
- `GET /preferences/{userId}` - Get by user ID
- `POST /preferences` - Create preference
- `PUT /preferences/{userId}` - Update preference
- `DELETE /preferences/{userId}` - Delete preference

**Database**: `user_preferences_db` in MongoDB

### 4. Notification Service (Port 8082)

**Purpose**: Send notifications through multiple channels

**Responsibilities**:
- Receive notification requests
- Fetch user preferences via Feign client
- Send notifications via enabled channels
- Store notification history
- Handle delivery failures

**Technology**: Spring Boot, Spring Data MongoDB, OpenFeign, Spring Mail

**Data Model**:
```java
Notification {
    id: String
    userId: String
    message: String
    subject: String
    channels: Set<NotificationChannel>
    status: NotificationStatus (PENDING/SENT/FAILED)
    createdAt: LocalDateTime
    sentAt: LocalDateTime
}
```

**Notification Channels**:
1. **Email**: SMTP via Spring Mail (configurable)
2. **SMS**: Extensible (Twilio, AWS SNS integration ready)
3. **App**: Extensible (Firebase Cloud Messaging integration ready)

**API Endpoints**:
- `GET /notifications` - Get all notifications
- `GET /notifications/user/{userId}` - Get by user ID
- `GET /notifications/{id}` - Get by notification ID
- `POST /notifications` - Send notification
- `DELETE /notifications/{id}` - Delete notification

**Database**: `notifications_db` in MongoDB

**Inter-Service Communication**:
Uses OpenFeign to call User Preference Service:
```java
@FeignClient(name = "user-preference-service")
public interface UserPreferenceClient {
    @GetMapping("/preferences/{userId}")
    UserPreference getUserPreference(@PathVariable String userId);
}
```

## Communication Flow

### Sending a Notification

```
1. Client → API Gateway
   POST /api/notifications
   Body: { userId, subject, message }

2. API Gateway → Notification Service
   Routes request to available instance via Eureka

3. Notification Service → User Preference Service
   GET /preferences/{userId}
   (via Feign Client + Eureka service discovery)

4. User Preference Service → MongoDB
   Query user preferences

5. User Preference Service → Notification Service
   Return preferences (email, phone, enabled channels)

6. Notification Service → Notification Channels
   - If emailEnabled: Send email via SMTP
   - If smsEnabled: Send SMS (extension point)
   - If appEnabled: Send push notification (extension point)

7. Notification Service → MongoDB
   Store notification record with status

8. Notification Service → API Gateway → Client
   Return notification record with delivery status
```

## Design Patterns

### 1. Service Discovery Pattern
- Services register with Eureka on startup
- Clients query Eureka for service locations
- Dynamic scaling and failover

### 2. API Gateway Pattern
- Single entry point for clients
- Centralized routing and filtering
- Cross-cutting concerns (authentication, logging, etc.)

### 3. Database per Service
- Each service has its own MongoDB database
- Data autonomy and independence
- Optimized schema per service

### 4. Client-Side Discovery
- Services query Eureka for locations
- Client-side load balancing
- Reduced latency

### 5. Circuit Breaker (Future Enhancement)
- Handle service failures gracefully
- Prevent cascading failures
- Fallback mechanisms

## Scalability Considerations

### Horizontal Scaling
- All services are stateless
- Can run multiple instances of each service
- API Gateway load balances across instances
- MongoDB supports replica sets and sharding

### Vertical Scaling
- Adjust JVM heap size
- Optimize MongoDB queries
- Connection pool tuning

### Caching (Future Enhancement)
- Cache user preferences in Notification Service
- Redis for distributed caching
- Reduce inter-service calls

## Security Considerations (Future Enhancements)

1. **Authentication & Authorization**
   - JWT tokens
   - OAuth2 integration
   - API key management

2. **Service-to-Service Security**
   - mTLS between services
   - Service mesh (Istio/Linkerd)

3. **Data Encryption**
   - Encrypt sensitive data at rest
   - TLS for data in transit
   - Secrets management (Vault)

4. **Rate Limiting**
   - API Gateway rate limiting
   - Per-user rate limits
   - DDoS protection

## Monitoring & Observability (Future Enhancements)

1. **Logging**
   - Centralized logging (ELK Stack)
   - Correlation IDs for request tracing
   - Structured logging

2. **Metrics**
   - Prometheus + Grafana
   - Service health metrics
   - Business metrics

3. **Distributed Tracing**
   - Zipkin/Jaeger
   - Request flow visualization
   - Performance bottleneck identification

4. **Health Checks**
   - Spring Boot Actuator
   - Liveness and readiness probes
   - Custom health indicators

## Extension Points

### Adding New Notification Channels

1. Add new channel to `NotificationChannel` enum
2. Implement delivery logic in `NotificationChannelService`
3. Update user preferences model
4. No changes needed in other services

Example for WhatsApp:
```java
public void sendWhatsAppNotification(UserPreference pref, Notification notif) {
    // WhatsApp Business API integration
}
```

### Adding New Microservices

1. Create new Spring Boot module
2. Add Eureka client dependency
3. Register with Eureka
4. Add route in API Gateway
5. Implement business logic

### Adding Event-Driven Communication

Replace synchronous Feign calls with:
- Apache Kafka
- RabbitMQ
- AWS SQS/SNS

Benefits:
- Asynchronous processing
- Better decoupling
- Event sourcing
- CQRS pattern

## Technology Stack Summary

| Component | Technology |
|-----------|-----------|
| Framework | Spring Boot 3.2.0 |
| Cloud | Spring Cloud 2023.0.0 |
| Service Discovery | Netflix Eureka |
| API Gateway | Spring Cloud Gateway |
| Inter-Service Communication | OpenFeign |
| Database | MongoDB |
| Email | Spring Mail (SMTP) |
| Build Tool | Maven |
| Containerization | Docker, Docker Compose |
| Java Version | 17 |

## Deployment Options

### Development
- Docker Compose (current setup)
- Local Maven runs

### Production Options
1. **Kubernetes**
   - Container orchestration
   - Auto-scaling
   - Self-healing
   - Service mesh integration

2. **Cloud Platforms**
   - AWS ECS/EKS
   - Azure Container Instances/AKS
   - Google Cloud Run/GKE

3. **Platform as a Service**
   - Heroku
   - Cloud Foundry
   - Azure Spring Apps

## Performance Characteristics

### Throughput
- API Gateway: ~1000s req/sec (depends on instance size)
- User Preference Service: ~500 req/sec per instance
- Notification Service: Limited by email/SMS providers

### Latency
- User Preference CRUD: ~10-50ms
- Notification delivery: 100ms - 5s (depends on channel)
- Inter-service communication: ~10-20ms

### Resource Requirements
- Eureka Server: 512MB RAM minimum
- API Gateway: 512MB RAM minimum
- User Preference Service: 512MB RAM minimum
- Notification Service: 1GB RAM minimum (due to email processing)
- MongoDB: 1GB RAM minimum

## Conclusion

This architecture provides a solid foundation for a scalable, maintainable notification system. The decoupled nature of microservices allows for independent development, deployment, and scaling of components. Future enhancements can be added without major architectural changes.

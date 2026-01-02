# Communication Service

> **Part of [Core Microservices Project](https://github.com/CoreWebMicroservices/corems-project)** - Enterprise-grade microservices toolkit for rapid application development

Email, SMS, and notification management microservice for CoreMS.

## Features

- Email sending (HTML/Text)
- SMS notifications via Twilio
- Slack integration
- Message queuing with RabbitMQ
- Template management

## Quick Start
```bash
# Clone the main project
git clone https://github.com/CoreWebMicroservices/corems-project.git
cd corems-project

# Build and start communication service
./setup.sh build communication-ms
./setup.sh start communication-ms

# Or start entire stack
./setup.sh start-all
```

### API Endpoints
- **Base URL**: `http://localhost:3001`
- **Health**: `GET /actuator/health`
- **Send Email**: `POST /api/messages/email`
- **Send SMS**: `POST /api/messages/sms`

## Environment Variables

Copy `.env-example` to `.env` and configure:
```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/corems
MAIL_HOST=smtp.gmail.com
MAIL_USERNAME=your_email@gmail.com
MAIL_PASSWORD=your_app_password
SMS_ACCOUNT_SID=your_twilio_sid
SMS_AUTH_TOKEN=your_twilio_token
RABBIT_DEFAULT_QUEUE=communication_queue
```

## Database Schema

- `communication_ms` schema with tables:
  - `message` - Message records
  - `email` - Email details
  - `sms` - SMS details
  - `email_attachment` - File attachments

## Architecture

```
communication-ms/
├── communication-api/     # OpenAPI spec + generated models
├── communication-client/  # API client for other services
├── communication-service/ # Main application
└── migrations/           # Database migrations
```
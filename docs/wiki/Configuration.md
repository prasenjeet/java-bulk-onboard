# Configuration

All backend settings live in:

```
backend/src/main/resources/application.properties
```

---

## Application Properties

### Server

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8080` | HTTP port the API listens on |

### File Upload

| Property | Default | Description |
|----------|---------|-------------|
| `spring.servlet.multipart.enabled` | `true` | Enable multipart file uploads |
| `spring.servlet.multipart.max-file-size` | `10MB` | Maximum size of a single uploaded file |
| `spring.servlet.multipart.max-request-size` | `10MB` | Maximum size of the whole multipart request |

### Processing

| Property | Default | Description |
|----------|---------|-------------|
| `app.onboarding.processing-delay-ms` | `300` | Artificial delay per record (ms). Set to `0` for instant processing. |
| `app.onboarding.max-concurrent-jobs` | `10` | Placeholder for future bounded thread-pool size |

### Logging

| Property | Default | Description |
|----------|---------|-------------|
| `logging.level.com.example.bulkonboard` | `DEBUG` | Log level for application classes. Change to `INFO` in production. |

---

## Example — Production `application.properties`

```properties
server.port=8080

spring.servlet.multipart.enabled=true
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=50MB

# No artificial delay in production — wire up real downstream calls
app.onboarding.processing-delay-ms=0

# Quieter logs in production
logging.level.com.example.bulkonboard=INFO
logging.level.root=WARN
```

---

## CORS

By default CORS allows **any origin** (`allowedOriginPatterns("*")`).
Before deploying to production, restrict this to your frontend domain in
`backend/src/main/java/com/example/bulkonboard/config/CorsConfig.java`:

```java
registry.addMapping("/api/**")
        .allowedOriginPatterns("https://onboard.yourdomain.com")
        .allowedMethods("GET", "POST")
        .maxAge(3600);
```

---

## Changing the API Base URL (Frontend)

Edit `frontend/js/onboardingService.js`:

```javascript
.constant('APP_CONFIG', {
  apiBase: 'https://api.yourdomain.com/api/onboard'
})
```

---

## Async Thread Pool

By default Spring uses `SimpleAsyncTaskExecutor` (one new thread per task).
For production replace it with a bounded pool to avoid thread exhaustion:

```java
// Add to any @Configuration class
@Bean(name = "onboardingExecutor")
public TaskExecutor onboardingExecutor() {
    ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
    exec.setCorePoolSize(4);
    exec.setMaxPoolSize(20);
    exec.setQueueCapacity(100);
    exec.setThreadNamePrefix("onboard-");
    exec.initialize();
    return exec;
}
```

Then annotate the async method:

```java
@Async("onboardingExecutor")
protected void processAsync(JobStatus job, List<AccountRecord> records) { … }
```

---

## Persistent Job Store

Jobs are currently held in a `ConcurrentHashMap` and lost on restart.
Replace with a database by:

1. Adding Spring Data JPA to `pom.xml`.
2. Converting `JobStatus` and `ProcessingResult` to `@Entity` classes.
3. Injecting a `JobStatusRepository` into `OnboardingService`.

---

## SSE Timeout

The SSE `SseEmitter` has a 5-minute timeout:

```java
SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
```

Increase it for very large files:

```java
SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);  // 30 minutes
```

---

## Removing the Random Failure Simulation

In `OnboardingService.processAsync()`, remove or comment out these lines:

```java
// Simulate ~10% random backend failures
if (Math.random() < 0.10) {
    errors.add("Downstream system error: account creation timeout");
}
```

Replace with your actual downstream API call (e.g., a REST call to an account
management service or a database `INSERT`).

---

## Environment Variables

Spring Boot properties can be overridden with environment variables using
the `SPRING_` prefix pattern:

```bash
export SERVER_PORT=9090
export APP_ONBOARDING_PROCESSING_DELAY_MS=0
export SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=50MB
java -jar bulk-onboard-1.0.0.jar
```

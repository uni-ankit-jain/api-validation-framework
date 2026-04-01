# api-validation-framework

A shared Spring Boot auto-configuration library that provides consistent HTTP header validation,
standardized error responses, and reusable JSR-303 constraint annotations across all Uniphore
microservices.

---

## Why This Library Exists

Each of the 13+ Uniphore Java microservices previously duplicated the same header and input
validation logic — checking for `Authorization`, `Content-Type`, and custom tenant headers — with
no consistent error response format. This library centralises that logic into a single dependency
with zero boilerplate integration.

**What it provides out of the box (zero configuration required):**

- Authorization header presence + `Bearer` scheme enforcement
- Content-Type validation on mutating requests (`POST`, `PUT`, `PATCH`)
- Standardized JSON error envelope with `traceId` and `tenantId`
- JSR-303 replacement handler for `MethodArgumentNotValidException`

**What it adds via opt-in configuration:**

- Per-service required custom headers (e.g. `X-Tenant-ID`)
- Per-endpoint header overrides via `@ValidateHeader`
- Endpoint opt-out via `@SkipValidation`
- Custom string safety constraints via `@SafeString` and `@NotBlankIfPresent`

---

## Requirements

| Dependency | Version |
|---|---|
| Java | 17+ |
| Spring Boot | 3.x |
| Jakarta Servlet API | 6.x (provided by Spring Boot 3) |

---

## Installation

### Step 1 — Add the dependency

Add the GitHub Packages repository to your `build.gradle` (already present in most services via
other Uniphore libraries):

```groovy
repositories {
    maven {
        url 'https://maven.pkg.github.com/uniphore/api-validation-framework'
        credentials {
            username = project.findProperty('GITHUB_ACTOR') ?: System.getenv('GITHUB_ACTOR')
            password = project.findProperty('GITHUB_PKG_READ_TOKEN') ?: System.getenv('GITHUB_PKG_READ_TOKEN')
        }
    }
}

dependencies {
    implementation 'com.uniphore:api-validation-framework:1.0.0-SNAPSHOT'
}
```

That's all that's needed for default behavior. The library auto-configures itself via Spring Boot's
`AutoConfiguration.imports` — no `@Import` or `@ComponentScan` changes are required.

### Step 2 — Add properties (optional)

The library works with zero properties. Add only what you need to override:

```properties
# Disable the entire library (e.g. for local dev or testing)
uniphore.validation.enabled=false

# Keep your existing AppExceptionHandler as the authoritative error formatter
uniphore.validation.exception-handler.enabled=false

# Require X-Tenant-ID on every request (except bypass paths)
uniphore.validation.custom-headers.required=X-Tenant-ID

# Require both X-Tenant-ID and X-Correlation-ID
uniphore.validation.custom-headers.required=X-Tenant-ID,X-Correlation-ID
```

### Step 3 — Per-endpoint overrides (optional)

```java
// Require an additional header only on this endpoint
@PostMapping("/api/conversations")
@ValidateHeader(name = "X-Request-Source", required = true)
public ResponseEntity<ConversationDto> create(@Valid @RequestBody CreateRequest req) {
    // ...
}

// Require multiple extra headers on this endpoint
@PostMapping("/api/transfers")
@ValidateHeader(name = "X-Idempotency-Key", required = true)
@ValidateHeader(name = "X-Request-Source", required = true)
public ResponseEntity<TransferDto> transfer(@Valid @RequestBody TransferRequest req) {
    // ...
}

// Skip all validation on a public / webhook endpoint
@PostMapping("/webhooks/inbound")
@SkipValidation
public ResponseEntity<Void> handleWebhook(@RequestBody String payload) {
    // ...
}
```

### Step 4 — Integrating with an existing `AppExceptionHandler` (optional)

If your service already has an `@RestControllerAdvice` that formats error responses, disable the
library's handler and add a delegating method to your existing advice:

**`application.properties`:**
```properties
uniphore.validation.exception-handler.enabled=false
```

**`AppExceptionHandler.java`:**
```java
import com.uniphore.platform.validation.exception.HeaderValidationException;

@RestControllerAdvice
public class AppExceptionHandler {

    // ... your existing handlers ...

    @ExceptionHandler(HeaderValidationException.class)
    public ResponseEntity<ErrorMsg> handleHeaderValidation(HeaderValidationException ex) {
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(new ErrorMsg(ex.getHttpStatus().value(), ex.getMessage()));
    }
}
```

Use `ex.getHttpStatus()` for the HTTP status code and `ex.getHeaderName()` to identify which
header triggered the failure.

---

## Configuration Reference

All properties are under the `uniphore.validation` prefix.

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | `boolean` | `true` | Master switch — set to `false` to disable the entire library |
| `authorization-header.required` | `boolean` | `true` | Require the `Authorization` header on every non-bypassed request |
| `authorization-header.require-bearer-prefix` | `boolean` | `true` | Enforce `Bearer <token>` format; also validates the token is non-empty |
| `content-type.validate-on-mutating` | `boolean` | `true` | Validate `Content-Type` on `POST`, `PUT`, and `PATCH` requests |
| `content-type.allowed-types` | `List<String>` | `[application/json]` | Prefix-matched allowed Content-Type values (e.g. `application/json; charset=UTF-8` matches `application/json`) |
| `custom-headers.required` | `List<String>` | `[]` | Header names that must be present and non-blank on every non-bypassed request |
| `custom-headers.not-blank-if-present` | `List<String>` | `[]` | Header names that, when present, must not be blank |
| `bypass-paths` | `List<String>` | `[/health/**, /swagger-ui/**, /v3/api-docs/**]` | Ant-style path patterns that skip all validation |
| `filter-order` | `int` | `Integer.MIN_VALUE + 10` | Servlet filter order; fires after MDC setup (CommonFilter) but before JWT decode (SecurityFilter) |
| `exception-handler.enabled` | `boolean` | `true` | Register the library's `@RestControllerAdvice`; set to `false` for services with an existing handler |

### Full `application.properties` example

```properties
uniphore.validation.enabled=true
uniphore.validation.authorization-header.required=true
uniphore.validation.authorization-header.require-bearer-prefix=true
uniphore.validation.content-type.validate-on-mutating=true
uniphore.validation.content-type.allowed-types=application/json,application/xml
uniphore.validation.custom-headers.required=X-Tenant-ID,X-Correlation-ID
uniphore.validation.custom-headers.not-blank-if-present=X-Optional-Source
uniphore.validation.bypass-paths=/health/**,/actuator/**,/swagger-ui/**,/v3/api-docs/**
uniphore.validation.filter-order=-2147483638
uniphore.validation.exception-handler.enabled=true
```

---

## Annotations

### `@ValidateHeader`

Declares a required (or conditionally required) HTTP header for a controller method or class.
Repeatable — multiple headers can be stacked on the same element. Method-level declarations
override class-level declarations for the same header name.

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Repeatable(ValidateHeaders.class)
public @interface ValidateHeader {
    String name();                     // header name, e.g. "X-Tenant-ID"
    boolean required() default true;   // fail if header is absent
    boolean notBlank() default true;   // fail if header is present but blank
    String message() default "";       // custom error message (auto-generated if blank)
}
```

**Examples:**

```java
// Class-level — applies to all methods unless overridden
@RestController
@RequestMapping("/api/reports")
@ValidateHeader(name = "X-Report-Context", required = true)
public class ReportController {

    // Inherits X-Report-Context requirement from class
    @GetMapping
    public List<Report> list() { ... }

    // Adds X-Export-Format on top of the class-level requirement
    @GetMapping("/export")
    @ValidateHeader(name = "X-Export-Format", required = true, message = "Specify export format: csv or pdf")
    public ResponseEntity<byte[]> export() { ... }
}
```

Responses when validation fails use HTTP **422 Unprocessable Entity**.

---

### `@SkipValidation`

Opts a single controller method out of **all** header validation performed by the library filter.
Useful for public webhook endpoints, callback URLs, or health-check endpoints not covered by
`bypass-paths`. Method-level only — cannot be placed on a class.

```java
@PostMapping("/webhooks/twilio")
@SkipValidation
public ResponseEntity<Void> twilioCallback(@RequestBody String body) {
    // No Authorization or Content-Type validation runs here
    return ResponseEntity.ok().build();
}
```

---

### `@SafeString`

JSR-303 constraint that validates a DTO string field against a whitelist regex. Use this to
prevent injection attacks by allowing only known-safe characters.

```java
public @interface SafeString {
    String pattern() default "^[\\w\\s\\-]+$";  // alphanumeric, spaces, hyphens, underscores
    String message() default "Field contains invalid characters";
}
```

**Example:**

```java
public class CreateConversationRequest {

    @NotBlank
    @SafeString(pattern = "^[a-zA-Z0-9 _-]+$")
    private String title;

    // Allow letters, numbers, spaces, and common punctuation
    @SafeString(pattern = "^[\\w\\s.,!?'-]+$", message = "Description contains unsupported characters")
    private String description;
}
```

Null values pass validation — pair with `@NotNull` if null should be rejected.

---

### `@NotBlankIfPresent`

JSR-303 constraint for optional-but-non-blank fields. Passes when the value is `null` (field
omitted from the request), but fails when the value is an empty or whitespace-only string.

```java
public class UpdateProfileRequest {

    // Omitting this field is fine; sending "" or "   " is not
    @NotBlankIfPresent
    private String displayName;

    @NotBlankIfPresent
    @SafeString
    private String bio;
}
```

---

## Error Response Format

All validation failures return a consistent JSON envelope:

```json
{
  "timestamp": "2025-04-01T10:23:45.123Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Authorization header is required",
  "traceId": "abc-123",
  "tenantId": "tenant-uuid",
  "errors": [
    {
      "field": "Authorization",
      "message": "Authorization header is required"
    }
  ]
}
```

| Field | Description |
|---|---|
| `timestamp` | ISO-8601 UTC timestamp of the error |
| `status` | HTTP status code |
| `error` | HTTP status reason phrase |
| `message` | Human-readable summary |
| `traceId` | Resolved from MDC key `auditTraceId`, falls back to request attribute `traceId` |
| `tenantId` | Resolved from MDC key `auditTenantId`, falls back to request attribute `tenantId` |
| `errors` | Array of per-field or per-header errors; omitted when empty |

### HTTP status codes used

| Scenario | Status |
|---|---|
| Missing or malformed `Authorization` header | `401 Unauthorized` |
| Missing or disallowed `Content-Type` | `400 Bad Request` |
| Missing globally required custom header (from properties) | `400 Bad Request` |
| Missing per-endpoint `@ValidateHeader` header | `422 Unprocessable Entity` |
| JSR-303 `@Valid` body validation failure | `400 Bad Request` |

---

## Filter Ordering

The library filter is registered at `Integer.MIN_VALUE + 10` (i.e. `HIGHEST_PRECEDENCE + 10`).
This places it:

- **After** `CommonFilter` (MDC trace/tenant ID setup, registered at default servlet container order)
- **Before** `SecurityFilter` (JWT decode and Spring Security authentication)

This ordering ensures:
1. `traceId` and `tenantId` are already in the MDC when error responses are built.
2. Requests with invalid headers are rejected before JWT decode is attempted — no wasted crypto work.

Override with `uniphore.validation.filter-order` if your service has a different filter chain layout.

---

## Advanced: Replacing the Filter or Handler

Both the filter and the exception handler are guarded by `@ConditionalOnMissingBean`. Declare your
own bean of the same type to take full control:

```java
// Subclass the filter to add extra validation steps
@Component
public class MyHeaderValidationFilter extends HeaderValidationFilter {

    public MyHeaderValidationFilter(ValidationProperties props,
                                    RequestMappingHandlerMapping mapping) {
        super(props, mapping);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        // custom pre-validation
        super.doFilterInternal(req, res, chain);
        // custom post-validation
    }
}
```

```java
// Replace the exception handler entirely
@RestControllerAdvice
public class MyValidationExceptionHandler extends ValidationExceptionHandler {
    // override individual @ExceptionHandler methods as needed
}
```

---

## Building and Publishing

### Build and run tests

```bash
./gradlew build
```

### Publish to Maven Local (for local integration testing)

```bash
./gradlew publishToMavenLocal
```

The JAR will be available at:
`~/.m2/repository/com/uniphore/api-validation-framework/1.0.0-SNAPSHOT/`

In the consuming service's `build.gradle`, add `mavenLocal()` to the repositories block:

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}
```

### Publish to GitHub Packages

```bash
GITHUB_ACTOR=<your-username> GITHUB_PKG_PUBLISH_TOKEN=<token> ./gradlew publish
```

---

## Project Structure

```
api-validation-framework/
├── build.gradle
├── settings.gradle
└── src/
    ├── main/
    │   ├── java/com/uniphore/platform/validation/
    │   │   ├── annotation/
    │   │   │   ├── ValidateHeader.java         # Repeatable per-endpoint header requirement
    │   │   │   ├── ValidateHeaders.java        # Container annotation (compiler-generated)
    │   │   │   └── SkipValidation.java         # Opt-out on a specific endpoint
    │   │   ├── autoconfigure/
    │   │   │   └── ValidationAutoConfiguration.java  # Spring Boot auto-configuration entry point
    │   │   ├── exception/
    │   │   │   ├── HeaderValidationException.java    # Carries HttpStatus + headerName
    │   │   │   └── BodyValidationException.java      # Carries Map<field, List<message>>
    │   │   ├── filter/
    │   │   │   └── HeaderValidationFilter.java       # OncePerRequestFilter — core validation logic
    │   │   ├── handler/
    │   │   │   └── ValidationExceptionHandler.java   # @RestControllerAdvice
    │   │   ├── model/
    │   │   │   ├── ValidationError.java              # {field, message, rejectedValue}
    │   │   │   └── ValidationErrorResponse.java      # Standard error envelope
    │   │   ├── properties/
    │   │   │   └── ValidationProperties.java         # uniphore.validation.* config
    │   │   └── validator/
    │   │       ├── SafeString.java / SafeStringValidator.java
    │   │       └── NotBlankIfPresent.java / NotBlankIfPresentValidator.java
    │   └── resources/META-INF/spring/
    │       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    └── test/
        └── java/com/uniphore/platform/validation/
            ├── filter/HeaderValidationFilterTest.java
            ├── handler/ValidationExceptionHandlerTest.java
            └── properties/ValidationPropertiesTest.java
```

---

## License

Internal Uniphore library — not for public distribution.

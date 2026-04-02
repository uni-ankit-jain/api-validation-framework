# api-validation-framework

A shared Spring Boot auto-configuration library that provides consistent HTTP header validation,
request body field validation, and standardized error responses across all Uniphore microservices.

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

- Per-service required custom headers (e.g. `X-Tenant-ID`) via properties
- Per-endpoint header rules via `@HeaderConstraints` + inline `@HeaderRule` entries
- Endpoint opt-out via `@SkipValidation`
- Custom string safety constraints via `@SafeString` and `@NotBlankIfPresent`
- Per-endpoint field allowed-value and length rules via `@FieldConstraints` / `@FieldRule`
- Protobuf message body validation — the same `@FieldConstraints` / `@FieldRule` annotations work transparently on `com.google.protobuf.Message` request bodies when `protobuf-java` is on the classpath

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

# Require X-Tenant-ID and X-Correlation-ID on every request (except bypass paths)
uniphore.validation.custom-headers.required=X-Tenant-ID,X-Correlation-ID
```

### Step 3 — Per-endpoint header rules (optional)

Use `@HeaderConstraints` with inline `@HeaderRule` entries to declare all header requirements
for an endpoint in one place:

```java
@HeaderConstraints({
    @HeaderRule(name = "X-Tenant-ID"),
    @HeaderRule(name = "X-Correlation-ID"),
    @HeaderRule(name = "X-Source", required = false)  // present → non-blank; absent → OK
})
@PostMapping("/api/orders")
public ResponseEntity<OrderResponse> createOrder(@RequestBody @Valid OrderRequest req) { ... }

// Skip all validation on a public / webhook endpoint
@HeaderConstraints(skipAuth = true)
@PostMapping("/webhooks/inbound")
public ResponseEntity<Void> handleWebhook(@RequestBody String payload) { ... }
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
| `content-type.allowed-types` | `List<String>` | `[application/json]` | Prefix-matched allowed Content-Type values (e.g. `application/json; charset=UTF-8` matches `application/json`). Add `application/x-protobuf` here when accepting protobuf request bodies. |
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

### `@HeaderConstraints` / `@HeaderRule`

Declares all HTTP header validation rules for a controller method or class in one place.
Inline `@HeaderRule` elements cover required headers, optional-but-non-blank headers, and custom
error messages. `@HeaderConstraints` also lets you override the Authorization and Content-Type
behavior per endpoint. When placed on a class, applies to every handler method within it; a
method-level annotation takes full precedence over a class-level one.

```java
public @interface HeaderConstraints {
    HeaderRule[] value()            default {};    // inline per-header rules
    boolean      skipAuth()         default false; // skip Authorization check for this endpoint
    String[]     allowedContentTypes() default {}; // override global Content-Type list
}

public @interface HeaderRule {
    String  name();                   // header name, e.g. "X-Tenant-ID"
    boolean required() default true;  // must be present and non-blank
    boolean notBlank() default true;  // must be non-blank when present
    String  message()  default "";    // custom error message (auto-generated if blank)
}
```

**Example — required and optional headers:**

```java
@HeaderConstraints({
    @HeaderRule(name = "X-Tenant-ID"),
    @HeaderRule(name = "X-Correlation-ID"),
    @HeaderRule(name = "X-Source", required = false)   // absent → OK; present → non-blank
})
@PostMapping("/orders")
public ResponseEntity<OrderResponse> createOrder(@RequestBody @Valid OrderRequest req) { ... }
```

**Example — override Content-Type and skip Authorization:**

```java
@HeaderConstraints(
    value               = { @HeaderRule(name = "X-Tenant-ID") },
    skipAuth            = true,
    allowedContentTypes = {"application/xml"}
)
@PostMapping("/webhooks/inbound")
public ResponseEntity<Void> handleWebhook(@RequestBody String payload) { ... }
```

**Example — class-level default with a method-level override:**

```java
@RestController
@RequestMapping("/api/reports")
@HeaderConstraints(
    value               = { @HeaderRule(name = "X-Tenant-ID") },
    allowedContentTypes = {"application/json"}
)
public class ReportController {

    // Inherits class-level rules
    @GetMapping
    public List<Report> list() { ... }

    // Method-level wins — also accepts XML and requires an extra header
    @GetMapping("/export")
    @HeaderConstraints(
        value = {
            @HeaderRule(name = "X-Tenant-ID"),
            @HeaderRule(name = "X-Export-Format", message = "Specify export format: csv or pdf")
        },
        allowedContentTypes = {"application/json", "application/xml"}
    )
    public ResponseEntity<byte[]> export() { ... }
}
```

**Interaction with global properties:**

| `@HeaderConstraints` attribute | Interaction |
|---|---|
| `@HeaderRule` entries | Applied in addition to globally configured `custom-headers.*` from properties |
| `skipAuth = true` | Skips Authorization check even if `authorization-header.required=true` globally |
| `allowedContentTypes` non-empty | Replaces `content-type.allowed-types` for this endpoint only |
| `allowedContentTypes` empty (default) | Falls through to the global `content-type.allowed-types` list |

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

### `@FieldConstraints` / `@FieldRule`

Declares field validation rules directly on the controller method or class — no DTO changes
required. Each `@FieldRule` can enforce allowed values, a length constraint, or both in a single
declaration. Validation is enforced by an AOP aspect that inspects the `@RequestBody` argument
before the method executes. All violations are collected and reported at once.

```java
public @interface FieldConstraints {
    FieldRule[] value();
}

public @interface FieldRule {
    String   field();                 // field name in the request body object
    String[] values()  default {};    // exhaustive list of allowed values (empty = no constraint)
    boolean  ignoreCase() default false; // case-insensitive allowed-values comparison
    int      min()     default -1;    // minimum length, -1 = no minimum
    int      max()     default -1;    // maximum length, -1 = no maximum
    String   message() default "";    // custom error message (auto-generated if blank)
}
```

`min` / `max` apply to character count for `String` fields and element count for `Collection`
fields. Other types log a warning and skip the length check.

**Example — allowed values:**

```java
@FieldConstraints({
    @FieldRule(field = "status",   values = {"ACTIVE", "INACTIVE", "PENDING"}),
    @FieldRule(field = "priority", values = {"LOW", "MEDIUM", "HIGH"}, ignoreCase = true)
})
@PostMapping("/orders")
public ResponseEntity<OrderResponse> createOrder(@RequestBody @Valid OrderRequest req) { ... }
```

**Example — length constraints:**

```java
@FieldConstraints({
    @FieldRule(field = "name",        min = 2, max = 100),
    @FieldRule(field = "description", max = 500),
    @FieldRule(field = "tags",        max = 10, message = "No more than 10 tags allowed")
})
@PostMapping("/products")
public ResponseEntity<Product> create(@RequestBody @Valid ProductRequest req) { ... }
```

**Example — both in the same rule:**

```java
@FieldConstraints({
    @FieldRule(field = "status",      values = {"ACTIVE", "INACTIVE"}),
    @FieldRule(field = "name",        min = 2, max = 100),
    @FieldRule(field = "countryCode", values = {"US", "IN", "GB"}, min = 2, max = 2)
})
@PostMapping("/products")
public ResponseEntity<Product> create(@RequestBody @Valid ProductRequest req) { ... }
```

**Example — class-level default with method-level override:**

```java
@RestController
@FieldConstraints({
    @FieldRule(field = "status", values = {"DRAFT", "PUBLISHED"}),
    @FieldRule(field = "title",  min = 1, max = 200)
})
public class ContentController {

    @PostMapping("/articles")
    public ResponseEntity<Article> createArticle(@RequestBody @Valid ArticleRequest req) { ... }

    // Method-level wins — status and title limits differ on this endpoint
    @PutMapping("/articles/{id}")
    @FieldConstraints({
        @FieldRule(field = "status", values = {"DRAFT", "PUBLISHED", "ARCHIVED"}),
        @FieldRule(field = "title",  min = 1, max = 500)
    })
    public ResponseEntity<Article> updateArticle(@PathVariable String id,
                                                 @RequestBody @Valid ArticleRequest req) { ... }
}
```

**Behaviour:**

| Scenario | Result |
|---|---|
| Value in allowed list / length within range | Passes |
| Value not in allowed list | Fails — `400 Bad Request` |
| Length outside `[min, max]` | Fails — `400 Bad Request` |
| Field value is `null` | Passes — pair with `@NotNull` on the DTO to reject null |
| Field name not found on DTO | Warning logged, rule skipped silently |
| Multiple fields or rules fail | All violations reported at once (not fail-fast) |
| `ignoreCase = true` | `"active"`, `"ACTIVE"`, `"Active"` are all treated as equal |
| Only `max` set | `"Field 'x' must not exceed N characters (actual: M)"` |
| Only `min` set | `"Field 'x' must be at least N characters (actual: M)"` |
| Both `min` and `max` set | `"Field 'x' length must be between N and M (actual: K)"` |

---

## Protobuf Request Body Validation

The same `@FieldConstraints` / `@FieldRule` annotations work transparently on
`com.google.protobuf.Message` request bodies. When the aspect detects that the incoming
`@RequestBody` argument is a protobuf `Message`, it switches from reflection to the
protobuf Descriptors API — no annotation changes required.

### Prerequisites

**1. Add `protobuf-java` to your service:**

```groovy
// build.gradle
dependencies {
    implementation 'com.google.protobuf:protobuf-java:3.25.5'
}
```

Spring Boot auto-configures `ProtobufHttpMessageConverter` when `protobuf-java` is on the
classpath, so no additional Spring beans are needed.

**2. Allow `application/x-protobuf` as a Content-Type:**

```properties
# application.properties
uniphore.validation.content-type.allowed-types=application/json,application/x-protobuf
```

### Field Name Convention

Use the field name exactly as it appears in the `.proto` file (snake_case). This is different
from the camelCase getter name that protobuf generates in Java.

```protobuf
// order.proto
message OrderRequest {
  string order_id  = 1;   // ← use "order_id" in @FieldRule, not "orderId"
  string status    = 2;
  Priority priority = 3;
}
```

### Supported Field Types

| Proto type | What `@FieldRule` receives | Notes |
|---|---|---|
| `string` | `String` | Character count for `min`/`max` |
| Numeric scalars (`int32`, `int64`, `float`, `double`, `bool`, …) | Boxed Java type (`Integer`, `Long`, …) | Comparable via `values` as strings |
| `enum` | `String` — the enum constant **name** (e.g. `"ACTIVE"`) | Case-insensitive comparison works via `ignoreCase = true` |
| `message` (nested) | The nested `Message` object | Use dot-notation to reach a child field (e.g. `"address.city"`) |
| `repeated T` | `List<T>` | Element count for `min`/`max` |
| `bytes` | `ByteString` | Byte count for `min`/`max` |

### Null / Unset Field Semantics

| Field type | Unset behaviour |
|---|---|
| Proto3 message field (not set) | Returns `null` — validation rule is skipped |
| Proto3 `optional` scalar (not set) | Returns `null` — validation rule is skipped |
| Proto3 regular scalar (not set) | Returns the proto3 default (`""`, `0`, `false`) — rule runs against the default |
| Proto2 field (not set) | Returns `null` — validation rule is skipped |

For regular proto3 scalars you cannot distinguish "not provided" from "set to the default value".
If you need to enforce a non-empty string, use `min = 1`; for a required integer, add a JSR-303
`@NotNull` on the DTO or use `values` to reject `"0"`.

### Usage Example

**Proto definition (`order.proto`):**

```protobuf
syntax = "proto3";
package com.example.orders;

option java_package = "com.example.orders.proto";
option java_outer_classname = "OrderProto";

enum Priority {
  PRIORITY_UNKNOWN = 0;
  LOW              = 1;
  MEDIUM           = 2;
  HIGH             = 3;
}

message Address {
  string city        = 1;
  string country_code = 2;
}

message OrderRequest {
  string   order_id  = 1;
  string   status    = 2;
  Priority priority  = 3;
  Address  address   = 4;
  repeated string tags = 5;
  bytes    checksum  = 6;
}
```

**Controller (`OrderController.java`):**

```java
import com.example.orders.proto.OrderProto.OrderRequest;
import com.uniphore.platform.validation.annotation.FieldConstraints;
import com.uniphore.platform.validation.annotation.FieldRule;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @PostMapping(
        consumes = "application/x-protobuf",
        produces = "application/x-protobuf"
    )
    @HeaderConstraints({
        @HeaderRule(name = "X-Tenant-ID"),
        @HeaderRule(name = "X-Correlation-ID")
    })
    @FieldConstraints({
        // Allowed-values on a string field
        @FieldRule(field = "status",   values = {"PENDING", "CONFIRMED", "CANCELLED"}),

        // Enum validated by constant name (case-insensitive)
        @FieldRule(field = "priority", values = {"LOW", "MEDIUM", "HIGH"}, ignoreCase = true),

        // Nested message field via dot-notation
        @FieldRule(field = "address.city",         values = {"NYC", "LON", "BLR", "SFO"}),
        @FieldRule(field = "address.country_code", min = 2, max = 2),

        // Repeated field — element count
        @FieldRule(field = "tags",     max = 10, message = "No more than 10 tags allowed"),

        // Bytes field — byte count
        @FieldRule(field = "checksum", min = 16, max = 32)
    })
    public OrderResponse createOrder(@RequestBody OrderRequest request) {
        // request body is fully validated before this method runs
        return orderService.create(request);
    }
}
```

**What happens on a bad request (`priority = PRIORITY_UNKNOWN`, `tags` has 12 elements):**

```json
{
  "timestamp": "2026-04-02T10:23:45.123Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Field validation failed",
  "traceId": "abc-123",
  "tenantId": "tenant-uuid",
  "errors": [
    {
      "field": "priority",
      "message": "Value 'PRIORITY_UNKNOWN' is not allowed. Allowed values: [LOW, MEDIUM, HIGH]"
    },
    {
      "field": "tags",
      "message": "No more than 10 tags allowed"
    }
  ]
}
```

All violations are collected before throwing — the response reports every invalid field at once.

### Mixing JSON and Protobuf Endpoints

`@FieldConstraints` works identically for both POJO and protobuf request bodies. The aspect
detects the type at runtime — no annotation changes are needed per content type. A service that
accepts both formats can apply the same rules:

```java
// Accepts both application/json and application/x-protobuf
@PostMapping(consumes = {"application/json", "application/x-protobuf"})
@FieldConstraints({
    @FieldRule(field = "status", values = {"ACTIVE", "INACTIVE"})
})
public ResponseEntity<Void> handle(@RequestBody OrderRequest request) { ... }
```

The field name `"status"` resolves via:
- **POJO path**: reflection over `OrderRequest.status` (camelCase Java field)
- **Protobuf path**: Descriptors API lookup of `status` (snake_case proto field name)

Ensure both your DTO and your `.proto` file use the same name for shared rules, or declare
separate endpoints with separate annotations when the names diverge.

---

## Error Response Format

All validation failures return a consistent JSON envelope:

```json
{
  "timestamp": "2025-04-01T10:23:45.123Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Required header 'X-Tenant-ID' is missing or blank",
  "traceId": "abc-123",
  "tenantId": "tenant-uuid",
  "errors": [
    {
      "field": "X-Tenant-ID",
      "message": "Required header 'X-Tenant-ID' is missing or blank"
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
| `errors` | Array of per-field or per-header errors |

### HTTP status codes used

| Scenario | Status |
|---|---|
| Missing or malformed `Authorization` header | `401 Unauthorized` |
| Missing or disallowed `Content-Type` | `400 Bad Request` |
| Missing required custom header (properties or `@HeaderConstraints`) | `400 Bad Request` |
| JSR-303 `@Valid` body validation failure | `400 Bad Request` |
| `@FieldConstraints` / `@FieldRule` value violation | `400 Bad Request` |

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
    │   │   │   ├── HeaderConstraints.java      # Per-endpoint header rules container
    │   │   │   ├── HeaderRule.java             # Inline header rule (name, required, notBlank, message)
    │   │   │   ├── SkipValidation.java         # Opt-out all validation on a specific endpoint
    │   │   │   ├── FieldConstraints.java       # Per-endpoint field validation rules container
    │   │   │   └── FieldRule.java              # Inline rule (field, values, ignoreCase, min, max, message)
    │   │   ├── aspect/
    │   │   │   ├── FieldConstraintsAspect.java # AOP aspect enforcing @FieldConstraints rules
    │   │   │   └── ProtoFieldReader.java       # Protobuf Descriptors-based field access (loaded only when protobuf-java is present)
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
            ├── aspect/FieldConstraintsAspectProtoTest.java
            ├── filter/HeaderValidationFilterTest.java
            ├── handler/ValidationExceptionHandlerTest.java
            └── properties/ValidationPropertiesTest.java
```

---

## License

Internal Uniphore library — not for public distribution.

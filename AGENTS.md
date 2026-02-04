# AGENTS.md - Coding Agent Guidelines for ymall-claude

This document provides guidelines for AI coding agents working in this repository.

## Project Overview

- **Project**: ymall-claude (E-commerce/Mall application)
- **Language**: Java 17
- **Build System**: Maven
- **Package Structure**: `com.yuge.*`

## Build Commands

### Full Build
```bash
mvn clean install
```

### Compile Only
```bash
mvn compile
```

### Package (create JAR)
```bash
mvn package
```

### Skip Tests During Build
```bash
mvn install -DskipTests
```

## Test Commands

### Run All Tests
```bash
mvn test
```

### Run a Single Test Class
```bash
mvn test -Dtest=TestClassName
```

### Run a Single Test Method
```bash
mvn test -Dtest=TestClassName#testMethodName
```

### Run Tests in a Specific Package
```bash
mvn test -Dtest="com.yuge.module.**"
```

### Run Tests with Verbose Output
```bash
mvn test -X
```

## Dependency Management

### Add Dependencies
Add dependencies to `pom.xml` in the `<dependencies>` section.

### Update Dependencies
```bash
mvn versions:display-dependency-updates
```

### Dependency Tree
```bash
mvn dependency:tree
```

## Code Style Guidelines

### Package Naming
- Use lowercase letters only
- Base package: `com.yuge`
- Sub-packages by feature: `com.yuge.user`, `com.yuge.product`, `com.yuge.order`

### Class Naming
- **Classes**: PascalCase (e.g., `UserService`, `ProductController`)
- **Interfaces**: PascalCase, no "I" prefix (e.g., `UserRepository`)
- **Abstract classes**: Prefix with `Abstract` (e.g., `AbstractBaseEntity`)
- **Exceptions**: Suffix with `Exception` (e.g., `UserNotFoundException`)
- **DTOs**: Suffix with `DTO` (e.g., `UserDTO`)
- **Entities**: No suffix, plain name (e.g., `User`, `Product`)

### Method Naming
- Use camelCase
- Start with verb: `getUserById()`, `createOrder()`, `validateInput()`
- Boolean methods: `isValid()`, `hasPermission()`, `canAccess()`

### Variable Naming
- Use camelCase for variables and parameters
- Use UPPER_SNAKE_CASE for constants
- Avoid single-letter names except for loop counters

### Import Organization
Order imports as follows (with blank lines between groups):
1. `java.*` - Standard Java imports
2. `javax.*` - Java extensions
3. Third-party  (alphabetically)
4. Project imports (`com.yuge.*`)

Avoid wildcard imports (`import java.util.*`). Import specific classes.

### Formatting
- **Indentation**: 4 spaces (no tabs)
- **Line length**: Maximum 120 characters
- **Braces**: Opening brace on same line (K&R style)
- **Blank lines**: One blank line between methods, two between class sections

### Type Annotations
- Use `@Nullable` and `@NonNull` annotations where appropriate
- Prefer `Optional<T>` for return types that may be absent
- Avoid returning `null` from methods; use `Optional` or throw exceptions

## Error Handling

### Exception Guidelines
- Create custom exceptions extending `RuntimeException` for business logic errors
- Use checked exceptions only for recoverable conditions
- Always include meaningful error messages
- Log exceptions at appropriate levels

### Exception Hierarchy
```
RuntimeException
├── BusinessException (base for all business errors)
│   ├── EntityNotFoundException
│   ├── ValidationException
│   └── AuthorizationException
```

### Try-Catch Best Practices
```java
try {
    // risky operation
} catch (SpecificException e) {
    log.error("Operation failed: {}", e.getMessage(), e);
    throw nsinessException("User-friendly message", e);
}
```

## Logging

- Use SLF4J with appropriate logging framework
- Log levels:
  - `ERROR`: System errors, exceptions
  - `WARN`: Recoverable issues, deprecation warnings
  - `INFO`: Important business events
  - `DEBUG`: Detailed flow information
  - `TRACE`: Very detailed debugging

```java
private static final Logger log = LoggerFactory.getLogger(ClassName.class);
```

## Testing Guidelines

### Test Class Naming
- Unit tests: `ClassNameTest.java`
- Integration tests: `ClassNameIT.java`

### Test Method Naming
Use descriptive names: `shouldReturnUserWhenIdExists()`, `shouldThrowExceptionWhenInputInvalid()`

### Test Structure (AAA Pattern)
```java
@Test
void shouldReturnUserWhenIdExists() {
    // Arrange
    Long userId = 1L;
    User expected = new User(userId, "test");
    
    // Act
    User result = userService.findById(userId);
    
    // Assert
    assertEquals(expected, result);
}
```

### Test Location
- Unit tests: `src/test/java/com/yuge/...`
- Test resources: `src/test/resources/`

## Project Structure

```
ymall-claude/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/yuge/
│   │   │       ├── controller/    # REST controllers
│   │   │       ├── service/       # Business logic
│   │   │       ├── repository/    # Data access
│   │   │       ├── entity/        # JPA entities
│   │   │       ├── dto/           # Data transfer objects
│   │   │       ├── config/        # Configuration classes
│   │   │       ├── exception/     # Custom exceptions
│   │   │       └── util/          # Utility classes
│   │   └── resources/
│   │       └── application.yml    # Configuration
│   └── test/
│       └── java/
│           └── com/yuge/          # Test classes
└── target/                        # Build outputed)
```

## Common Patterns

### Service Layer
```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    
    public User findById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
    }
}
```

### Repository Layer
```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
}
```

## Important Notes for Agents

1. **Always run tests** after making changes: `mvn test`
2. **Check compilation** before committing: `mvn compile`
3. **Follow existing patterns** in the codebase
4. **Add tests** for new functionality
5. **Update documentation** when adding new features
6. **Use meaningful commit messages** following conventional commits

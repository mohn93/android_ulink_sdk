# ULink SDK Testing Guide

This document provides comprehensive information about testing the ULink Android SDK.

## Test Structure

The test suite is organized into several categories:

### 1. Unit Tests (`app/src/test/java/ly/ulink/sdk/`)

#### `ULinkTest.kt`
- Tests core SDK functionality
- Link creation and resolution
- Session management
- Deep link handling
- Installation ID management
- Error handling scenarios

#### `HttpClientTest.kt` (`network/`)
- HTTP client functionality
- GET and POST requests
- Error response handling
- Network failure scenarios
- Request timeout handling
- Custom headers support

#### `DeviceInfoUtilsTest.kt` (`utils/`)
- Device information collection
- Network connectivity detection
- App version retrieval
- Carrier and country detection
- Permission handling

#### `ModelsTest.kt` (`models/`)
- Data model validation
- Serialization/deserialization
- Model equality and hashCode
- Edge cases for model creation

#### `ULinkIntegrationTest.kt` (`integration/`)
- End-to-end workflow testing
- Complete link creation and resolution flow
- Session management workflow
- Error handling in complete workflows
- Concurrent operations

## Running Tests

### Quick Start

```bash
# Run all tests with our custom script
./run_tests.sh
```

### Manual Test Execution

```bash
# Run all unit tests
./gradlew test

# Run specific test class
./gradlew test --tests ULinkTest
./gradlew test --tests HttpClientTest
./gradlew test --tests DeviceInfoUtilsTest
./gradlew test --tests ModelsTest
./gradlew test --tests ULinkIntegrationTest

# Run tests with verbose output
./gradlew test --info --stacktrace

# Run tests and generate coverage report
./gradlew testDebugUnitTestCoverage
```

### Test Categories

```bash
# Run only unit tests
./gradlew testDebugUnitTest

# Run only instrumented tests
./gradlew connectedAndroidTest
```

## Test Dependencies

The test suite uses the following libraries:

- **JUnit 4**: Core testing framework
- **MockK**: Kotlin-friendly mocking library
- **Robolectric**: Android unit testing framework
- **Kotlinx Coroutines Test**: Testing coroutines
- **AndroidX Test**: Android testing utilities

## Test Configuration

### Robolectric Configuration

Robolectric is configured via `app/src/test/resources/robolectric.properties`:

```properties
sdk=28
qualifiers=en-rUS-w320dp-h470dp-normal-notlong-notround-lowdr-nowidecg-port-notnight-mdpi-finger-keysexposed-nokeys-navhidden-nonav
```

### Mock Setup

Tests use MockK for mocking Android dependencies:

```kotlin
// Example mock setup
private lateinit var mockContext: Context
private lateinit var mockSharedPreferences: SharedPreferences

@Before
fun setup() {
    mockContext = mockk(relaxed = true)
    mockSharedPreferences = mockk(relaxed = true)
    
    every { mockContext.getSharedPreferences(any(), any()) } returns mockSharedPreferences
}
```

## Test Coverage

The test suite aims for comprehensive coverage of:

### Core Functionality
- ✅ SDK initialization
- ✅ Link creation (Dynamic and Unified)
- ✅ Link resolution
- ✅ Session management
- ✅ Deep link handling
- ✅ Installation ID management

### Network Layer
- ✅ HTTP GET/POST requests
- ✅ Request/response handling
- ✅ Error scenarios
- ✅ Network failures
- ✅ Timeout handling

### Utilities
- ✅ Device information collection
- ✅ Network connectivity detection
- ✅ Permission handling
- ✅ Error recovery

### Data Models
- ✅ Model creation and validation
- ✅ Serialization/deserialization
- ✅ Edge cases

### Integration Scenarios
- ✅ End-to-end workflows
- ✅ Error propagation
- ✅ Concurrent operations
- ✅ State management

## Writing New Tests

### Test Naming Convention

```kotlin
@Test
fun `test description in backticks with spaces`() {
    // Test implementation
}
```

### Mock Setup Pattern

```kotlin
@Before
fun setup() {
    // Initialize mocks
    mockDependency = mockk(relaxed = true)
    
    // Configure mock behavior
    every { mockDependency.method() } returns expectedValue
    
    // Initialize system under test
    systemUnderTest = SystemUnderTest(mockDependency)
}

@After
fun tearDown() {
    clearAllMocks()
}
```

### Async Testing Pattern

```kotlin
@Test
fun `test async operation`() = runTest {
    // Mock async dependencies
    coEvery { mockService.asyncMethod() } returns expectedResult
    
    // Execute async operation
    val result = systemUnderTest.asyncOperation()
    
    // Verify result
    assertEquals(expectedResult, result)
}
```

## Test Reports

After running tests, reports are available at:

- **Test Results**: `app/build/reports/tests/testDebugUnitTest/index.html`
- **Coverage Report**: `app/build/reports/jacoco/testDebugUnitTestCoverage/html/index.html`

## Continuous Integration

For CI/CD pipelines, use:

```bash
# Run tests with XML output for CI
./gradlew test --continue

# Generate coverage report
./gradlew testDebugUnitTestCoverage
```

## Troubleshooting

### Common Issues

1. **Robolectric SDK Download Issues**
   ```bash
   # Clear Robolectric cache
   rm -rf ~/.robolectric
   ```

2. **MockK Verification Failures**
   ```kotlin
   // Use relaxed mocks for complex objects
   val mock = mockk<ComplexClass>(relaxed = true)
   ```

3. **Coroutine Test Issues**
   ```kotlin
   // Use runTest for coroutine testing
   @Test
   fun `test coroutine`() = runTest {
       // Test implementation
   }
   ```

### Debug Tips

- Use `--info` flag for verbose test output
- Use `--stacktrace` for detailed error information
- Check individual test reports for specific failures
- Verify mock setup matches actual usage patterns

## Best Practices

1. **Test Independence**: Each test should be independent and not rely on other tests
2. **Clear Naming**: Use descriptive test names that explain the scenario
3. **Arrange-Act-Assert**: Structure tests with clear setup, execution, and verification phases
4. **Mock Minimally**: Only mock what's necessary for the test
5. **Test Edge Cases**: Include tests for error conditions and edge cases
6. **Keep Tests Fast**: Unit tests should run quickly
7. **Maintain Tests**: Update tests when code changes

## Contributing

When adding new features:

1. Write tests first (TDD approach)
2. Ensure new tests follow existing patterns
3. Update this documentation if adding new test categories
4. Verify all tests pass before submitting changes
5. Aim for high test coverage on new code
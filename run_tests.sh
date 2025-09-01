#!/bin/bash

# ULink SDK Test Runner
# This script runs all unit tests and generates a comprehensive test report

set -e

echo "🧪 Running ULink SDK Tests..."
echo "================================"

# Clean previous test results
echo "📁 Cleaning previous test results..."
./gradlew clean

# Run unit tests with coverage
echo "🔬 Running unit tests..."
./gradlew test --info

# Check if tests passed
if [ $? -eq 0 ]; then
    echo "✅ All tests passed!"
    echo ""
    echo "📊 Test Results Summary:"
    echo "========================"
    
    # Display test results
    if [ -f "app/build/reports/tests/testDebugUnitTest/index.html" ]; then
        echo "📋 Detailed test report available at:"
        echo "   app/build/reports/tests/testDebugUnitTest/index.html"
    fi
    
    if [ -f "app/build/reports/jacoco/testDebugUnitTestCoverage/html/index.html" ]; then
        echo "📈 Code coverage report available at:"
        echo "   app/build/reports/jacoco/testDebugUnitTestCoverage/html/index.html"
    fi
    
    echo ""
    echo "🎉 SDK is ready for production!"
else
    echo "❌ Some tests failed. Please check the output above."
    exit 1
fi

echo ""
echo "💡 To run specific test classes:"
echo "   ./gradlew test --tests ULinkTest"
echo "   ./gradlew test --tests HttpClientTest"
echo "   ./gradlew test --tests DeviceInfoUtilsTest"
echo "   ./gradlew test --tests ModelsTest"
echo "   ./gradlew test --tests ULinkIntegrationTest"
echo ""
echo "💡 To run tests with verbose output:"
echo "   ./gradlew test --info --stacktrace"
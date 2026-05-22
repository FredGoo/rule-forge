# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

This is a Maven-based Java project. Use these commands for development:

```bash
# Build all modules
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Run specific module tests
mvn test -pl ruleforge-core

# Package for deployment
mvn clean package

# Clean build artifacts
mvn clean
```

## Project Architecture

RuleForge is a Java-based rule engine built on the RETE algorithm. The project is organized as a multi-module Maven project:

### Core Modules

- **ruleforge-parent**: Parent POM with common configuration and dependencies
- **ruleforge-core**: Core rule engine implementation with RETE algorithm
- **ruleforge-api**: Public API interfaces and models
- **ruleforge-console**: Web-based console and management interface
- **ruleforge-dev-console**: Development console for testing and debugging
- **ruleforge-dev-executor-test**: Test execution environment

### Key Components

**Rule Engine Core** (`ruleforge-core/src/main/java/com/ruleforge/`):
- RETE algorithm implementation
- Rule execution engine
- Knowledge base management
- Variable and constant handling

**Console System** (`ruleforge-console/src/main/java/com/ruleforge/`):
- Web-based rule designer
- Repository management for rule storage
- Permission system for rule access control
- Servlet handlers for HTTP requests

### Rule Types Supported

The engine supports multiple rule definition formats:
- Wizard-style rule sets (向导式规则集)
- Script-based rule sets (脚本式规则集)
- Decision tables (决策表)
- Cross decision tables (交叉决策表) - PRO version
- Decision trees (决策树)
- Score cards (评分卡)
- Decision flows (决策流)

### Development Notes

- Uses Spring Framework 4.3.11 for web components
- ANTLR4 for parsing rule definitions
- Jackson for JSON processing
- The project follows Apache-2.0 license

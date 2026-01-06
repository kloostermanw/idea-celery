# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an IntelliJ IDEA plugin (intelli-celery-web-app) that provides PHP code completion features. The plugin is built using the IntelliJ Platform Plugin Template and targets IntelliJ IDEA Ultimate Edition with PHP support.

**Key Feature**: The plugin provides intelligent autocomplete for a specific PHP pattern: `Language::get($item, $category)` method calls. It parses language files (`english-utf-8.php`) from the project to suggest:
- Category names (second parameter)
- Language item keys (first parameter) based on the selected category
- When clicking on the category you should jump to the corresponding item or category in the `english-utf-8.php` file.

## Development Commands

### Building and Testing
```bash
# Run linters (detekt, ktlint) and tests
./gradlew check

# Build the plugin
./gradlew buildPlugin

# Verify plugin compatibility
./gradlew verifyPlugin

# Run the plugin in a development IDE instance
./gradlew runIde

# Run plugin verifier against multiple IDE versions
./gradlew runPluginVerifier
```

### Testing
```bash
# Run all tests
./gradlew test

# Run specific test
./gradlew test --tests "TestClassName"
```

## Architecture

### Package Structure
The codebase uses two package namespaces (this is intentional from the template migration):
- `com.github.kloostermanw.ideacelery` - Kotlin files (services, listeners, bundle)
- `eu.kloosterman.ideacelery` - Java files (completion contributor)

### Core Components

**ToolboxCompletionContributor** (`src/main/java/eu/kloosterman/ideacelery/completion/ToolboxCompletionContributor.java`)
- Main completion logic for PHP code
- Triggered when typing in `Language::get()` method calls
- Searches for `english-utf-8.php` files in `languages` directories
- Parses PHP array structures to extract language categories and items
- Provides context-aware completion based on parameter position

**Plugin Services**
- `MyApplicationService` - Application-level service (singleton)
- `MyProjectService` - Project-level service (one per project)
- `MyProjectManagerListener` - Initializes project service when project opens

**Plugin Metadata**
- `plugin.xml` - Declares plugin dependencies (PHP module required), services, and extension points
- Plugin description is extracted from README.md during build (between `<!-- Plugin description -->` tags)

### Platform Configuration
- **Target IDE**: IntelliJ IDEA Ultimate Edition (`platformType = IU`)
- **IDE Version**: 2020.2.4 with compatibility up to 2021.1.* builds
- **Dependencies**: Requires PHP plugin and IntelliLang
- **Java Version**: Target JVM 1.8
- **Kotlin Version**: 1.5.10

### Language File Structure
The completion contributor expects this PHP file structure:
```php
// english-utf-8.php in a 'languages' directory
$lang = [
    'category_name' => [
        'item_key' => 'Translation value',
        // ...
    ],
    // ...
];
```

## Important Notes

- The plugin is based on the IntelliJ Platform Plugin Template
- CI/CD uses GitHub Actions with workflows for build, test, verify, and release
- Plugin changes are extracted from CHANGELOG.md for releases
- The plugin verifier tests against multiple IDE versions (2020.2.4, 2020.3.4, 2021.1.1)
- Detekt and ktlint are configured for code quality checks

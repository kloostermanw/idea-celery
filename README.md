# Celery PhpStorm Plugin

A PhpStorm plugin that provides intelligent code completion, navigation, and route discovery for [Celery Payroll](https://www.celerypayroll.com/) PHP projects.

<!-- Plugin description -->
PhpStorm plugin for Celery Payroll PHP development. Provides autocomplete for `Language::get()` calls, go-to-definition for language keys, and route search powered by automatic RewriteConstants and View class indexing.
<!-- Plugin description end -->

## Installation

1. Download the latest `.zip` file from the [GitHub Releases](https://github.com/kloostermanw/idea-celery/releases/latest) page
2. Open PhpStorm
3. Go to <kbd>Settings</kbd> > <kbd>Plugins</kbd>
4. Click the <kbd>gear icon</kbd> and select <kbd>Install Plugin from Disk...</kbd>
5. Select the downloaded `.zip` file
6. Restart PhpStorm when prompted

## Features

### Language Autocomplete

Provides intelligent code completion for `Language::get($item, $category)` calls by parsing the `english-utf-8.php` language files in your project.

- **Category completion** (second parameter) — suggests all available category names
- **Item key completion** (first parameter) — suggests item keys within the selected category

### Language Navigation

<kbd>Ctrl+Click</kbd> (or <kbd>Cmd+Click</kbd> on Mac) on `Language::get()` parameters to jump directly to their definitions in `english-utf-8.php`.

- Clicking a category name navigates to that category's array key
- Clicking an item key navigates to the specific item within its category

### Route Search

Access via <kbd>Celery</kbd> > <kbd>Route Search...</kbd> in the menu bar.

Opens an interactive search popup to discover and navigate to application routes. Search by:

- URL pattern
- Class name
- Method name
- Section, subsection, or command slug

Select a route to jump directly to the corresponding action method in your View class.

### Automatic Route Indexing

The plugin automatically discovers and indexes routes by combining:

- **RewriteConstants** parsing — extracts section, subsection, command, and parse type URL slug mappings
- **View class scanning** — discovers classes in `/Views/` directories, their section/subsection assignments, and action methods
- **Constant resolution** — resolves constant references to their values

The index is cached and automatically rebuilt when relevant PHP files change.

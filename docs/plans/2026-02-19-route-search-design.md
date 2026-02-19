# Route Search Feature Design

## Summary

Add a top-level "Celery" menu to PhpStorm's menu bar (between Code and Refactor) with a "Route Search..." action that opens a searchable route list. The plugin discovers routes by parsing `RewriteConstants.php` mapping tables and scanning `App\Views/` class files using IntelliJ's PSI API.

## Menu Bar

- Top-level "Celery" menu, placed between Code and Refactor in the main menu bar
- First item: **Route Search...** - opens the route search dialog

## Route Search Dialog

Uses IntelliJ's ChooseByNamePopup pattern (same UI as "Go to Class"):

- **Search field** at top - accepts URL patterns (`/employees/personal/edit`), class name fragments (`EmployeePersonal`), or section names (`employees`)
- **Results list** showing matching routes with: URL, Class (fully qualified), Method
- **Enter** navigates to the View class file and jumps to the action method
- Fuzzy matching as you type

## Route Discovery Engine

### Step 1: Parse RewriteConstants.php

Find `RewriteConstants.php` (in `src/includes/App/Base/`) and extract 4 arrays via PSI:

- `SECTION_COLLECTION` - URL slug -> internal section name (e.g. `"employees"` -> `"Employee"`)
- `SUB_SECTION_COLLECTION` - URL slug -> internal subsection name
- `CMD_COLLECTION` - URL slug -> internal command name
- `PARSE_COLLECTION` - URL value -> parse type

Build reverse lookup maps (internal name -> URL slug) for URL generation.

### Step 2: Scan View Classes

Find all PHP files in `App/Views/` directories. For each file, extract via PSI:

- Class name (e.g. `EmployeePersonalHtml`)
- `$this->section` assignment (e.g. `RewriteConstants::SECTION_EMPLOYEES`)
- `$this->subsection` assignment (if present)
- All `public function action*()` methods

### Step 3: Build Route Entries

For each View class + action method combination:

1. Get section slug from reverse section map
2. Get subsection slug from reverse subsection map (if applicable)
3. Get command slug from reverse command map (strip "action" prefix from method name)
4. Detect parse type from class name suffix (Html/Ajax/Json/Pdf)
5. Compose URL: `/{section-slug}/{subsection-slug}/{command-slug}`
6. For non-default parse types, append `/view/{type}`

### Caching

Route index is built on first access and cached in the project service. Invalidated when PHP files in `App/Views/` or `RewriteConstants.php` change (via VirtualFileListener).

## Component Structure

```
src/main/java/eu/kloosterman/ideacelery/
  actions/
    RouteSearchAction.java          - Menu action, opens search dialog
  routing/
    RouteEntry.java                 - Data class: url, className, methodName, psiElement ref
    RewriteConstantsParser.java     - Parses RewriteConstants.php into mapping tables
    ViewClassScanner.java           - Scans App/Views/ for class info + action methods
    RouteIndexService.java          - Project service combining parser + scanner, caches results
```

`RouteIndexService` registered as a project-level service in `plugin.xml`.

## Navigation

When the user selects a route and presses Enter:

1. Resolve the `PsiElement` for the target action method (or class if no specific method)
2. Use `NavigationUtil` / `PsiNavigateUtil` to open the file and jump to the method declaration

## Key Design Decisions

- **PSI-based parsing** over regex - consistent with existing plugin code, accurate, supports navigation
- **ChooseByNamePopup** over custom dialog - native IDE feel, less code, familiar UX
- **Project service for caching** - one index per project, lazy initialization
- **Both URL and class search** - users can search by what they know (URL or class name)

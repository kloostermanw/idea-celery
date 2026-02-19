# Route Search Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a "Celery" top-level menu to PhpStorm with a "Route Search" popup that discovers and navigates to routes by parsing RewriteConstants.php and scanning App\Views class files.

**Architecture:** PSI-based parsing of PHP files to build a route index. Custom search popup with text field + filterable list. RouteIndexService (project service) caches routes and invalidates on file changes. Navigation uses PhpIndex to resolve classes and jump to action methods.

**Tech Stack:** Java, IntelliJ Platform SDK 2024.3, JetBrains PHP Plugin PSI API, Swing (JBTextField, JBList, JBPopup)

---

### Task 1: RouteEntry data class

**Files:**
- Create: `src/main/java/eu/kloosterman/ideacelery/routing/RouteEntry.java`

**Step 1: Create RouteEntry.java**

```java
package eu.kloosterman.ideacelery.routing;

import com.intellij.openapi.project.Project;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpClass;

import java.util.Collection;

public class RouteEntry {
    private final String url;
    private final String className;       // Short name e.g. "EmployeePersonalHtml"
    private final String namespace;       // e.g. "App\\Views"
    private final String methodName;      // e.g. "actionEdit"
    private final String sectionSlug;     // e.g. "employees"
    private final String subsectionSlug;  // e.g. "personal" (nullable)
    private final String commandSlug;     // e.g. "edit"
    private final String parseType;       // e.g. "html", "json", "ajax", "pdf"

    public RouteEntry(String url, String className, String namespace,
                      String methodName, String sectionSlug,
                      String subsectionSlug, String commandSlug,
                      String parseType) {
        this.url = url;
        this.className = className;
        this.namespace = namespace;
        this.methodName = methodName;
        this.sectionSlug = sectionSlug;
        this.subsectionSlug = subsectionSlug;
        this.commandSlug = commandSlug;
        this.parseType = parseType;
    }

    public String getUrl() { return url; }
    public String getClassName() { return className; }
    public String getNamespace() { return namespace; }
    public String getMethodName() { return methodName; }
    public String getSectionSlug() { return sectionSlug; }
    public String getSubsectionSlug() { return subsectionSlug; }
    public String getCommandSlug() { return commandSlug; }
    public String getParseType() { return parseType; }

    public String getFqn() {
        return namespace != null ? namespace + "\\" + className : className;
    }

    /**
     * Returns text used for filtering in the search popup.
     * Matches against URL, class name, method name, and slugs.
     */
    public boolean matches(String query) {
        if (query == null || query.isEmpty()) return true;
        String lower = query.toLowerCase();
        return url.toLowerCase().contains(lower)
            || className.toLowerCase().contains(lower)
            || methodName.toLowerCase().contains(lower)
            || (sectionSlug != null && sectionSlug.toLowerCase().contains(lower))
            || (subsectionSlug != null && subsectionSlug.toLowerCase().contains(lower));
    }

    /**
     * Navigate to the action method (or class) in the editor.
     */
    public void navigate(Project project) {
        String fqn = "\\" + getFqn();
        Collection<PhpClass> classes = PhpIndex.getInstance(project).getClassesByFQN(fqn);
        if (classes.isEmpty()) {
            // Fallback: search by short name
            classes = PhpIndex.getInstance(project).getClassesByName(className);
        }
        if (classes.isEmpty()) return;

        PhpClass phpClass = classes.iterator().next();

        if (methodName != null) {
            Method method = phpClass.findMethodByName(methodName);
            if (method != null) {
                method.navigate(true);
                return;
            }
        }
        phpClass.navigate(true);
    }

    @Override
    public String toString() {
        return url + " -> " + className + "::" + methodName + "()";
    }
}
```

**Step 2: Build to verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/eu/kloosterman/ideacelery/routing/RouteEntry.java
git commit -m "feat: add RouteEntry data class for route search"
```

---

### Task 2: RewriteConstantsParser

Parses `RewriteConstants.php` to extract the 4 mapping collections. Uses PSI to read class constants and static array properties.

**Files:**
- Create: `src/main/java/eu/kloosterman/ideacelery/routing/RewriteConstantsParser.java`

**Step 1: Create RewriteConstantsParser.java**

The PHP file structure we're parsing:
```php
class RewriteConstants {
    const SECTION_EMPLOYEES = "Employee";
    // ...30+ constants

    public static $SECTION_COLLECTION = [
        'employees' => self::SECTION_EMPLOYEES,
        'company' => self::SECTION_COMPANY,
        // ...
    ];
    // Similar for SUB_SECTION_COLLECTION, CMD_COLLECTION, PARSE_COLLECTION
}
```

```java
package eu.kloosterman.ideacelery.routing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RewriteConstantsParser {

    private final Map<String, String> sectionMap;        // slug -> internal name
    private final Map<String, String> subSectionMap;     // slug -> internal name
    private final Map<String, String> commandMap;        // slug -> internal name
    private final Map<String, String> parseTypeMap;      // slug -> internal name

    private final Map<String, String> reverseSectionMap;    // internal name -> slug
    private final Map<String, String> reverseSubSectionMap; // internal name -> slug
    private final Map<String, String> reverseCommandMap;    // internal name -> slug

    private RewriteConstantsParser(Map<String, String> sectionMap,
                                   Map<String, String> subSectionMap,
                                   Map<String, String> commandMap,
                                   Map<String, String> parseTypeMap) {
        this.sectionMap = sectionMap;
        this.subSectionMap = subSectionMap;
        this.commandMap = commandMap;
        this.parseTypeMap = parseTypeMap;

        this.reverseSectionMap = reverseMap(sectionMap);
        this.reverseSubSectionMap = reverseMap(subSectionMap);
        this.reverseCommandMap = reverseMap(commandMap);
    }

    private static Map<String, String> reverseMap(Map<String, String> map) {
        Map<String, String> reversed = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!entry.getKey().isEmpty()) {
                reversed.put(entry.getValue(), entry.getKey());
            }
        }
        return reversed;
    }

    public Map<String, String> getSectionMap() { return sectionMap; }
    public Map<String, String> getSubSectionMap() { return subSectionMap; }
    public Map<String, String> getCommandMap() { return commandMap; }
    public Map<String, String> getParseTypeMap() { return parseTypeMap; }
    public Map<String, String> getReverseSectionMap() { return reverseSectionMap; }
    public Map<String, String> getReverseSubSectionMap() { return reverseSubSectionMap; }
    public Map<String, String> getReverseCommandMap() { return reverseCommandMap; }

    /**
     * Parse RewriteConstants.php from the project.
     * Returns null if the file cannot be found or parsed.
     */
    @Nullable
    public static RewriteConstantsParser parse(@NotNull Project project) {
        // Find RewriteConstants.php
        Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(
            "RewriteConstants.php", GlobalSearchScope.projectScope(project)
        );

        PsiManager psiManager = PsiManager.getInstance(project);

        for (VirtualFile vf : files) {
            if (!vf.getPath().contains("RewriteConstants")) continue;

            PsiFile psiFile = psiManager.findFile(vf);
            if (psiFile == null) continue;

            // Find the PhpClass in the file
            PhpClass rewriteClass = findClass(psiFile, "RewriteConstants");
            if (rewriteClass == null) continue;

            // Step 1: Build constant values map (SECTION_EMPLOYEES -> "Employee")
            Map<String, String> constantValues = parseClassConstants(rewriteClass);

            // Step 2: Parse each collection array
            Map<String, String> sections = parseCollectionField(
                rewriteClass, "SECTION_COLLECTION", constantValues);
            Map<String, String> subSections = parseCollectionField(
                rewriteClass, "SUB_SECTION_COLLECTION", constantValues);
            Map<String, String> commands = parseCollectionField(
                rewriteClass, "CMD_COLLECTION", constantValues);
            Map<String, String> parseTypes = parseCollectionField(
                rewriteClass, "PARSE_COLLECTION", constantValues);

            if (!sections.isEmpty()) {
                return new RewriteConstantsParser(sections, subSections, commands, parseTypes);
            }
        }

        return null;
    }

    @Nullable
    private static PhpClass findClass(PsiFile psiFile, String className) {
        Collection<PhpClass> classes = PsiTreeUtil.findChildrenOfType(psiFile, PhpClass.class);
        for (PhpClass phpClass : classes) {
            if (className.equals(phpClass.getName())) {
                return phpClass;
            }
        }
        return null;
    }

    /**
     * Extract all class constant definitions: const NAME = "value"
     * Returns map of constant name -> string value.
     */
    private static Map<String, String> parseClassConstants(PhpClass phpClass) {
        Map<String, String> constants = new HashMap<>();
        for (Field field : phpClass.getOwnFields()) {
            if (field.isConstant()) {
                com.intellij.psi.PsiElement defaultValue = field.getDefaultValue();
                if (defaultValue instanceof StringLiteralExpression) {
                    constants.put(field.getName(),
                        ((StringLiteralExpression) defaultValue).getContents());
                }
            }
        }
        return constants;
    }

    /**
     * Parse a static array property like $SECTION_COLLECTION = ['slug' => self::CONST, ...]
     * Returns map of slug -> internal name.
     */
    private static Map<String, String> parseCollectionField(
            PhpClass phpClass, String fieldName, Map<String, String> constantValues) {
        Map<String, String> result = new LinkedHashMap<>();

        Field field = phpClass.findFieldByName(fieldName, false);
        if (field == null) return result;

        com.intellij.psi.PsiElement defaultValue = field.getDefaultValue();
        if (!(defaultValue instanceof ArrayCreationExpression)) return result;

        ArrayHashElement[] elements = PsiTreeUtil.getChildrenOfType(
            defaultValue, ArrayHashElement.class);
        if (elements == null) return result;

        for (ArrayHashElement element : elements) {
            PhpPsiElement key = element.getKey();
            PhpPsiElement value = element.getValue();

            String slug = null;
            if (key instanceof StringLiteralExpression) {
                slug = ((StringLiteralExpression) key).getContents();
            }

            String internalName = resolveValue(value, constantValues);

            if (slug != null && internalName != null) {
                result.put(slug, internalName);
            }
        }

        return result;
    }

    /**
     * Resolve a PHP expression to a string value.
     * Handles: string literals ("Employee") and self::CONSTANT references.
     */
    @Nullable
    private static String resolveValue(@Nullable PhpPsiElement value,
                                       Map<String, String> constantValues) {
        if (value == null) return null;

        if (value instanceof StringLiteralExpression) {
            return ((StringLiteralExpression) value).getContents();
        }

        // Handle self::CONSTANT_NAME or RewriteConstants::CONSTANT_NAME
        String text = value.getText();
        if (text != null) {
            int colonPos = text.indexOf("::");
            if (colonPos >= 0) {
                String constName = text.substring(colonPos + 2);
                return constantValues.get(constName);
            }
        }

        return null;
    }
}
```

**Step 2: Build to verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/eu/kloosterman/ideacelery/routing/RewriteConstantsParser.java
git commit -m "feat: add RewriteConstantsParser for URL mapping tables"
```

---

### Task 3: ViewClassScanner

Scans `App/Views/` PHP files to extract class names, section/subsection assignments, and action methods.

**Files:**
- Create: `src/main/java/eu/kloosterman/ideacelery/routing/ViewClassScanner.java`

**Step 1: Create ViewClassScanner.java**

Uses a hybrid approach:
- PSI for class name and action method discovery (accurate)
- Regex on file text for `$this->section = RewriteConstants::SECTION_*` patterns (simple and reliable for this strict convention)

```java
package eu.kloosterman.ideacelery.routing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ViewClassScanner {

    private static final Pattern SECTION_PATTERN = Pattern.compile(
        "\\$this->section\\s*=\\s*(?:RewriteConstants|self)::(SECTION_\\w+)"
    );
    private static final Pattern SUBSECTION_PATTERN = Pattern.compile(
        "\\$this->subsection\\s*=\\s*(?:RewriteConstants|self)::(SUB_SECTION_\\w+)"
    );

    public static class ViewClassInfo {
        public final String className;
        public final String namespace;
        public final String sectionConstant;     // e.g. "SECTION_EMPLOYEES"
        public final String subsectionConstant;  // e.g. "SUB_SECTION_PERSONAL" (nullable)
        public final List<String> actionMethods;  // e.g. ["actionEdit", "actionList"]
        public final String parseTypeSuffix;     // e.g. "Html", "Json", "Ajax", "Pdf"

        public ViewClassInfo(String className, String namespace,
                             String sectionConstant, String subsectionConstant,
                             List<String> actionMethods, String parseTypeSuffix) {
            this.className = className;
            this.namespace = namespace;
            this.sectionConstant = sectionConstant;
            this.subsectionConstant = subsectionConstant;
            this.actionMethods = actionMethods;
            this.parseTypeSuffix = parseTypeSuffix;
        }
    }

    /**
     * Scan all PHP files in App/Views/ directories.
     * Returns info about each View class found.
     */
    @NotNull
    public static List<ViewClassInfo> scan(@NotNull Project project) {
        List<ViewClassInfo> results = new ArrayList<>();

        Collection<VirtualFile> phpFiles = FileTypeIndex.getFiles(
            PhpFileType.INSTANCE, GlobalSearchScope.projectScope(project)
        );

        PsiManager psiManager = PsiManager.getInstance(project);

        for (VirtualFile vf : phpFiles) {
            if (!vf.getPath().contains("/Views/")) continue;

            PsiFile psiFile = psiManager.findFile(vf);
            if (psiFile == null) continue;

            ViewClassInfo info = parseViewFile(psiFile);
            if (info != null) {
                results.add(info);
            }
        }

        return results;
    }

    @Nullable
    private static ViewClassInfo parseViewFile(@NotNull PsiFile psiFile) {
        // Find PHP class in file
        Collection<PhpClass> classes = PsiTreeUtil.findChildrenOfType(psiFile, PhpClass.class);
        if (classes.isEmpty()) return null;

        PhpClass phpClass = classes.iterator().next();
        String className = phpClass.getName();
        if (className == null) return null;

        // Get namespace
        String fqn = phpClass.getFQN();
        String namespace = null;
        if (fqn != null) {
            int lastSlash = fqn.lastIndexOf('\\');
            if (lastSlash > 0) {
                namespace = fqn.substring(1, lastSlash); // Strip leading backslash
            }
        }

        // Extract section/subsection from file text using regex
        String fileText = psiFile.getText();
        String sectionConstant = extractPattern(fileText, SECTION_PATTERN);
        String subsectionConstant = extractPattern(fileText, SUBSECTION_PATTERN);

        // Skip files without a section assignment (not a routable View)
        if (sectionConstant == null) return null;

        // Find action methods
        List<String> actionMethods = new ArrayList<>();
        for (Method method : phpClass.getOwnMethods()) {
            String methodName = method.getName();
            if (methodName != null
                    && methodName.startsWith("action")
                    && methodName.length() > 6
                    && method.getAccess().isPublic()) {
                actionMethods.add(methodName);
            }
        }

        // Detect parse type suffix from class name
        String parseTypeSuffix = detectParseTypeSuffix(className);

        return new ViewClassInfo(className, namespace, sectionConstant,
            subsectionConstant, actionMethods, parseTypeSuffix);
    }

    @Nullable
    private static String extractPattern(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Detect parse type suffix from class name.
     * E.g. "EmployeePersonalHtml" -> "Html"
     */
    @NotNull
    private static String detectParseTypeSuffix(String className) {
        if (className.endsWith("Ajax")) return "Ajax";
        if (className.endsWith("Json")) return "Json";
        if (className.endsWith("Pdf")) return "Pdf";
        if (className.endsWith("Html")) return "Html";
        return "Html"; // Default
    }
}
```

**Step 2: Build to verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/eu/kloosterman/ideacelery/routing/ViewClassScanner.java
git commit -m "feat: add ViewClassScanner for App/Views class discovery"
```

---

### Task 4: RouteIndexService

Project service that combines RewriteConstantsParser + ViewClassScanner to build a cached list of RouteEntry objects.

**Files:**
- Create: `src/main/java/eu/kloosterman/ideacelery/routing/RouteIndexService.java`
- Modify: `src/main/resources/META-INF/plugin.xml` (register project service)

**Step 1: Create RouteIndexService.java**

```java
package eu.kloosterman.ideacelery.routing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RouteIndexService {
    private final Project project;
    private List<RouteEntry> cachedRoutes = null;

    public RouteIndexService(@NotNull Project project) {
        this.project = project;

        // Invalidate cache when PHP files change
        VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileListener() {
            @Override
            public void contentsChanged(@NotNull VirtualFileEvent event) {
                if (isRelevantFile(event.getFile().getPath())) {
                    invalidateCache();
                }
            }

            @Override
            public void fileCreated(@NotNull VirtualFileEvent event) {
                if (isRelevantFile(event.getFile().getPath())) {
                    invalidateCache();
                }
            }

            @Override
            public void fileDeleted(@NotNull VirtualFileEvent event) {
                if (isRelevantFile(event.getFile().getPath())) {
                    invalidateCache();
                }
            }
        }, project);
    }

    private boolean isRelevantFile(String path) {
        return path.endsWith(".php")
            && (path.contains("/Views/") || path.contains("RewriteConstants"));
    }

    public void invalidateCache() {
        cachedRoutes = null;
    }

    @NotNull
    public List<RouteEntry> getRoutes() {
        if (cachedRoutes != null) {
            return cachedRoutes;
        }

        cachedRoutes = buildRoutes();
        return cachedRoutes;
    }

    @NotNull
    private List<RouteEntry> buildRoutes() {
        // Step 1: Parse RewriteConstants
        RewriteConstantsParser constants = RewriteConstantsParser.parse(project);
        if (constants == null) {
            return Collections.emptyList();
        }

        // Step 2: Scan View classes
        List<ViewClassScanner.ViewClassInfo> viewClasses = ViewClassScanner.scan(project);

        // Step 3: Build route entries
        List<RouteEntry> routes = new ArrayList<>();

        Map<String, String> constantValues = buildConstantToValueMap(constants);
        Map<String, String> reverseSections = constants.getReverseSectionMap();
        Map<String, String> reverseSubSections = constants.getReverseSubSectionMap();
        Map<String, String> reverseCommands = constants.getReverseCommandMap();

        for (ViewClassScanner.ViewClassInfo viewClass : viewClasses) {
            // Resolve section constant to internal name, then to URL slug
            String sectionInternal = constantValues.get(viewClass.sectionConstant);
            if (sectionInternal == null) continue;

            String sectionSlug = reverseSections.get(sectionInternal);
            if (sectionSlug == null) continue;

            // Resolve subsection (optional)
            String subsectionSlug = null;
            if (viewClass.subsectionConstant != null) {
                String subInternal = constantValues.get(viewClass.subsectionConstant);
                if (subInternal != null) {
                    subsectionSlug = reverseSubSections.get(subInternal);
                }
            }

            // Determine parse type
            String parseType = viewClass.parseTypeSuffix.toLowerCase();

            // Build route for each action method
            for (String actionMethod : viewClass.actionMethods) {
                // Strip "action" prefix to get command name
                String commandName = actionMethod.substring(6); // "actionEdit" -> "Edit"
                String commandSlug = reverseCommands.get(commandName);

                // Build URL
                String url = buildUrl(sectionSlug, subsectionSlug, commandSlug, parseType);

                routes.add(new RouteEntry(
                    url,
                    viewClass.className,
                    viewClass.namespace,
                    actionMethod,
                    sectionSlug,
                    subsectionSlug,
                    commandSlug,
                    parseType
                ));
            }
        }

        // Sort routes alphabetically by URL
        routes.sort((a, b) -> a.getUrl().compareToIgnoreCase(b.getUrl()));

        return routes;
    }

    /**
     * Build a map from constant name (e.g. "SECTION_EMPLOYEES") to internal value
     * (e.g. "Employee") by combining all constant types.
     */
    private Map<String, String> buildConstantToValueMap(RewriteConstantsParser constants) {
        // The constants parser already resolved these when parsing.
        // We need to build a reverse from the forward maps.
        // But the forward maps are slug -> internal name, and we need
        // constant name -> internal name.
        //
        // We need to re-parse the class constants. Let's store them in the parser.
        // For now, re-parse.
        return RewriteConstantsParser.parseConstantValues(project);
    }

    @NotNull
    private String buildUrl(String sectionSlug, String subsectionSlug,
                            String commandSlug, String parseType) {
        StringBuilder url = new StringBuilder("/");
        url.append(sectionSlug);

        if (subsectionSlug != null && !subsectionSlug.isEmpty()) {
            url.append("/").append(subsectionSlug);
        }

        if (commandSlug != null && !commandSlug.isEmpty()) {
            url.append("/").append(commandSlug);
        }

        if (parseType != null && !"html".equals(parseType)) {
            url.append("/view/").append(parseType);
        }

        return url.toString();
    }

    public static RouteIndexService getInstance(@NotNull Project project) {
        return project.getService(RouteIndexService.class);
    }
}
```

**Important:** The `buildConstantToValueMap` method above references `RewriteConstantsParser.parseConstantValues()` which doesn't exist yet. We need to add a public static method to RewriteConstantsParser that exposes the constant name -> value map.

**Step 2: Add `parseConstantValues` to RewriteConstantsParser**

Add this public static method to `RewriteConstantsParser.java`:

```java
/**
 * Parse class constants from RewriteConstants.php.
 * Returns map of constant name -> string value
 * (e.g. "SECTION_EMPLOYEES" -> "Employee").
 */
@NotNull
public static Map<String, String> parseConstantValues(@NotNull Project project) {
    Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(
        "RewriteConstants.php", GlobalSearchScope.projectScope(project)
    );

    PsiManager psiManager = PsiManager.getInstance(project);

    for (VirtualFile vf : files) {
        PsiFile psiFile = psiManager.findFile(vf);
        if (psiFile == null) continue;

        PhpClass rewriteClass = findClass(psiFile, "RewriteConstants");
        if (rewriteClass == null) continue;

        return parseClassConstants(rewriteClass);
    }

    return Collections.emptyMap();
}
```

**Step 3: Register service in plugin.xml**

Add inside the `<extensions>` block:

```xml
<projectService
    serviceImplementation="eu.kloosterman.ideacelery.routing.RouteIndexService"/>
```

**Step 4: Build to verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/java/eu/kloosterman/ideacelery/routing/RouteIndexService.java
git add src/main/java/eu/kloosterman/ideacelery/routing/RewriteConstantsParser.java
git add src/main/resources/META-INF/plugin.xml
git commit -m "feat: add RouteIndexService combining parser and scanner"
```

---

### Task 5: RouteSearchAction + Popup UI

Menu action that opens a custom search popup with a text field and filterable route list.

**Files:**
- Create: `src/main/java/eu/kloosterman/ideacelery/actions/RouteSearchAction.java`

**Step 1: Create RouteSearchAction.java**

```java
package eu.kloosterman.ideacelery.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import eu.kloosterman.ideacelery.routing.RouteEntry;
import eu.kloosterman.ideacelery.routing.RouteIndexService;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.stream.Collectors;

public class RouteSearchAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        List<RouteEntry> allRoutes = RouteIndexService.getInstance(project).getRoutes();
        showSearchPopup(project, allRoutes);
    }

    private void showSearchPopup(@NotNull Project project, @NotNull List<RouteEntry> allRoutes) {
        // Search field
        JBTextField searchField = new JBTextField();
        searchField.getEmptyText().setText("Search routes by URL or class name...");

        // Route list
        CollectionListModel<RouteEntry> listModel = new CollectionListModel<>(allRoutes);
        JBList<RouteEntry> list = new JBList<>(listModel);
        list.setCellRenderer(new RouteListCellRenderer());
        list.setVisibleRowCount(15);

        // Panel layout
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(searchField, BorderLayout.NORTH);
        panel.add(new JBScrollPane(list), BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(700, 400));

        // Create popup
        JBPopup popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, searchField)
            .setTitle("Celery Route Search")
            .setRequestFocus(true)
            .setFocusable(true)
            .setResizable(true)
            .setMovable(true)
            .setDimensionServiceKey(project, "CeleryRouteSearch", false)
            .createPopup();

        // Filter routes as user types
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { filterRoutes(); }
            @Override
            public void removeUpdate(DocumentEvent e) { filterRoutes(); }
            @Override
            public void changedUpdate(DocumentEvent e) { filterRoutes(); }

            private void filterRoutes() {
                String query = searchField.getText();
                List<RouteEntry> filtered = allRoutes.stream()
                    .filter(r -> r.matches(query))
                    .collect(Collectors.toList());
                listModel.replaceAll(filtered);
                if (!filtered.isEmpty()) {
                    list.setSelectedIndex(0);
                }
            }
        });

        // Navigate on Enter in search field
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    RouteEntry selected = list.getSelectedValue();
                    if (selected != null) {
                        popup.closeOk(null);
                        selected.navigate(project);
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    list.requestFocusInWindow();
                    if (list.getSelectedIndex() < 0 && list.getModel().getSize() > 0) {
                        list.setSelectedIndex(0);
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    popup.cancel();
                }
            }
        });

        // Navigate on Enter in list
        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    RouteEntry selected = list.getSelectedValue();
                    if (selected != null) {
                        popup.closeOk(null);
                        selected.navigate(project);
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_UP
                           && list.getSelectedIndex() == 0) {
                    searchField.requestFocusInWindow();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    popup.cancel();
                }
            }
        });

        // Double-click to navigate
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    RouteEntry selected = list.getSelectedValue();
                    if (selected != null) {
                        popup.closeOk(null);
                        selected.navigate(project);
                    }
                }
            }
        });

        // Select first item by default
        if (!allRoutes.isEmpty()) {
            list.setSelectedIndex(0);
        }

        popup.showCenteredInCurrentWindow(project);
    }

    /**
     * Custom cell renderer showing URL, class name, and method on each row.
     */
    private static class RouteListCellRenderer extends ColoredListCellRenderer<RouteEntry> {
        @Override
        protected void customizeCellRenderer(
                @NotNull JList<? extends RouteEntry> list,
                RouteEntry value, int index, boolean selected, boolean hasFocus) {
            if (value == null) return;

            // URL in bold
            append(value.getUrl(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            append("  ", SimpleTextAttributes.REGULAR_ATTRIBUTES);

            // Class name in gray
            append(value.getClassName(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
            append("::", SimpleTextAttributes.GRAYED_ATTRIBUTES);

            // Method name
            append(value.getMethodName() + "()", SimpleTextAttributes.GRAYED_ATTRIBUTES);

            // Parse type badge for non-html
            if (!"html".equals(value.getParseType())) {
                append("  [" + value.getParseType().toUpperCase() + "]",
                    SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
            }
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // Only enable when a project is open
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }
}
```

**Step 2: Build to verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/eu/kloosterman/ideacelery/actions/RouteSearchAction.java
git commit -m "feat: add RouteSearchAction with search popup UI"
```

---

### Task 6: Plugin.xml — Register Menu and Action

Add the Celery top-level menu and Route Search action to plugin.xml.

**Files:**
- Modify: `src/main/resources/META-INF/plugin.xml`

**Step 1: Add actions block to plugin.xml**

Add after the `</extensions>` closing tag and before `<applicationListeners>`:

```xml
<actions>
    <group id="CeleryMainMenu" text="Celery" popup="true">
        <add-to-group group-id="MainMenu" anchor="after" relative-to-action="CodeMenu"/>
        <action id="Celery.RouteSearch"
                class="eu.kloosterman.ideacelery.actions.RouteSearchAction"
                text="Route Search..."
                description="Search routes by URL pattern or class name">
        </action>
    </group>
</actions>
```

The complete plugin.xml should now look like:

```xml
<idea-plugin>
    <id>com.github.kloostermanw.ideacelery</id>
    <name>intelli-celery-web-app</name>
    <vendor>kloostermanw</vendor>

    <depends>com.intellij.modules.platform</depends>
    <depends>com.intellij.modules.lang</depends>
    <depends>com.jetbrains.php</depends>

    <extensions defaultExtensionNs="com.intellij">
        <applicationService serviceImplementation="com.github.kloostermanw.ideacelery.services.CeleryApplicationService"/>
        <projectService serviceImplementation="com.github.kloostermanw.ideacelery.services.CeleryProjectService"/>
        <projectService serviceImplementation="eu.kloosterman.ideacelery.routing.RouteIndexService"/>

        <completion.contributor
                language="PHP"
                order="first"
                implementationClass="eu.kloosterman.ideacelery.completion.CeleryCompletionContributor"
        />

        <psi.referenceContributor
                language="PHP"
                implementation="eu.kloosterman.ideacelery.reference.LanguageReferenceContributor"
        />
    </extensions>

    <actions>
        <group id="CeleryMainMenu" text="Celery" popup="true">
            <add-to-group group-id="MainMenu" anchor="after" relative-to-action="CodeMenu"/>
            <action id="Celery.RouteSearch"
                    class="eu.kloosterman.ideacelery.actions.RouteSearchAction"
                    text="Route Search..."
                    description="Search routes by URL pattern or class name">
            </action>
        </group>
    </actions>

    <applicationListeners>
        <listener class="com.github.kloostermanw.ideacelery.listeners.CeleryProjectManagerListener"
                  topic="com.intellij.openapi.project.ProjectManagerListener"/>
    </applicationListeners>
</idea-plugin>
```

**Step 2: Build full plugin**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL with no errors

**Step 3: Commit**

```bash
git add src/main/resources/META-INF/plugin.xml
git commit -m "feat: register Celery menu bar and Route Search action"
```

---

### Task 7: Integration Verification

Manual verification that the full feature works end-to-end.

**Step 1: Run the development IDE**

Run: `./gradlew runIde`

This launches a development instance of IntelliJ/PhpStorm with the plugin installed.

**Step 2: Verify menu bar**

Expected: A "Celery" menu appears in the main menu bar, positioned between "Code" and "Refactor".

**Step 3: Open a project with RewriteConstants.php and App/Views classes**

Open a project that has:
- `src/includes/App/Base/RewriteConstants.php` with section/subsection/command/parse collections
- `src/includes/App/Views/*.php` with View classes that have `$this->section` assignments and `action*()` methods

**Step 4: Test Route Search**

1. Click Celery > Route Search...
2. Expected: A popup appears with a search field and a list of discovered routes
3. Type a URL fragment like "employees" — list should filter to matching routes
4. Type a class name fragment like "Personal" — list should filter to matching classes
5. Select a route and press Enter — should navigate to the action method in the View class
6. Verify double-click also navigates

**Step 5: Verify empty state**

Open a project without RewriteConstants.php. The popup should open but show an empty list.

**Step 6: Final commit**

If any fixes were needed during testing, commit them:

```bash
git add -A
git commit -m "fix: adjustments from integration testing"
```

---

### Summary of files created/modified

| File | Action |
|------|--------|
| `src/main/java/eu/kloosterman/ideacelery/routing/RouteEntry.java` | Create |
| `src/main/java/eu/kloosterman/ideacelery/routing/RewriteConstantsParser.java` | Create |
| `src/main/java/eu/kloosterman/ideacelery/routing/ViewClassScanner.java` | Create |
| `src/main/java/eu/kloosterman/ideacelery/routing/RouteIndexService.java` | Create |
| `src/main/java/eu/kloosterman/ideacelery/actions/RouteSearchAction.java` | Create |
| `src/main/resources/META-INF/plugin.xml` | Modify |

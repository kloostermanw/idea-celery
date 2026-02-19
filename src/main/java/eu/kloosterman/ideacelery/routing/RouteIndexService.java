package eu.kloosterman.ideacelery.routing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Project-level service that combines {@link RewriteConstantsParser} and
 * {@link ViewClassScanner} to build and cache a list of {@link RouteEntry} objects.
 * <p>
 * The cache is automatically invalidated when PHP files in /Views/ directories
 * or RewriteConstants files are changed, created, or deleted.
 */
public class RouteIndexService {

    private final Project project;
    @Nullable
    private List<RouteEntry> cachedRoutes;

    public RouteIndexService(@NotNull Project project) {
        this.project = project;
        this.cachedRoutes = null;

        project.getMessageBus().connect().subscribe(VirtualFileManager.VFS_CHANGES,
            new com.intellij.openapi.vfs.newvfs.BulkFileListener() {
                @Override
                public void after(@NotNull List<? extends com.intellij.openapi.vfs.newvfs.events.VFileEvent> events) {
                    for (com.intellij.openapi.vfs.newvfs.events.VFileEvent event : events) {
                        if (event.getFile() != null && isRelevantFile(event.getFile().getPath())) {
                            invalidateCache();
                            break;
                        }
                    }
                }
            });
    }

    /**
     * Returns the cached list of routes. If the cache is empty, builds the
     * route list first, caches it, and then returns it.
     */
    @NotNull
    public List<RouteEntry> getRoutes() {
        if (cachedRoutes != null) {
            return cachedRoutes;
        }
        cachedRoutes = buildRoutes();
        return cachedRoutes;
    }

    /**
     * Invalidates the cached route list, forcing a rebuild on next access.
     */
    public void invalidateCache() {
        cachedRoutes = null;
    }

    /**
     * Returns the RouteIndexService instance for the given project.
     */
    @NotNull
    public static RouteIndexService getInstance(@NotNull Project project) {
        return project.getService(RouteIndexService.class);
    }

    /**
     * Builds the full route list by parsing RewriteConstants and scanning View classes,
     * then correlating the data to produce RouteEntry objects.
     */
    @NotNull
    private List<RouteEntry> buildRoutes() {
        // Step 1: Parse RewriteConstants
        RewriteConstantsParser parser = RewriteConstantsParser.parse(project);
        if (parser == null) {
            return Collections.emptyList();
        }

        // Step 2: Get constant values map (needed to resolve constant names to internal values)
        Map<String, String> constantValues = RewriteConstantsParser.parseConstantValues(project);

        // Step 3: Scan View classes
        List<ViewClassScanner.ViewClassInfo> viewClasses = ViewClassScanner.scan(project);

        // Step 4: Get reverse maps from the parser
        Map<String, String> reverseSectionMap = parser.getReverseSectionMap();
        Map<String, String> reverseSubSectionMap = parser.getReverseSubSectionMap();
        Map<String, String> reverseCommandMap = parser.getReverseCommandMap();

        List<RouteEntry> routes = new ArrayList<>();

        // Step 5: For each ViewClassInfo, build route entries
        for (ViewClassScanner.ViewClassInfo viewClass : viewClasses) {
            // 5a: Look up sectionConstant in constantValues to get internal section name
            String internalSectionName = constantValues.get(viewClass.sectionConstant);
            if (internalSectionName == null) {
                continue;
            }

            // 5b: Look up internal name in reverseSectionMap to get URL slug
            String sectionSlug = reverseSectionMap.get(internalSectionName);
            if (sectionSlug == null) {
                continue;
            }

            // 5c: Same for subsection (if present)
            String subsectionSlug = null;
            if (viewClass.subsectionConstant != null) {
                String internalSubsectionName = constantValues.get(viewClass.subsectionConstant);
                if (internalSubsectionName != null) {
                    subsectionSlug = reverseSubSectionMap.get(internalSubsectionName);
                }
            }

            // 5d: For each action method in the view class
            for (String actionMethod : viewClass.actionMethods) {
                // Strip "action" prefix to get command name
                String commandName = actionMethod.substring("action".length());

                // Look up command name in reverseCommandMap to get slug
                String commandSlug = reverseCommandMap.get(commandName);

                // Build URL
                String parseType = viewClass.parseTypeSuffix;
                String url = buildUrl(sectionSlug, subsectionSlug, commandSlug, parseType);

                // Create RouteEntry
                RouteEntry entry = new RouteEntry(
                    url,
                    viewClass.className,
                    viewClass.namespace,
                    actionMethod,
                    sectionSlug,
                    subsectionSlug,
                    commandSlug,
                    parseType
                );

                routes.add(entry);
            }
        }

        // Step 6: Sort routes by URL (case-insensitive)
        routes.sort((a, b) -> a.getUrl().compareToIgnoreCase(b.getUrl()));

        return routes;
    }

    /**
     * Builds a URL string from the route segments.
     * <p>
     * Format: /section[/subsection][/command][/view/parseType]
     * The parseType suffix is only appended when it is not "html" (case-insensitive).
     */
    @NotNull
    private String buildUrl(
        @NotNull String sectionSlug,
        @Nullable String subsectionSlug,
        @Nullable String commandSlug,
        @NotNull String parseType
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("/").append(sectionSlug);

        if (subsectionSlug != null && !subsectionSlug.isEmpty()) {
            sb.append("/").append(subsectionSlug);
        }

        if (commandSlug != null && !commandSlug.isEmpty()) {
            sb.append("/").append(commandSlug);
        }

        if (!"html".equalsIgnoreCase(parseType)) {
            sb.append("/view/").append(parseType);
        }

        return sb.toString();
    }

    /**
     * Returns true if the given file path is relevant for route indexing.
     * A file is relevant if it is a PHP file that is either in a /Views/ directory
     * or is a RewriteConstants file.
     */
    private boolean isRelevantFile(@NotNull String path) {
        return path.endsWith(".php") && (path.contains("/Views/") || path.contains("RewriteConstants"));
    }
}

package eu.kloosterman.ideacelery.routing;

import com.intellij.openapi.project.Project;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Data class representing a single discovered route in the Celery PHP framework.
 * Holds all the information needed to display, search, and navigate to a route target.
 */
public class RouteEntry {

    private final String url;
    private final String className;
    private final String namespace;
    private final String methodName;
    private final String sectionSlug;
    private final String subsectionSlug;
    private final String commandSlug;
    private final String parseType;

    public RouteEntry(
        @NotNull String url,
        @NotNull String className,
        @Nullable String namespace,
        @NotNull String methodName,
        @NotNull String sectionSlug,
        @Nullable String subsectionSlug,
        @Nullable String commandSlug,
        @NotNull String parseType
    ) {
        this.url = url;
        this.className = className;
        this.namespace = namespace;
        this.methodName = methodName;
        this.sectionSlug = sectionSlug;
        this.subsectionSlug = subsectionSlug;
        this.commandSlug = commandSlug;
        this.parseType = parseType;
    }

    @NotNull
    public String getUrl() {
        return url;
    }

    @NotNull
    public String getClassName() {
        return className;
    }

    @Nullable
    public String getNamespace() {
        return namespace;
    }

    @NotNull
    public String getMethodName() {
        return methodName;
    }

    @NotNull
    public String getSectionSlug() {
        return sectionSlug;
    }

    @Nullable
    public String getSubsectionSlug() {
        return subsectionSlug;
    }

    @Nullable
    public String getCommandSlug() {
        return commandSlug;
    }

    @NotNull
    public String getParseType() {
        return parseType;
    }

    /**
     * Returns the fully qualified class name.
     * If a namespace is set, returns "namespace\\className"; otherwise just className.
     */
    @NotNull
    public String getFqn() {
        if (namespace != null && !namespace.isEmpty()) {
            String fqn = namespace + "\\" + className;
            return fqn.startsWith("\\") ? fqn : "\\" + fqn;
        }
        return className;
    }

    /**
     * Returns true if the query matches any of: url, className, methodName,
     * sectionSlug, subsectionSlug, or commandSlug (case-insensitive contains check).
     * <p>
     * If the query looks like a full URL (contains "://"), the domain and any
     * leading numeric department/session segment are stripped before matching.
     */
    public boolean matches(@NotNull String query) {
        String normalizedQuery = normalizeQuery(query).toLowerCase();
        if (url.toLowerCase().contains(normalizedQuery)) {
            return true;
        }
        if (className.toLowerCase().contains(normalizedQuery)) {
            return true;
        }
        if (methodName.toLowerCase().contains(normalizedQuery)) {
            return true;
        }
        if (sectionSlug.toLowerCase().contains(normalizedQuery)) {
            return true;
        }
        if (subsectionSlug != null && subsectionSlug.toLowerCase().contains(normalizedQuery)) {
            return true;
        }
        if (commandSlug != null && commandSlug.toLowerCase().contains(normalizedQuery)) {
            return true;
        }
        return false;
    }

    /**
     * Normalizes a search query by stripping protocol/domain from full URLs
     * and removing leading numeric path segments (department/session IDs).
     * <p>
     * For example: "https://pg.celery.loc/35346486.91069296/dashboard/list"
     * becomes "/dashboard/list".
     */
    @NotNull
    private static String normalizeQuery(@NotNull String query) {
        String normalized = query.trim();

        // Strip protocol and domain
        if (normalized.contains("://")) {
            int pathStart = normalized.indexOf('/', normalized.indexOf("://") + 3);
            if (pathStart >= 0) {
                normalized = normalized.substring(pathStart);
            } else {
                return normalized;
            }
        }

        // Strip leading segment that looks like a numeric ID (e.g. /35346486.91069296/)
        if (normalized.startsWith("/")) {
            String withoutLeading = normalized.substring(1);
            int nextSlash = withoutLeading.indexOf('/');
            if (nextSlash > 0) {
                String firstSegment = withoutLeading.substring(0, nextSlash);
                if (firstSegment.matches("[0-9]+(\\.[0-9]+)*")) {
                    normalized = withoutLeading.substring(nextSlash);
                }
            }
        }

        return normalized;
    }

    /**
     * Navigate to the action method in the IDE. Uses PhpIndex to find the class
     * by FQN, then navigates to the action method. Falls back to class navigation
     * if the method is not found, and falls back to class-by-short-name if FQN
     * lookup fails.
     */
    public void navigate(@NotNull Project project) {
        PhpIndex phpIndex = PhpIndex.getInstance(project);
        String fqn = getFqn();

        // Try FQN lookup first
        Collection<PhpClass> classes = phpIndex.getClassesByFQN(fqn);

        // Fall back to short name lookup if FQN yields no results
        if (classes.isEmpty()) {
            classes = phpIndex.getClassesByName(className);
        }

        if (classes.isEmpty()) {
            return;
        }

        PhpClass phpClass = classes.iterator().next();

        // Try to find and navigate to the specific method
        Method method = phpClass.findMethodByName(methodName);
        if (method != null) {
            method.navigate(true);
            return;
        }

        // Fall back to navigating to the class itself
        phpClass.navigate(true);
    }

    @Override
    public String toString() {
        return url + " -> " + className + "::" + methodName + "()";
    }
}

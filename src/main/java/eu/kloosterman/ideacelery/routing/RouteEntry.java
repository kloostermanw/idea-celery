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
     */
    public boolean matches(@NotNull String query) {
        String lowerQuery = query.toLowerCase();
        if (url.toLowerCase().contains(lowerQuery)) {
            return true;
        }
        if (className.toLowerCase().contains(lowerQuery)) {
            return true;
        }
        if (methodName.toLowerCase().contains(lowerQuery)) {
            return true;
        }
        if (sectionSlug.toLowerCase().contains(lowerQuery)) {
            return true;
        }
        if (subsectionSlug != null && subsectionSlug.toLowerCase().contains(lowerQuery)) {
            return true;
        }
        if (commandSlug != null && commandSlug.toLowerCase().contains(lowerQuery)) {
            return true;
        }
        return false;
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

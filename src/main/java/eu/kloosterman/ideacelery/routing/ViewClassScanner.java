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

/**
 * Scans PHP files in the project's App/Views/ directories to discover View classes,
 * their section/subsection assignments, and action methods.
 */
public class ViewClassScanner {

    private static final Pattern SECTION_PATTERN =
        Pattern.compile("\\$this->section\\s*=\\s*(?:RewriteConstants|self)::(SECTION_\\w+)");

    private static final Pattern SUBSECTION_PATTERN =
        Pattern.compile("\\$this->subsection\\s*=\\s*(?:RewriteConstants|self)::(SUB_SECTION_\\w+)");

    /**
     * Data class holding all discovered information about a single View class.
     */
    public static class ViewClassInfo {
        public final String className;
        @Nullable
        public final String namespace;
        public final String sectionConstant;
        @Nullable
        public final String subsectionConstant;
        public final List<String> actionMethods;
        public final String parseTypeSuffix;

        public ViewClassInfo(
            @NotNull String className,
            @Nullable String namespace,
            @NotNull String sectionConstant,
            @Nullable String subsectionConstant,
            @NotNull List<String> actionMethods,
            @NotNull String parseTypeSuffix
        ) {
            this.className = className;
            this.namespace = namespace;
            this.sectionConstant = sectionConstant;
            this.subsectionConstant = subsectionConstant;
            this.actionMethods = actionMethods;
            this.parseTypeSuffix = parseTypeSuffix;
        }
    }

    /**
     * Scans all PHP files under /Views/ directories in the project.
     * For each file, parses the View class to extract section, subsection,
     * action methods, and parse type suffix.
     *
     * @param project the IntelliJ project to scan
     * @return list of discovered ViewClassInfo entries
     */
    @NotNull
    public static List<ViewClassInfo> scan(@NotNull Project project) {
        List<ViewClassInfo> results = new ArrayList<>();

        Collection<VirtualFile> phpFiles = FileTypeIndex.getFiles(
            PhpFileType.INSTANCE,
            GlobalSearchScope.projectScope(project)
        );

        PsiManager psiManager = PsiManager.getInstance(project);

        for (VirtualFile vf : phpFiles) {
            String path = vf.getPath();
            if (!path.contains("/Views/")) {
                continue;
            }

            PsiFile psiFile = psiManager.findFile(vf);
            if (psiFile == null) {
                continue;
            }

            ViewClassInfo info = parseViewFile(psiFile);
            if (info != null) {
                results.add(info);
            }
        }

        return results;
    }

    /**
     * Parses a single PHP file to extract View class information.
     * Returns null if the file does not contain a class with a section assignment.
     */
    @Nullable
    private static ViewClassInfo parseViewFile(@NotNull PsiFile psiFile) {
        Collection<PhpClass> classes = PsiTreeUtil.findChildrenOfType(psiFile, PhpClass.class);
        if (classes.isEmpty()) {
            return null;
        }

        PhpClass phpClass = classes.iterator().next();
        String className = phpClass.getName();
        if (className == null || className.isEmpty()) {
            return null;
        }

        // Extract namespace from FQN
        String fqn = phpClass.getFQN();
        String namespace = null;
        if (fqn != null && fqn.contains("\\")) {
            // FQN is like \App\Views\ClassName - strip leading backslash and class name
            String withoutLeading = fqn.startsWith("\\") ? fqn.substring(1) : fqn;
            int lastBackslash = withoutLeading.lastIndexOf('\\');
            if (lastBackslash > 0) {
                namespace = withoutLeading.substring(0, lastBackslash);
            }
        }

        // Extract section and subsection from file text using regex
        String fileText = psiFile.getText();

        String sectionConstant = extractPattern(fileText, SECTION_PATTERN);
        if (sectionConstant == null) {
            // Skip files without a section assignment
            return null;
        }

        String subsectionConstant = extractPattern(fileText, SUBSECTION_PATTERN);

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

        return new ViewClassInfo(
            className,
            namespace,
            sectionConstant,
            subsectionConstant,
            actionMethods,
            parseTypeSuffix
        );
    }

    /**
     * Runs the given pattern against the text and returns the first capture group,
     * or null if no match is found.
     */
    @Nullable
    private static String extractPattern(@NotNull String text, @NotNull Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Detects the parse type suffix from the class name.
     * Checks for "Ajax", "Json", "Pdf", "Html" suffixes in that order.
     * Defaults to "Html" if none match.
     */
    @NotNull
    private static String detectParseTypeSuffix(@NotNull String className) {
        if (className.endsWith("Ajax")) {
            return "Ajax";
        }
        if (className.endsWith("Json")) {
            return "Json";
        }
        if (className.endsWith("Pdf")) {
            return "Pdf";
        }
        if (className.endsWith("Html")) {
            return "Html";
        }
        return "Html";
    }
}

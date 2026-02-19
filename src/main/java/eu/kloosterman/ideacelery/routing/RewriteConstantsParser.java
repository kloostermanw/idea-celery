package eu.kloosterman.ideacelery.routing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses RewriteConstants.php to extract URL routing mapping tables.
 * <p>
 * The PHP file defines class constants and collection arrays that map URL slugs
 * to internal names for sections, sub-sections, commands, and parse types.
 */
public class RewriteConstantsParser {

    private final Map<String, String> sectionMap;
    private final Map<String, String> subSectionMap;
    private final Map<String, String> commandMap;
    private final Map<String, String> parseTypeMap;
    private final Map<String, String> reverseSectionMap;
    private final Map<String, String> reverseSubSectionMap;
    private final Map<String, String> reverseCommandMap;

    private RewriteConstantsParser(
        @NotNull Map<String, String> sectionMap,
        @NotNull Map<String, String> subSectionMap,
        @NotNull Map<String, String> commandMap,
        @NotNull Map<String, String> parseTypeMap
    ) {
        this.sectionMap = Collections.unmodifiableMap(sectionMap);
        this.subSectionMap = Collections.unmodifiableMap(subSectionMap);
        this.commandMap = Collections.unmodifiableMap(commandMap);
        this.parseTypeMap = Collections.unmodifiableMap(parseTypeMap);
        this.reverseSectionMap = Collections.unmodifiableMap(reverseMap(sectionMap));
        this.reverseSubSectionMap = Collections.unmodifiableMap(reverseMap(subSectionMap));
        this.reverseCommandMap = Collections.unmodifiableMap(reverseMap(commandMap));
    }

    // --- Getters ---

    @NotNull
    public Map<String, String> getSectionMap() {
        return sectionMap;
    }

    @NotNull
    public Map<String, String> getSubSectionMap() {
        return subSectionMap;
    }

    @NotNull
    public Map<String, String> getCommandMap() {
        return commandMap;
    }

    @NotNull
    public Map<String, String> getParseTypeMap() {
        return parseTypeMap;
    }

    @NotNull
    public Map<String, String> getReverseSectionMap() {
        return reverseSectionMap;
    }

    @NotNull
    public Map<String, String> getReverseSubSectionMap() {
        return reverseSubSectionMap;
    }

    @NotNull
    public Map<String, String> getReverseCommandMap() {
        return reverseCommandMap;
    }

    // --- Static factory methods ---

    /**
     * Parses RewriteConstants.php from the project and returns a populated parser instance,
     * or null if the file or class cannot be found.
     */
    @Nullable
    public static RewriteConstantsParser parse(@NotNull Project project) {
        Collection<VirtualFile> virtualFiles = FilenameIndex.getVirtualFilesByName(
            "RewriteConstants.php",
            GlobalSearchScope.projectScope(project)
        );

        if (virtualFiles.isEmpty()) {
            return null;
        }

        PsiManager psiManager = PsiManager.getInstance(project);

        for (VirtualFile vf : virtualFiles) {
            PsiFile psiFile = psiManager.findFile(vf);
            if (psiFile == null) {
                continue;
            }

            PhpClass phpClass = findClass(psiFile, "RewriteConstants");
            if (phpClass == null) {
                continue;
            }

            Map<String, String> constantValues = parseClassConstants(phpClass);

            Map<String, String> sectionMap = parseCollectionField(phpClass, "SECTION_COLLECTION", constantValues);
            Map<String, String> subSectionMap = parseCollectionField(phpClass, "SUB_SECTION_COLLECTION", constantValues);
            Map<String, String> commandMap = parseCollectionField(phpClass, "CMD_COLLECTION", constantValues);
            Map<String, String> parseTypeMap = parseCollectionField(phpClass, "PARSE_COLLECTION", constantValues);

            return new RewriteConstantsParser(sectionMap, subSectionMap, commandMap, parseTypeMap);
        }

        return null;
    }

    /**
     * Convenience method that parses the class constants from RewriteConstants.php
     * and returns the constant name to string value map.
     *
     * @return map of constant names to their string values, or an empty map if not found
     */
    @NotNull
    public static Map<String, String> parseConstantValues(@NotNull Project project) {
        Collection<VirtualFile> virtualFiles = FilenameIndex.getVirtualFilesByName(
            "RewriteConstants.php",
            GlobalSearchScope.projectScope(project)
        );

        if (virtualFiles.isEmpty()) {
            return Collections.emptyMap();
        }

        PsiManager psiManager = PsiManager.getInstance(project);

        for (VirtualFile vf : virtualFiles) {
            PsiFile psiFile = psiManager.findFile(vf);
            if (psiFile == null) {
                continue;
            }

            PhpClass phpClass = findClass(psiFile, "RewriteConstants");
            if (phpClass == null) {
                continue;
            }

            return parseClassConstants(phpClass);
        }

        return Collections.emptyMap();
    }

    // --- Private helpers ---

    /**
     * Finds a PhpClass by name within a PSI file.
     */
    @Nullable
    private static PhpClass findClass(@NotNull PsiFile psiFile, @NotNull String className) {
        Collection<PhpClass> classes = PsiTreeUtil.findChildrenOfType(psiFile, PhpClass.class);
        for (PhpClass phpClass : classes) {
            if (className.equals(phpClass.getName())) {
                return phpClass;
            }
        }
        return null;
    }

    /**
     * Iterates all fields in the PHP class, filters for constants, and extracts
     * string literal default values into a name-to-value map.
     */
    @NotNull
    private static Map<String, String> parseClassConstants(@NotNull PhpClass phpClass) {
        Map<String, String> constants = new HashMap<>();

        for (Field field : phpClass.getOwnFields()) {
            if (!field.isConstant()) {
                continue;
            }

            PsiElement defaultValue = field.getDefaultValue();
            if (defaultValue instanceof StringLiteralExpression) {
                String name = field.getName();
                String value = ((StringLiteralExpression) defaultValue).getContents();
                constants.put(name, value);
            }
        }

        return constants;
    }

    /**
     * Parses a class constant collection (e.g. SECTION_COLLECTION) from the PHP class.
     * Extracts the ArrayCreationExpression default value and resolves each entry's
     * key (string) and value (string literal or self::CONSTANT reference).
     * <p>
     * Uses iteration over getOwnFields() filtered by isConstant() because
     * findFieldByName() only finds properties, not class constants.
     */
    @NotNull
    private static Map<String, String> parseCollectionField(
        @NotNull PhpClass phpClass,
        @NotNull String fieldName,
        @NotNull Map<String, String> constantValues
    ) {
        Map<String, String> result = new HashMap<>();

        Field field = null;
        for (Field f : phpClass.getOwnFields()) {
            if (f.isConstant() && fieldName.equals(f.getName())) {
                field = f;
                break;
            }
        }
        if (field == null) {
            return result;
        }

        PsiElement defaultValue = field.getDefaultValue();
        if (!(defaultValue instanceof ArrayCreationExpression)) {
            return result;
        }

        ArrayCreationExpression array = (ArrayCreationExpression) defaultValue;
        PsiElement[] children = array.getChildren();

        for (PsiElement child : children) {
            if (!(child instanceof ArrayHashElement)) {
                continue;
            }

            ArrayHashElement hashElement = (ArrayHashElement) child;
            PhpPsiElement key = hashElement.getKey();
            PhpPsiElement value = hashElement.getValue();

            if (!(key instanceof StringLiteralExpression)) {
                continue;
            }

            String keyString = ((StringLiteralExpression) key).getContents();
            String valueString = resolveValue(value, constantValues);

            if (valueString != null) {
                result.put(keyString, valueString);
            }
        }

        return result;
    }

    /**
     * Resolves a PHP PSI element to a string value. Handles:
     * <ul>
     *   <li>StringLiteralExpression - returns the string contents directly</li>
     *   <li>self::CONSTANT_NAME or ClassName::CONSTANT_NAME references - strips the
     *       prefix and looks up the constant in the provided map</li>
     *   <li>null PSI elements (e.g. "'' => null" entries) - returns null</li>
     * </ul>
     */
    @Nullable
    private static String resolveValue(@Nullable PhpPsiElement element, @NotNull Map<String, String> constantValues) {
        if (element == null) {
            return null;
        }

        if (element instanceof StringLiteralExpression) {
            return ((StringLiteralExpression) element).getContents();
        }

        // Handle self::CONSTANT_NAME or ClassName::CONSTANT_NAME references
        String text = element.getText();
        if (text != null && text.contains("::")) {
            String constantName = text.substring(text.indexOf("::") + 2);
            return constantValues.get(constantName);
        }

        return null;
    }

    /**
     * Creates a reversed copy of the given map (value becomes key, key becomes value).
     * Entries with empty-string keys are skipped.
     */
    @NotNull
    private static Map<String, String> reverseMap(@NotNull Map<String, String> map) {
        Map<String, String> reversed = new HashMap<>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getKey().isEmpty()) {
                continue;
            }
            reversed.put(entry.getValue(), entry.getKey());
        }
        return reversed;
    }
}

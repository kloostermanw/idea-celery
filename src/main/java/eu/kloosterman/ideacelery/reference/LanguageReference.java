package eu.kloosterman.ideacelery.reference;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reference implementation that resolves Language::get() parameters to their
 * corresponding definitions in english-utf-8.php files.
 */
public class LanguageReference extends PsiReferenceBase<StringLiteralExpression> {

    private final int parameterIndex; // 0 = item, 1 = category
    private final String category; // Category context for item resolution

    public LanguageReference(@NotNull StringLiteralExpression element, int parameterIndex, @Nullable String category) {
        super(element, TextRange.from(1, element.getContents().length())); // Exclude quotes
        this.parameterIndex = parameterIndex;
        this.category = category;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        String value = myElement.getContents();
        if (value.isEmpty()) {
            return null;
        }

        Project project = myElement.getProject();
        PsiFile[] files = FilenameIndex.getFilesByName(
            project,
            "english-utf-8.php",
            GlobalSearchScope.allScope(project)
        );

        for (PsiFile file : files) {
            PsiDirectory directory = file.getContainingDirectory();
            if (directory == null || !directory.getName().equals("languages")) {
                continue;
            }

            // Parse the language file structure: $lang = [ ... ]
            ArrayHashElement[] topLevelElements = getArrayHashElements(file);
            if (topLevelElements == null) {
                continue;
            }

            if (parameterIndex == 1) {
                // Resolving category (second parameter)
                PsiElement categoryElement = findCategoryKey(topLevelElements, value);
                if (categoryElement != null) {
                    return categoryElement;
                }
            } else if (parameterIndex == 0 && category != null) {
                // Resolving item (first parameter) within a category
                PsiElement itemElement = findItemKey(topLevelElements, category, value);
                if (itemElement != null) {
                    return itemElement;
                }
            }
        }

        return null;
    }

    /**
     * Find the key element for a category in the top-level array.
     */
    @Nullable
    private PsiElement findCategoryKey(ArrayHashElement[] elements, String categoryName) {
        for (ArrayHashElement element : elements) {
            PhpPsiElement key = element.getKey();
            if (key instanceof StringLiteralExpression) {
                String content = ((StringLiteralExpression) key).getContents();
                if (categoryName.equals(content)) {
                    return key; // Return the key itself for navigation
                }
            }
        }
        return null;
    }

    /**
     * Find the key element for an item within a specific category.
     */
    @Nullable
    private PsiElement findItemKey(ArrayHashElement[] elements, String categoryName, String itemName) {
        for (ArrayHashElement element : elements) {
            PhpPsiElement key = element.getKey();
            if (key instanceof StringLiteralExpression) {
                String content = ((StringLiteralExpression) key).getContents();
                if (categoryName.equals(content)) {
                    // Found the category, now search for the item
                    PhpPsiElement value = element.getValue();
                    if (value instanceof ArrayCreationExpression) {
                        PsiElement[] children = value.getChildren();
                        for (PsiElement child : children) {
                            if (child instanceof ArrayHashElement) {
                                PsiElement itemKey = ((ArrayHashElement) child).getKey();
                                if (itemKey instanceof StringLiteralExpression) {
                                    String itemContent = ((StringLiteralExpression) itemKey).getContents();
                                    if (itemName.equals(itemContent)) {
                                        return itemKey; // Return the item key for navigation
                                    }
                                }
                            }
                        }
                    }
                    break;
                }
            }
        }
        return null;
    }

    /**
     * Extract the top-level array hash elements from the language file.
     * Expects structure: $lang = [ 'category' => [...], ... ];
     */
    @Nullable
    private ArrayHashElement[] getArrayHashElements(PsiFile psiFile) {
        GroupStatement groupStatement = PsiTreeUtil.getChildOfType(psiFile, GroupStatement.class);
        if (groupStatement == null) {
            return null;
        }

        Statement statement = PsiTreeUtil.getChildOfType(groupStatement, Statement.class);
        if (statement == null) {
            return null;
        }

        AssignmentExpression assignmentExpression = PsiTreeUtil.getChildOfType(statement, AssignmentExpression.class);
        if (assignmentExpression == null) {
            return null;
        }

        ArrayCreationExpression arrayCreationExpression = PsiTreeUtil.getChildOfType(assignmentExpression, ArrayCreationExpression.class);
        if (arrayCreationExpression == null) {
            return null;
        }

        return PsiTreeUtil.getChildrenOfType(arrayCreationExpression, ArrayHashElement.class);
    }
}

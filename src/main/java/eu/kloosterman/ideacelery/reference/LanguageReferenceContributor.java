package eu.kloosterman.ideacelery.reference;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import com.jetbrains.php.lang.psi.elements.MethodReference;
import com.jetbrains.php.lang.psi.elements.ParameterList;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import com.jetbrains.php.lang.psi.elements.ClassReference;
import org.jetbrains.annotations.NotNull;

/**
 * Provides references for Language::get() method parameters to enable navigation
 * to the corresponding items in english-utf-8.php files.
 */
public class LanguageReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(StringLiteralExpression.class),
            new PsiReferenceProvider() {
                @NotNull
                @Override
                public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                    if (!(element instanceof StringLiteralExpression)) {
                        return PsiReference.EMPTY_ARRAY;
                    }

                    StringLiteralExpression literal = (StringLiteralExpression) element;

                    // Check if we're in a Language::get() call
                    PsiElement parent = literal.getParent();
                    if (!(parent instanceof ParameterList)) {
                        return PsiReference.EMPTY_ARRAY;
                    }

                    ParameterList parameterList = (ParameterList) parent;
                    PsiElement methodCall = parameterList.getParent();

                    if (!(methodCall instanceof MethodReference)) {
                        return PsiReference.EMPTY_ARRAY;
                    }

                    MethodReference method = (MethodReference) methodCall;

                    // Check if it's Language::get()
                    if (!"get".equals(method.getName())) {
                        return PsiReference.EMPTY_ARRAY;
                    }

                    PsiElement classRef = method.getClassReference();
                    if (!(classRef instanceof ClassReference)) {
                        return PsiReference.EMPTY_ARRAY;
                    }

                    ClassReference cls = (ClassReference) classRef;
                    if (!"Language".equals(cls.getName())) {
                        return PsiReference.EMPTY_ARRAY;
                    }

                    // Determine parameter position
                    PsiElement[] parameters = parameterList.getParameters();
                    int paramIndex = -1;
                    for (int i = 0; i < parameters.length; i++) {
                        if (parameters[i].equals(literal)) {
                            paramIndex = i;
                            break;
                        }
                    }

                    if (paramIndex == -1 || paramIndex > 1) {
                        return PsiReference.EMPTY_ARRAY;
                    }

                    // Get category value if we're on the first parameter (item)
                    String category = null;
                    if (paramIndex == 0 && parameters.length > 1) {
                        PsiElement categoryParam = parameters[1];
                        if (categoryParam instanceof StringLiteralExpression) {
                            category = ((StringLiteralExpression) categoryParam).getContents();
                        }
                    }

                    // Create reference based on parameter position
                    return new PsiReference[]{
                        new LanguageReference(literal, paramIndex, category)
                    };
                }
            }
        );
    }
}

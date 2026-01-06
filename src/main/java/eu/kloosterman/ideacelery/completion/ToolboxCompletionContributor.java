package eu.kloosterman.ideacelery.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.parser.PhpElementTypes;
import com.jetbrains.php.lang.patterns.PhpPatterns;
import com.jetbrains.php.lang.psi.elements.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
public class ToolboxCompletionContributor extends CompletionContributor {

    public ToolboxCompletionContributor() {

        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            new CompletionProvider<CompletionParameters>() {
                @Override
                protected void addCompletions(@NotNull CompletionParameters completionParameters, ProcessingContext processingContext, @NotNull CompletionResultSet completionResultSet) {

                    PsiElement position = completionParameters.getPosition();

                    Project project = position.getProject();
                    List<String> arrCategories = getCategoriesForLanguageFiles(project);

                    FileType fileType = position.getContainingFile().getFileType();
                    if(!(fileType instanceof PhpFileType)) {
                        return;
                    }

                    ParameterList parameterList = PsiTreeUtil.getParentOfType(position, ParameterList.class);
                    if (parameterList == null) {
                        return;
                    }

                    PsiElement context = parameterList.getContext();
                    if (!(context instanceof FunctionReference)) {
                        return;
                    }

                    Integer index = getCurrentParameterIndex(position.getParent());

                    if(context instanceof MethodReference){
                        MethodReference method = (MethodReference) context;
                        ClassReference cls = (ClassReference) method.getClassReference();

                        String strMethod = method.getName();
                        String strClass = cls != null ? cls.getName() : null;

                        if (!strMethod.equals("get")) {
                            return;
                        }

                        if (!"Language".equals(strClass)) {
                            return;
                        }

                        if (index == 0) {
                            String cat = getCategoryValue(position.getParent());

                            if (cat == null) {
                                return;
                            }

                            List<String> arrItems = getItemsOfCategories(project, cat);

                            for (String item: arrItems) {
                                completionResultSet.addElement(LookupElementBuilder.create(item));
                            }
                        }

                        if (index == 1) {
                            for (String category: arrCategories) {
                                completionResultSet.addElement(LookupElementBuilder.create(category));
                            }
                        }
                    }
                }
            }
        );
    }

    private List<String> getItemsOfCategories(Project project, String findCategory) {
        PsiFile @NotNull [] arrPsiFiles = FilenameIndex.getFilesByName(project, "english-utf-8.php", GlobalSearchScope.allScope(project));

        List<String> arrReturn = new ArrayList<>();

        for (PsiFile psiFile : arrPsiFiles) {
            PsiDirectory psiDirectory = psiFile.getContainingDirectory();
            if (psiDirectory.getName().equals("languages")) {

                ArrayHashElement[] arrayHashElements = getArrayHashElements(psiFile);

                assert arrayHashElements != null;
                for (ArrayHashElement arrayHashElement: arrayHashElements) {
                    PhpPsiElement child = arrayHashElement.getKey();
                    if(child instanceof StringLiteralExpression) {
                        String strCategory = ((StringLiteralExpression) child).getContents();
                        if (strCategory.equals(findCategory)) {

                            PhpPsiElement arrayHashElementValue = arrayHashElement.getValue();
                            if(arrayHashElementValue instanceof ArrayCreationExpression) {
                                PsiElement @NotNull [] xxx = arrayHashElementValue.getChildren();
                                for (PsiElement psiElement: xxx) {
                                    if (psiElement instanceof ArrayHashElement) {
                                        PsiElement key = ((ArrayHashElement) psiElement).getKey();
                                        if(key instanceof StringLiteralExpression) {
                                            String str = ((StringLiteralExpression) key).getContents();
                                            arrReturn.add(str);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return arrReturn;
    }

    @Nullable
    private ArrayHashElement[] getArrayHashElements(PsiFile psiFile) {
        GroupStatement groupStatement = PsiTreeUtil.getChildOfType(psiFile, GroupStatement.class);
        Statement statement = PsiTreeUtil.getChildOfType(groupStatement, Statement.class);
        AssignmentExpression assignmentExpression = PsiTreeUtil.getChildOfType(statement, AssignmentExpression.class);
        ArrayCreationExpression arrayCreationExpression = PsiTreeUtil.getChildOfType(assignmentExpression, ArrayCreationExpression.class);

        return PsiTreeUtil.getChildrenOfType(arrayCreationExpression, ArrayHashElement.class);
    }

    private List<String> getCategoriesForLanguageFiles(Project project) {
        PsiFile @NotNull [] arrPsiFiles = FilenameIndex.getFilesByName(project, "english-utf-8.php", GlobalSearchScope.allScope(project));

        List<String> arrReturn = new ArrayList<>();

        for (PsiFile psiFile : arrPsiFiles) {
            PsiDirectory psiDirectory = psiFile.getContainingDirectory();
            if (psiDirectory.getName().equals("languages")) {

                ArrayHashElement[] arrayHashElements = getArrayHashElements(psiFile);

                assert arrayHashElements != null;
                for (ArrayHashElement arrayHashElement: arrayHashElements) {
                    PhpPsiElement child = arrayHashElement.getKey();
                    if(child instanceof StringLiteralExpression) {
                        String strCategory = ((StringLiteralExpression) child).getContents();
                        arrReturn.add(strCategory);
                    }
                }
            }
        }

        return arrReturn;
    }

    protected String getCategoryValue(PsiElement psiElement) {

        if (!(psiElement.getContext() instanceof ParameterList)) {
            return null;
        }

        ParameterList parameterList = (ParameterList) psiElement.getContext();
        if (!(parameterList.getContext() instanceof ParameterListOwner)) {
            return null;
        }

        PsiElement[] parameters = parameterList.getParameters();

        if (parameters[1] == null) {
            return null;
        }

        String strReturn = parameters[1].getText();
        strReturn = strReturn.replaceAll("^\"|\"$", "");

        if (strReturn.equals("")) {
            return null;
        }

        if (strReturn.equals("\"\"")) {
            return null;
        }

        return strReturn;
    }

    protected Integer getCurrentParameterIndex(PsiElement psiElement) {

        if (!(psiElement.getContext() instanceof ParameterList)) {
            return null;
        }

        ParameterList parameterList = (ParameterList) psiElement.getContext();
        if (!(parameterList.getContext() instanceof ParameterListOwner)) {
            return null;
        }

        return getCurrentParameter(parameterList.getParameters(), psiElement);
    }

    protected Integer getCurrentParameter(PsiElement[] parameters, PsiElement parameter) {
        int i;
        for(i = 0; i < parameters.length; i = i + 1) {
            if(parameters[i].equals(parameter)) {
                return i;
            }
        }

        return null;
    }

    @Nullable
    protected static ArrayCreationExpression getCompletableArrayCreationElement(PsiElement psiElement) {

        // array('<test>' => '')
        if(PhpPatterns.psiElement(PhpElementTypes.ARRAY_KEY).accepts(psiElement.getContext())) {
            PsiElement arrayKey = psiElement.getContext();
            if(arrayKey != null) {
                PsiElement arrayHashElement = arrayKey.getContext();
                if(arrayHashElement instanceof ArrayHashElement) {
                    PsiElement arrayCreationExpression = arrayHashElement.getContext();
                    if(arrayCreationExpression instanceof ArrayCreationExpression) {
                        return (ArrayCreationExpression) arrayCreationExpression;
                    }
                }
            }

        }

        // on array creation key dont have value, so provide completion here also
        // array('foo' => 'bar', '<test>')
        if(PhpPatterns.psiElement(PhpElementTypes.ARRAY_VALUE).accepts(psiElement.getContext())) {
            PsiElement arrayKey = psiElement.getContext();
            if(arrayKey != null) {
                PsiElement arrayCreationExpression = arrayKey.getContext();
                if(arrayCreationExpression instanceof ArrayCreationExpression) {
                    return (ArrayCreationExpression) arrayCreationExpression;
                }

            }

        }

        return null;
    }
}

package fun.mike.intellij.plugin;

import com.intellij.lang.LanguageCodeInsightActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RecordBuilderActionHandler implements LanguageCodeInsightActionHandler {
    @Override
    public boolean isValidFor(Editor editor, PsiFile file) {
        if (!(file instanceof PsiJavaFile)) {
            return false;
        }

        final Project project = editor.getProject();

        if (project == null) {
            return false;
        }

        return ClassLocator.locateStaticOrTopLevelClass(editor, file) != null;
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        PsiClass rootClass = ClassLocator.locateClass(editor, file);

        if (rootClass == null) {
            return;
        }

        // Get fields
        List<PsiField> fields = Arrays.asList(rootClass.getFields());

        Set<String> potentialGetters = fields.stream()
                .flatMap(field -> Stream.of(field.getName(), "get" + capitalize(field.getName())))
                .collect(Collectors.toSet());

        Set<String> potentialMethodsToDelete = new HashSet<>(potentialGetters);
        potentialMethodsToDelete.add("toString");
        potentialMethodsToDelete.add("hashCode");
        potentialMethodsToDelete.add("equals");
        potentialMethodsToDelete.add("newBuilder");

        // Delete methods
        List<PsiMethod> methods = Arrays.asList(rootClass.getMethods());

        List<PsiMethod> methodsToDelete = methods.stream()
                .filter(method -> potentialMethodsToDelete.contains(method.getName()))
                .collect(Collectors.toList());

        methodsToDelete.forEach(PsiElement::delete);

        // Delete constructors
        List<PsiMethod> constructors = Arrays.asList(rootClass.getConstructors());

        constructors.forEach(PsiMethod::delete);

        // Generate getters
        rootClass.add(generateConstructor(project, rootClass, fields));

        // Delete inner class
        List<PsiClass> innerClasses = Arrays.asList(rootClass.getInnerClasses());

        PsiClass builderClass = innerClasses.stream()
                .filter(innerClass -> innerClass.getName().equals("Builder"))
                .findFirst()
                .orElseGet(() -> generateBuilderClass(project, rootClass));

        // Delete builder fields
        Arrays.stream(builderClass.getFields())
                .forEach(PsiField::delete);

        // Delete potential builder methods
        Set<String> potentialBuilderMethodsToDelete = new HashSet<>(potentialGetters);
        potentialBuilderMethodsToDelete.add("build");

        List<PsiMethod> builderMethods = Arrays.asList(builderClass.getMethods());

        List<PsiMethod> builderMethodsToDelete = Arrays.stream(builderClass.getMethods())
                .filter(method -> potentialBuilderMethodsToDelete.contains(method.getName()))
                .collect(Collectors.toList());

        builderMethodsToDelete.forEach(PsiMethod::delete);

        // Delete builder constructors
        Arrays.stream(builderClass.getConstructors())
                .forEach(PsiMethod::delete);

        // Generate builder fields
        fields.stream()
                .map(field -> generateField(project, field))
                .forEach(builderClass::add);

        // Generate builder methods
        fields.stream()
                .map(field -> generateBuilderMethod(project, builderClass, field))
                .forEach(builderClass::add);

        // Generate build method
        builderClass.add(generateBuildMethod(project, rootClass, fields));

        // Attach builder class
        rootClass.add(generateNewBuilderMethod(project, rootClass, builderClass));

        // Shorten class names
        JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);

        codeStyleManager.shortenClassReferences(rootClass);
    }

    private PsiMethod generateConstructor(Project project,
                                          PsiClass clazz,
                                          List<PsiField> fields) {
        PsiElementFactory elementFactory =
                JavaPsiFacade.getInstance(project).getElementFactory();

        PsiMethod constructor = elementFactory.createConstructor(clazz.getName());

        fields.forEach(field -> {
            PsiParameter parameter = elementFactory.createParameter(field.getName(), field.getType());
            constructor.getParameterList().add(parameter);
        });

        fields.forEach(field -> {
            PsiStatement assignment = elementFactory
                    .createStatementFromText("this." + field.getName() + " = " + field.getName() + ";",
                                             constructor);

            constructor.getBody().add(assignment);
        });

        String valueList = fields.stream()
                .map(field -> '"' + field.getName() + '"')
                .collect(Collectors.joining(", "));

        constructor.getModifierList().addAnnotation("java.beans.ConstructorProperties({" + valueList + "})");

        return constructor;
    }

    private PsiClass generateBuilderClass(Project project, PsiClass clazz) {
        PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

        PsiClass builderClass = (PsiClass) clazz.add(elementFactory.createClass("Builder"));

        PsiUtil.setModifierProperty(builderClass, PsiModifier.STATIC, true);
        PsiUtil.setModifierProperty(builderClass, PsiModifier.FINAL, true);

        return builderClass;
    }

    private static PsiField generateField(Project project, PsiField rootField) {
        PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
        PsiField field = elementFactory.createField(rootField.getName(), rootField.getType());

        PsiUtil.setModifierProperty(field, PsiModifier.PRIVATE, true);

        return field;
    }

    private static PsiMethod generateBuilderMethod(Project project, PsiClass builderClass, PsiField field) {
        String fieldName = field.getName();
        PsiType fieldType = field.getType();

        PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

        PsiMethod method = elementFactory.createMethod(fieldName, PsiType.getTypeByName(builderClass.getQualifiedName(),
                                                                                        project,
                                                                                        GlobalSearchScope.EMPTY_SCOPE));

        method.getParameterList().add(elementFactory.createParameter("val", fieldType));

        method.getBody().add(elementFactory.createStatementFromText(fieldName + " = val;", method));

        method.getBody().add(elementFactory.createStatementFromText("return this;", method));

        PsiUtil.setModifierProperty(method, PsiModifier.PUBLIC, true);

        return method;
    }

    private PsiElement generateBuildMethod(Project project, PsiClass rootClass, List<PsiField> fields) {
        PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

        PsiMethod method = elementFactory.createMethod("build",
                                                       PsiType.getTypeByName(rootClass.getQualifiedName(),
                                                                             project,
                                                                             GlobalSearchScope.EMPTY_SCOPE));

        String fieldList = fields.stream()
                .map(PsiField::getName)
                .collect(Collectors.joining(",\n"));

        method.getBody().add(elementFactory.createStatementFromText("return new " + rootClass.getName() + "(" + fieldList + ");", method));

        PsiUtil.setModifierProperty(method, PsiModifier.PUBLIC, true);

        return method;
    }


    private PsiElement generateNewBuilderMethod(Project project, PsiClass rootClass, PsiClass builderClass) {
        PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

        PsiMethod method = elementFactory.createMethod("newBuilder",
                                                       PsiType.getTypeByName(builderClass.getQualifiedName(),
                                                                             project,
                                                                             GlobalSearchScope.EMPTY_SCOPE));

        method.getBody().add(elementFactory.createStatementFromText("return new " + builderClass.getName() + "();", method));

        PsiUtil.setModifierProperty(method, PsiModifier.PUBLIC, true);
        PsiUtil.setModifierProperty(method, PsiModifier.STATIC, true);

        return method;
    }

    private static String capitalize(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}

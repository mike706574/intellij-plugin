package fun.mike.intellij.plugin;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;

public class ClassLocator {
    private ClassLocator() {}

    public static PsiClass locateClass(Editor editor, PsiFile file) {
        final int offset = editor.getCaretModel().getOffset();

        final PsiElement element = file.findElementAt(offset);

        if (element == null) {
            return null;
        }

        return PsiTreeUtil.getParentOfType(element, PsiClass.class);
    }

    public static PsiClass locateStaticOrTopLevelClass(Editor editor, PsiFile file) {
        final int offset = editor.getCaretModel().getOffset();

        final PsiElement element = file.findElementAt(offset);

        if (element == null) {
            return null;
        }

        PsiClass topLevelClass = PsiUtil.getTopLevelClass(element);
        PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);

        if (psiClass != null && (psiClass.hasModifierProperty(PsiModifier.STATIC) ||
                psiClass.getManager().areElementsEquivalent(psiClass, topLevelClass))) {
            return psiClass;
        } else {
            return null;
        }
    }
}

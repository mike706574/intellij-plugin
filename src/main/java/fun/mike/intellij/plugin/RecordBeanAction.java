package fun.mike.intellij.plugin;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import org.jetbrains.annotations.NotNull;

public class RecordBeanAction extends BaseCodeInsightAction {
    private final RecordBeanActionHandler handler = new RecordBeanActionHandler();

    @Override
    protected @NotNull CodeInsightActionHandler getHandler() {
        return handler;
    }
}

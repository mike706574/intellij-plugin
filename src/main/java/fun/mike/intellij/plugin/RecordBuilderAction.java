package fun.mike.intellij.plugin;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import org.jetbrains.annotations.NotNull;

public class RecordBuilderAction extends BaseCodeInsightAction {
    private final RecordBuilderActionHandler handler = new RecordBuilderActionHandler();

    @Override
    protected @NotNull CodeInsightActionHandler getHandler() {
        return handler;
    }
}


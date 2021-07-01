package fun.mike.intellij.plugin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

public class MessageLogger {
    private final Project project;
    private final boolean debug;

    public MessageLogger(Project project, boolean debug) {
        this.project = project;
        this.debug = debug;
    }

    public void debug(String message) {
        if (debug) {
            Messages.showMessageDialog(project, message, "Message", Messages.getInformationIcon());
        }
    }
}
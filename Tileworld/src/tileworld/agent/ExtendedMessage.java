package tileworld.agent;

public class ExtendedMessage extends Message {
    private final MessageType type;
    private Object payload;

    public ExtendedMessage(String from, String to, String message, MessageType type, Object payload) {
        super(from, to, message);
        this.type = type;
        this.payload = payload;
    }

    public MessageType getType() {
        return type;
    }

    public Object getPayload() {
        return payload;
    }

    public TaskManager.TaskAssignment getTaskAssignment() {
        if (payload instanceof TaskManager.TaskAssignment) {
            return (TaskManager.TaskAssignment) payload;
        }
        return null;
    }
}

enum MessageType {
    DENSITY_UPDATE,
    AGENT_STATUS,
    TARGET_ASSIGNMENT,
    TASK_COMPLETE,
    EMERGENCY_FUEL,
    MANAGER_DIRECTIVE,
    TASK_REJECTED,
    PERCEPTIONS 
}
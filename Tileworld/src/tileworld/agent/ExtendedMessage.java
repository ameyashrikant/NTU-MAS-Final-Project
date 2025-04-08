package tileworld.agent;

/**
 * Extended message class that adds type information to basic messages.
 */
public class ExtendedMessage extends Message {
    private final MessageType type;

    /**
     * Creates a new extended message.
     * 
     * @param from    Sender identifier.
     * @param to      Recipient identifier.
     * @param message Message content.
     * @param type    Type of message.
     */
    public ExtendedMessage(String from, String to, String message, MessageType type) {
        super(from, to, message);
        this.type = type;
    }

    /**
     * @return The type of this message.
     */
    public MessageType getType() {
        return type;
    }
}

/**
 * Defines the types of messages that can be exchanged between agents.
 */
enum MessageType {
    DENSITY_UPDATE,     // Quadrant density information.
    AGENT_STATUS,       // Agent position, fuel, tiles carried.
    TARGET_ASSIGNMENT,  // Manager assigns targets to agents.
    TASK_COMPLETE,      // Agent reports completed task.
    EMERGENCY_FUEL,     // Critical fuel status.
    MANAGER_DIRECTIVE   // General manager instructions.
}
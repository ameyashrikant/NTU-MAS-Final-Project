package tileworld.agent;

import tileworld.environment.TWEntity;

public class TaskPriority {
    private final TWEntity entity;
    private final double priority;
    private final long timestamp;
    private final double decayRate;

    public TaskPriority(TWEntity entity, double priority, double decayRate) {
        this.entity = entity;
        this.priority = priority;
        this.timestamp = System.currentTimeMillis();
        this.decayRate = decayRate;
    }

    public TWEntity getEntity() {
        return entity;
    }

    public double getCurrentPriority(long currentTime) {
        return priority * Math.exp(-decayRate * (currentTime - timestamp));
    }
}

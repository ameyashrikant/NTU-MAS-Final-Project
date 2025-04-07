package tileworld.agent;

import tileworld.Parameters;
import tileworld.environment.*;
import sim.engine.SimState;
import sim.engine.Steppable;
import java.util.*;
import java.util.PriorityQueue;

public class TaskManager implements Steppable {
    private final TWEnvironment environment;
    private Map<TWHole, SpiralSearchingAgent> assignedHoles;
    private Map<TWTile, SpiralSearchingAgent> assignedTiles;
    private Map<String, Boolean> activeAgents;
    private PriorityQueue<TWHole> availableHoles;
    private Set<TWTile> availableTiles;
    private PriorityQueue<TaskPriority> taskQueue;
    private Map<TWEntity, Double> entityDecayRates;
    private static final int TASK_TIMEOUT = 20;
    private double lastChangeRate = 0.0;
    private long lastUpdateTime = 0;
    private static final double CHANGE_RATE_SMOOTHING = 0.3;

    public static class TaskPriority {
        private final TWEntity entity;
        private final double basePriority;
        private final double decayRate;
        private final long creationTime;

        public TaskPriority(TWEntity entity, double basePriority, double decayRate) {
            this.entity = entity;
            this.basePriority = basePriority;
            this.decayRate = decayRate;
            this.creationTime = System.currentTimeMillis();
        }

        public double getCurrentPriority(double currentTime) {
            double timeElapsed = (currentTime - (creationTime / 1000.0));
            return basePriority * Math.exp(-decayRate * timeElapsed);
        }

        public TWEntity getEntity() {
            return entity;
        }
    }

    public TaskManager(TWEnvironment env) {
        this.environment = env;
        this.assignedHoles = new HashMap<>();
        this.assignedTiles = new HashMap<>();
        this.activeAgents = new HashMap<>();
        this.availableHoles = new PriorityQueue<>((h1, h2) -> 
            Double.compare(getHoleUrgency(h2), getHoleUrgency(h1)));
        this.availableTiles = new HashSet<>();
        this.taskQueue = new PriorityQueue<>((a, b) -> 
            Double.compare(b.getCurrentPriority(environment.schedule.getTime()),
                          a.getCurrentPriority(environment.schedule.getTime())));
        this.entityDecayRates = new HashMap<>();
        environment.schedule.scheduleRepeating(this);
    }

    @Override
    public void step(SimState state) {
        cleanupExpiredAssignments();
    }

    private double getHoleUrgency(TWHole hole) {
        return hole.getTimeLeft(environment.schedule.getTime());
    }

    public synchronized void registerHole(TWHole hole) {
        if (!assignedHoles.containsKey(hole)) {
            availableHoles.offer(hole);
            System.out.println("Registered new hole at: " + hole.getX() + "," + hole.getY());
        }
    }

    public synchronized void registerTile(TWTile tile) {
        if (!assignedTiles.containsKey(tile)) {
            availableTiles.add(tile);
            System.out.println("Registered new tile at: " + tile.getX() + "," + tile.getY());
        }
    }

    public synchronized TWHole assignNearestUrgentHole(SpiralSearchingAgent agent, int x, int y) {
        cleanupExpiredAssignments();
        activeAgents.put(agent.getName(), true);
        
        TWHole mostUrgent = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double currentTime = environment.schedule.getTime();

        for (TWHole hole : availableHoles) {
            double timeLeft = hole.getTimeLeft(currentTime);
            double distance = manhattanDistance(x, y, hole.getX(), hole.getY());
            double score = calculateUrgencyScore(timeLeft, distance);
            
            if (score > bestScore) {
                bestScore = score;
                mostUrgent = hole;
            }
        }

        if (mostUrgent != null) {
            availableHoles.remove(mostUrgent);
            assignedHoles.put(mostUrgent, agent);
            System.out.println("Assigned hole at " + mostUrgent.getX() + "," + mostUrgent.getY() + 
                             " to agent " + agent.getName());
        }
        
        return mostUrgent;
    }

    private double calculateUrgencyScore(double timeLeft, double distance) {
        if (timeLeft <= 0) return Double.NEGATIVE_INFINITY;
        return (1.0 / distance) * timeLeft;
    }

    private int manhattanDistance(int x1, int y1, int x2, int y2) {
        return Math.abs(x2 - x1) + Math.abs(y2 - y1);
    }

    private void cleanupExpiredAssignments() {
        double currentTime = environment.schedule.getTime();
        Iterator<Map.Entry<TWHole, SpiralSearchingAgent>> it = assignedHoles.entrySet().iterator();
        
        while (it.hasNext()) {
            Map.Entry<TWHole, SpiralSearchingAgent> entry = it.next();
            TWHole hole = entry.getKey();
            if (hole.getTimeLeft(currentTime) <= 0 || 
                !activeAgents.getOrDefault(entry.getValue().getName(), false)) {
                it.remove();
                System.out.println("Cleaned up expired assignment for hole at " + 
                                 hole.getX() + "," + hole.getY());
            }
        }
    }

    public synchronized void releaseHole(TWHole hole) {
        SpiralSearchingAgent agent = assignedHoles.remove(hole);
        if (agent != null && hole.getTimeLeft(environment.schedule.getTime()) > 0) {
            availableHoles.offer(hole);
            System.out.println("Released hole at " + hole.getX() + "," + hole.getY() + 
                             " from agent " + agent.getName());
        }
    }

    public Map<TWHole, SpiralSearchingAgent> getAssignedHoles() {
        return new HashMap<>(assignedHoles);
    }

    public Map<TWTile, SpiralSearchingAgent> getAssignedTiles() {
        return new HashMap<>(assignedTiles);
    }

    public void updateTaskPriority(TWEntity entity) {
        double priority = calculatePriority(entity);
        double decayRate = estimateDecayRate(entity);
        TaskPriority newTask = new TaskPriority(entity, priority, decayRate);
        
        taskQueue.removeIf(task -> task.getEntity().equals(entity));
        taskQueue.offer(newTask);
    }

    private double calculatePriority(TWEntity entity) {
        double currentTime = environment.schedule.getTime();
        double timeLeft = 0;
        double distance = 0;
        double environmentDynamism = getEnvironmentDynamism();

        if (entity instanceof TWHole) {
            TWHole hole = (TWHole) entity;
            timeLeft = hole.getTimeLeft(currentTime);
            distance = getDistanceToNearestTile(hole);
            return (1.0 / Math.max(timeLeft, 0.1)) * (1.0 / Math.max(distance, 0.1)) * 
                   (1 + environmentDynamism) * 2.0;
        } else if (entity instanceof TWTile) {
            TWTile tile = (TWTile) entity;
            timeLeft = tile.getTimeLeft(currentTime);
            distance = getDistanceToNearestHole(tile);
            return (1.0 / Math.max(timeLeft, 0.1)) * (1.0 / Math.max(distance, 0.1)) * 
                   (1 + environmentDynamism);
        }
        return 0;
    }

    private double estimateDecayRate(TWEntity entity) {
        if (!entityDecayRates.containsKey(entity)) {

            double initialLifetime = Parameters.lifeTime;
            entityDecayRates.put(entity, 1.0 / initialLifetime);
        }
        return entityDecayRates.get(entity);
    }

    public TaskPriority getHighestPriorityTask() {
        return taskQueue.peek();
    }

    private double getDistanceToNearestTile(TWHole hole) {
        double minDistance = Double.MAX_VALUE;
        for (TWTile tile : availableTiles) {
            double distance = manhattanDistance(
                hole.getX(), hole.getY(),
                tile.getX(), tile.getY()
            );
            minDistance = Math.min(minDistance, distance);
        }
        return minDistance == Double.MAX_VALUE ? 
               environment.getxDimension() + environment.getyDimension() : 
               minDistance;
    }

    private double getDistanceToNearestHole(TWTile tile) {
        double minDistance = Double.MAX_VALUE;
        for (TWHole hole : availableHoles) {
            double distance = manhattanDistance(
                tile.getX(), tile.getY(),
                hole.getX(), hole.getY()
            );
            minDistance = Math.min(minDistance, distance);
        }
        return minDistance == Double.MAX_VALUE ? 
               environment.getxDimension() + environment.getyDimension() : 
               minDistance;
    }

    private double getEnvironmentDynamism() {
        long currentTime = environment.schedule.getSteps();
        double recentChanges = entityDecayRates.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
        return Math.min(1.0, recentChanges * currentTime / 1000.0);
    }

    public double getEnvironmentChangeRate() {
        long currentTime = environment.schedule.getSteps();
        if (currentTime == lastUpdateTime) {
            return lastChangeRate;
        }
        
        // Calculate change rate based on decay rates and recent changes
        double instantRate = entityDecayRates.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
        
        // Apply exponential smoothing to avoid sudden changes
        lastChangeRate = (CHANGE_RATE_SMOOTHING * instantRate) + 
                        ((1 - CHANGE_RATE_SMOOTHING) * lastChangeRate);
        lastUpdateTime = currentTime;
        
        return Math.min(1.0, Math.max(0.1, lastChangeRate));
    }
}
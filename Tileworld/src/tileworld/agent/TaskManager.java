package tileworld.agent;

import tileworld.Parameters;
import tileworld.environment.*;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.field.grid.ObjectGrid2D;
import java.util.*;

public class TaskManager implements Steppable {
    private final TWEnvironment environment;
    private Map<TWHole, SpiralSearchingAgent> assignedHoles;
    private Map<TWTile, SpiralSearchingAgent> assignedTiles;
    private Map<String, Boolean> activeAgents;
    private PriorityQueue<TWHole> availableHoles;
    private Set<TWTile> availableTiles;
    private PriorityQueue<TaskPriority> taskQueue;
    private Map<TWEntity, Double> entityDecayRates;
    private Map<TWEntity, TaskPriority> priorityCache;
    private static final int TASK_TIMEOUT = 20;
    private double lastChangeRate = 0.0;
    private long lastUpdateTime = 0;
    private static final double CHANGE_RATE_SMOOTHING = 0.3;

    private List<TaskAssignment> pendingAssignments = new ArrayList<>();
    private Map<String, List<TaskAssignment>> acceptedAssignments = new HashMap<>();

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

    public static class TaskAssignment {
        public final TWEntity entity;
        public final SpiralSearchingAgent agent;
        public final double priority;
        public final long timestamp;
        public boolean accepted;

        public TaskAssignment(SpiralSearchingAgent agent, TWEntity entity) {
            this.entity = entity;
            this.agent = agent;
            this.priority = 1.0; // Default priority
            this.timestamp = System.currentTimeMillis();
            this.accepted = true; // Auto-accepted if created by agent
        }

        public TaskAssignment(TWEntity entity, SpiralSearchingAgent agent, double priority) {
            this.entity = entity;
            this.agent = agent;
            this.priority = priority;
            this.timestamp = System.currentTimeMillis();
            this.accepted = false; // Not accepted until confirmed
        }

        public TWEntity getEntity() {
            return entity;
        }

        public SpiralSearchingAgent getAgent() {
            return agent;
        }

        public double getPriority() {
            return priority;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public boolean isAccepted() {
            return accepted;
        }

        public void setAccepted(boolean accepted) {
            this.accepted = accepted;
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
        this.priorityCache = new HashMap<>();  
        environment.schedule.scheduleRepeating(this);
    }

    @Override
    public void step(SimState state) {
        cleanupExpiredAssignments();
        if (environment.schedule.getSteps() % 5 == 0) {
            optimizeTaskAllocation();
        }
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

    public double calculatePriority(TWEntity entity) {
        if (entity == null) return 0.0;

        // Check cache first
        if (priorityCache.containsKey(entity)) {
            // Get the current priority value from the cached TaskPriority object
            return priorityCache.get(entity).getCurrentPriority(environment.schedule.getTime());
        }

        double priority = 0.0;

        if (entity instanceof TWHole) {
            TWHole hole = (TWHole) entity;
            double timeLeft = hole.getTimeLeft(environment.schedule.getTime());
            priority = calculateHolePriority(hole, timeLeft);
        } else if (entity instanceof TWTile) {
            priority = 0.5; // Base priority for tiles
        }

        // Create and cache a TaskPriority object
        double decayRate = estimateDecayRate(entity);
        TaskPriority taskPriority = new TaskPriority(entity, priority, decayRate);
        priorityCache.put(entity, taskPriority);
        
        return priority;
    }

    private double calculateHolePriority(TWHole hole, double timeLeft) {
        if (timeLeft <= 0) return 0.0;

        // Higher priority for holes that are about to expire
        double urgencyFactor = 1.0 / (timeLeft + 1);

        // Consider hole's position and surrounding context
        double positionFactor = calculatePositionFactor(hole);

        return urgencyFactor * positionFactor * 10.0; // Scale factor
    }

    private double calculatePositionFactor(TWHole hole) {
        // Consider edge cases and position-based priority
        int distanceFromEdge = Math.min(
            Math.min(hole.getX(), environment.getxDimension() - hole.getX()),
            Math.min(hole.getY(), environment.getyDimension() - hole.getY())
        );

        return 1.0 + (0.2 * distanceFromEdge / environment.getxDimension());
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

    public void assignTask(SpiralSearchingAgent agent, TWEntity entity, double priority) {
        TaskAssignment task = new TaskAssignment(entity, agent, priority);
        pendingAssignments.add(task);
        System.out.println("Task assigned: " + entity.getClass().getSimpleName() + 
                           " at " + entity.getX() + "," + entity.getY() + 
                           " to agent " + agent.getName());
    }

    public synchronized void acceptTask(SpiralSearchingAgent agent, TWEntity entity) {
        boolean entityExists = false;
        if (entity instanceof TWHole) {
            entityExists = availableHoles.contains(entity) || assignedHoles.containsKey((TWHole) entity);
        } else if (entity instanceof TWTile) {
            entityExists = availableTiles.contains(entity) || assignedTiles.containsKey((TWTile) entity);
        }

        if (!entityExists) {
            System.out.println("Agent " + agent.getName() + " tried to accept non-existent task");
            return;
        }

        Iterator<TaskAssignment> it = pendingAssignments.iterator();
        while (it.hasNext()) {
            TaskAssignment task = it.next();
            if (task.entity.equals(entity) && task.agent.equals(agent)) {
                task.accepted = true;
                it.remove();
                acceptedAssignments.computeIfAbsent(agent.getName(), k -> new ArrayList<>()).add(task);
                if (entity instanceof TWHole) {
                    assignedHoles.put((TWHole) entity, agent);
                    availableHoles.remove(entity);
                } else if (entity instanceof TWTile) {
                    assignedTiles.put((TWTile) entity, agent);
                    availableTiles.remove(entity);
                }
                System.out.println("Agent " + agent.getName() + " accepted task for " + 
                                   entity.getClass().getSimpleName() + " at " + 
                                   entity.getX() + "," + entity.getY());
                return;
            }
        }
    }

    public synchronized void rejectTask(SpiralSearchingAgent agent, TWEntity entity, String reason) {
        pendingAssignments.removeIf(task -> task.entity.equals(entity) && task.agent.equals(agent));
        System.out.println("Agent " + agent.getName() + " rejected task for " + 
                           entity.getClass().getSimpleName() + " at " + 
                           entity.getX() + "," + entity.getY() + " - Reason: " + reason);
        scheduleReassignment();
    }

    private void scheduleReassignment() {
        environment.schedule.scheduleOnce(environment.schedule.getTime() + 1, state -> optimizeTaskAllocation());
    }

    public void optimizeTaskAllocation() {
        List<SpiralSearchingAgent> availableAgents = getAvailableAgents();
        List<TWEntity> availableTasks = getAvailableTasks();

        if (availableAgents.isEmpty() || availableTasks.isEmpty()) {
            return;
        }

        double[][] costMatrix = new double[availableAgents.size()][availableTasks.size()];

        for (int i = 0; i < availableAgents.size(); i++) {
            SpiralSearchingAgent agent = availableAgents.get(i);
            for (int j = 0; j < availableTasks.size(); j++) {
                TWEntity task = availableTasks.get(j);
                int distance = manhattanDistance(agent.getX(), agent.getY(), task.getX(), task.getY());
                double priority = calculatePriority(task);
                boolean canHandle = canAgentHandleTask(agent, task);

                costMatrix[i][j] = canHandle ? distance / (priority + 0.1) : Double.MAX_VALUE;
            }
        }

        int[] assignments = new HungarianAlgorithm(costMatrix).execute();

        for (int i = 0; i < assignments.length; i++) {
            int taskIndex = assignments[i];
            if (taskIndex >= 0 && costMatrix[i][taskIndex] < Double.MAX_VALUE) {
                SpiralSearchingAgent agent = availableAgents.get(i);
                TWEntity task = availableTasks.get(taskIndex);
                assignTaskToAgent(agent, task);
            }
        }
    }

    private boolean canAgentHandleTask(SpiralSearchingAgent agent, TWEntity task) {
        int distance = manhattanDistance(agent.getX(), agent.getY(), task.getX(), task.getY());
        int fuelNeeded = distance + (int) (distance * 0.2);

        if (task instanceof TWHole) {
            // Change from agent.sensor.getCarriedTiles() to agent.carriedTiles
            return !agent.carriedTiles.isEmpty() && agent.getFuelLevel() >= fuelNeeded;
        } else if (task instanceof TWTile) {
            // Change from agent.sensor.getCarriedTiles() to agent.carriedTiles
            return agent.carriedTiles.isEmpty() && agent.getFuelLevel() >= fuelNeeded;
        }

        return false;
    }

    private void assignTaskToAgent(SpiralSearchingAgent agent, TWEntity task) {
        String taskType = task instanceof TWHole ? "HOLE" : "TILE";
        String message = "TASK:" + taskType + ":" + task.getX() + "," + task.getY() + ":" + agent.getName();
        double priority = calculatePriority(task);
        TaskManager.TaskAssignment assignment = new TaskManager.TaskAssignment(task, agent, priority);
        environment.receiveMessage(new ExtendedMessage("MANAGER", agent.getName(), message, MessageType.TARGET_ASSIGNMENT, assignment));

        if (task instanceof TWHole) {
            assignedHoles.put((TWHole) task, agent);
            availableHoles.remove(task);
        } else if (task instanceof TWTile) {
            assignedTiles.put((TWTile) task, agent);
            availableTiles.remove(task);
        }
    }

    public double getTaskPriority(TWEntity entity) {
        TaskPriority taskPriority = priorityCache.get(entity);
        if (taskPriority != null) {
            return taskPriority.getCurrentPriority(environment.schedule.getTime());
        }
        return calculatePriority(entity);
    }

    public void completeTask(String agentName, TWEntity entity) {
        if (entity instanceof TWHole) {
            TWHole hole = (TWHole) entity;
            if (assignedHoles.containsKey(hole)) {
                assignedHoles.remove(hole);
                System.out.println("Hole task completed at (" + hole.getX() + "," + hole.getY() + ") by " + agentName);
            }
        } else if (entity instanceof TWTile) {
            TWTile tile = (TWTile) entity;
            if (assignedTiles.containsKey(tile)) {
                assignedTiles.remove(tile);
                System.out.println("Tile task completed at (" + tile.getX() + "," + tile.getY() + ") by " + agentName);
            }
        }
        priorityCache.remove(entity);
    }

    private SpiralSearchingAgent getAgentByName(String name) {
        // Iterate through the entire agent grid to find the agent
        ObjectGrid2D agentGrid = environment.getAgentGrid();
        for (int x = 0; x < environment.getxDimension(); x++) {
            for (int y = 0; y < environment.getyDimension(); y++) {
                Object obj = agentGrid.get(x, y);
                if (obj instanceof SpiralSearchingAgent && 
                    ((SpiralSearchingAgent) obj).getName().equals(name)) {
                    return (SpiralSearchingAgent) obj;
                }
            }
        }
        return null;
    }

    private List<SpiralSearchingAgent> getAvailableAgents() {
        List<SpiralSearchingAgent> result = new ArrayList<>();
        // Iterate through the agent grid to find all SpiralSearchingAgents
        ObjectGrid2D agentGrid = environment.getAgentGrid();
        for (int x = 0; x < environment.getxDimension(); x++) {
            for (int y = 0; y < environment.getyDimension(); y++) {
                Object obj = agentGrid.get(x, y);
                if (obj instanceof SpiralSearchingAgent) {
                    result.add((SpiralSearchingAgent) obj);
                }
            }
        }
        return result;
    }

    private List<TWEntity> getAvailableTasks() {
        List<TWEntity> tasks = new ArrayList<>(availableHoles);
        tasks.addAll(availableTiles);
        return tasks;
    }

    private class HungarianAlgorithm {
        private final double[][] costMatrix;
        private final int rows, cols;
        private final double[] labelByWorker, labelByJob;
        private final int[] minSlackWorkerByJob;
        private final double[] minSlackValueByJob;
        private final int[] matchJobByWorker, matchWorkerByJob;
        private final int[] parentWorkerByCommittedJob;
        private final boolean[] committedWorkers;

        public HungarianAlgorithm(double[][] costMatrix) {
            this.rows = costMatrix.length;
            this.cols = costMatrix[0].length;
            this.costMatrix = new double[this.rows][this.cols];
            for (int w = 0; w < this.rows; w++) {
                System.arraycopy(costMatrix[w], 0, this.costMatrix[w], 0, this.cols);
            }
            labelByWorker = new double[this.rows];
            labelByJob = new double[this.cols];
            minSlackWorkerByJob = new int[this.cols];
            minSlackValueByJob = new double[this.cols];
            committedWorkers = new boolean[this.rows];
            parentWorkerByCommittedJob = new int[this.cols];
            matchJobByWorker = new int[this.rows];
            Arrays.fill(matchJobByWorker, -1);
            matchWorkerByJob = new int[this.cols];
            Arrays.fill(matchWorkerByJob, -1);
        }

        public int[] execute() {
            reduce();
            computeInitialFeasibleSolution();
            greedyMatch();
            int w = fetchUnmatchedWorker();
            while (w < rows) {
                initializePhase(w);
                executePhase();
                w = fetchUnmatchedWorker();
            }
            int[] result = Arrays.copyOf(matchJobByWorker, rows);
            for (w = 0; w < result.length; w++) {
                if (result[w] >= cols) {
                    result[w] = -1;
                }
            }
            return result;
        }

        private void reduce() {
            for (int w = 0; w < rows; w++) {
                double min = Double.POSITIVE_INFINITY;
                for (int j = 0; j < cols; j++) {
                    if (costMatrix[w][j] < min) {
                        min = costMatrix[w][j];
                    }
                }
                for (int j = 0; j < cols; j++) {
                    costMatrix[w][j] -= min;
                }
            }
            double[] min = new double[cols];
            Arrays.fill(min, Double.POSITIVE_INFINITY);
            for (int w = 0; w < rows; w++) {
                for (int j = 0; j < cols; j++) {
                    if (costMatrix[w][j] < min[j]) {
                        min[j] = costMatrix[w][j];
                    }
                }
            }
            for (int w = 0; w < rows; w++) {
                for (int j = 0; j < cols; j++) {
                    costMatrix[w][j] -= min[j];
                }
            }
        }

        private void computeInitialFeasibleSolution() {
            Arrays.fill(labelByWorker, 0);
            Arrays.fill(labelByJob, Double.POSITIVE_INFINITY);
            for (int w = 0; w < rows; w++) {
                for (int j = 0; j < cols; j++) {
                    if (costMatrix[w][j] < labelByJob[j]) {
                        labelByJob[j] = costMatrix[w][j];
                    }
                }
            }
        }

        private void greedyMatch() {
            for (int w = 0; w < rows; w++) {
                for (int j = 0; j < cols; j++) {
                    if (matchWorkerByJob[j] == -1 && 
                        Math.abs(costMatrix[w][j] - labelByWorker[w] - labelByJob[j]) < 1e-10) {
                        matchJobByWorker[w] = j;
                        matchWorkerByJob[j] = w;
                        break;
                    }
                }
            }
        }

        private int fetchUnmatchedWorker() {
            int w;
            for (w = 0; w < rows; w++) {
                if (matchJobByWorker[w] == -1) {
                    break;
                }
            }
            return w;
        }

        private void initializePhase(int w) {
            Arrays.fill(committedWorkers, false);
            Arrays.fill(parentWorkerByCommittedJob, -1);
            committedWorkers[w] = true;
            for (int j = 0; j < cols; j++) {
                minSlackValueByJob[j] = costMatrix[w][j] - labelByWorker[w] - labelByJob[j];
                minSlackWorkerByJob[j] = w;
            }
        }

        private void executePhase() {
            while (true) {
                int minSlackWorker = -1, minSlackJob = -1;
                double minSlackValue = Double.POSITIVE_INFINITY;
                for (int j = 0; j < cols; j++) {
                    if (parentWorkerByCommittedJob[j] == -1) {
                        if (minSlackValueByJob[j] < minSlackValue) {
                            minSlackValue = minSlackValueByJob[j];
                            minSlackWorker = minSlackWorkerByJob[j];
                            minSlackJob = j;
                        }
                    }
                }
                if (minSlackValue > 0) {
                    updateLabeling(minSlackValue);
                }
                parentWorkerByCommittedJob[minSlackJob] = minSlackWorker;
                if (matchWorkerByJob[minSlackJob] == -1) {
                    int committedJob = minSlackJob;
                    int parentWorker = parentWorkerByCommittedJob[committedJob];
                    while (true) {
                        int temp = matchJobByWorker[parentWorker];
                        matchJobByWorker[parentWorker] = committedJob;
                        matchWorkerByJob[committedJob] = parentWorker;
                        committedJob = temp;
                        if (committedJob == -1) {
                            break;
                        }
                        parentWorker = parentWorkerByCommittedJob[committedJob];
                    }
                    return;
                } else {
                    int worker = matchWorkerByJob[minSlackJob];
                    committedWorkers[worker] = true;
                    for (int j = 0; j < cols; j++) {
                        if (parentWorkerByCommittedJob[j] == -1) {
                            double slack = costMatrix[worker][j] - labelByWorker[worker] - labelByJob[j];
                            if (minSlackValueByJob[j] > slack) {
                                minSlackValueByJob[j] = slack;
                                minSlackWorkerByJob[j] = worker;
                            }
                        }
                    }
                }
            }
        }

        private void updateLabeling(double slack) {
            for (int w = 0; w < rows; w++) {
                if (committedWorkers[w]) {
                    labelByWorker[w] += slack;
                }
            }
            for (int j = 0; j < cols; j++) {
                if (parentWorkerByCommittedJob[j] != -1) {
                    labelByJob[j] -= slack;
                } else {
                    minSlackValueByJob[j] -= slack;
                }
            }
        }
    }
}
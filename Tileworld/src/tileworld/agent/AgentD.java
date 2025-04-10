package tileworld.agent;

import tileworld.environment.TWDirection;
import tileworld.environment.TWEntity;
import tileworld.environment.TWTile;
import tileworld.environment.TWHole;
import tileworld.environment.TWFuelStation;
import tileworld.planners.TWPath;
import tileworld.agent.TWAction;
import tileworld.agent.TWThought;
import sim.util.Bag;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class AgentD extends SpiralSearchingAgent {
    private Map<String, AgentStatus> teamStatus = new HashMap<>();
    private TaskManager taskManager;

    private static class AgentStatus {
        final int x, y;
        final double fuelLevel;
        final int tileCount;
        final long timestamp;

        AgentStatus(int x, int y, double fuelLevel, int tileCount) {
            this.x = x;
            this.y = y;
            this.fuelLevel = fuelLevel;
            this.tileCount = tileCount;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public AgentD(String name, int xpos, int ypos, tileworld.environment.TWEnvironment env, double fuelLevel) {
        super(name, xpos, ypos, env, fuelLevel, TWDirection.W);
        this.taskManager = env.getTaskManager();
    }

    @Override
    protected TWThought think() {
        long currentTime = getEnvironment().schedule.getSteps();

        // Process messages first to update team status and handle completions
        processMessages();

        // Register new tasks from memory
        for (TWEntity e : ((SmartMemory) memory).getObjects()) {
            if (e instanceof TWTile && !taskManager.getAssignedTiles().containsKey(e) && 
                !taskManager.availableTiles.contains(e)) {
                taskManager.registerTile((TWTile) e);
                System.out.println(name + " registered tile at " + e.getX() + "," + e.getY());
            } else if (e instanceof TWHole && !taskManager.getAssignedHoles().containsKey(e) && 
                !taskManager.availableHoles.contains(e)) {
                taskManager.registerHole((TWHole) e);
                System.out.println(name + " registered hole at " + e.getX() + "," + e.getY());
            }
        }

        // Assign tasks to other agents (and itself) every 5 steps
        if (currentTime % 5 == 0) {
            assignTasksBasedOnStatus();
        }

        // Optimize task allocation every 20 steps
        if (currentTime % 20 == 0) {
            taskManager.optimizeTaskAllocation();
            System.out.println(name + " optimized task allocation");
        }

        // Manager acts as a worker: reuse SpiralSearchingAgent logic for its own tasks
        TWThought workerThought = super.think();
        return workerThought; // Execute its own task (pickup, putdown, refuel, or move)
    }

    private void assignTasksBasedOnStatus() {
        System.out.println(name + " assigning tasks based on status");
        List<SpiralSearchingAgent> availableAgents = taskManager.getAvailableAgents();
        List<TWEntity> availableTasks = taskManager.getAvailableTasks();

        // Sort tasks by priority (descending order)
        List<TWEntity> sortedTasks = new ArrayList<>(availableTasks);
        sortedTasks.sort((t1, t2) -> Double.compare(taskManager.calculatePriority(t2), taskManager.calculatePriority(t1)));

        // Include manager itself in the agent list
        List<SpiralSearchingAgent> allAgents = new ArrayList<>(availableAgents);
        allAgents.add(this); // Add AgentD to participate

        for (SpiralSearchingAgent agent : allAgents) {
            String agentName = agent.getName();
            AgentStatus status = agentName.equals(this.name) ? 
                new AgentStatus(getX(), getY(), getFuelLevel(), carriedTiles.size()) : 
                teamStatus.get(agentName);
            if (status == null) {
                System.out.println(name + " no status for " + agentName);
                continue;
            }

            if (status.fuelLevel < MIN_FUEL_LEVEL / 2 && fuelStationX != -1) {
                sendTaskMessage(agentName, "FUEL", fuelStationX, fuelStationY);
                agent.isHeadingToFuelStation = true;
                agent.currentPath = null;
                continue;
            }

            // Skip if agent already has a task
            if (agent.isPickingUpTile || agent.fillHole) {
                continue;
            }

            TWEntity bestTask = null;
            double bestScore = -1.0;
            for (TWEntity task : sortedTasks) {
                if (task instanceof TWHole && status.tileCount > 0 && !taskManager.getAssignedHoles().containsKey(task)) {
                    int distance = taskManager.manhattanDistance(status.x, status.y, task.getX(), task.getY());
                    double priority = taskManager.calculatePriority(task);
                    double score = priority / (distance + 1.0);
                    if (score > bestScore && getEnvironment().getObjectGrid().get(task.getX(), task.getY()) != null) {
                        bestTask = task;
                        bestScore = score;
                    }
                } else if (task instanceof TWTile && status.tileCount < 3 && !taskManager.getAssignedTiles().containsKey(task)) {
                    int distance = taskManager.manhattanDistance(status.x, status.y, task.getX(), task.getY());
                    double priority = taskManager.calculatePriority(task);
                    double score = priority / (distance + 1.0);
                    if (score > bestScore && getEnvironment().getObjectGrid().get(task.getX(), task.getY()) != null) {
                        bestTask = task;
                        bestScore = score;
                    }
                }
            }

            if (bestTask != null) {
                taskManager.assignTask(agent, bestTask, taskManager.calculatePriority(bestTask));
                if (bestTask instanceof TWTile) {
                    sendTaskMessage(agentName, "TILE", bestTask.getX(), bestTask.getY());
                    agent.tileToBePickedUp = (TWTile) bestTask;
                    agent.isPickingUpTile = true;
                    agent.currentPath = null;
                } else if (bestTask instanceof TWHole) {
                    sendTaskMessage(agentName, "HOLE", bestTask.getX(), bestTask.getY());
                    agent.holeToBeFilled = (TWHole) bestTask;
                    agent.fillHole = true;
                    agent.currentPath = null;
                }
                sortedTasks.remove(bestTask); // Prevent reassignment
            }
        }
    }

    @Override
    protected void processMessages() {
        super.processMessages();
        System.out.println(name + " processing " + getEnvironment().getMessages().size() + " messages");
        for (Message m : getEnvironment().getMessages()) {
            if (m instanceof ExtendedMessage) {
                ExtendedMessage em = (ExtendedMessage) m;
                System.out.println(name + " received message: " + em.getMessage() + " from " + em.getFrom() + " to " + em.getTo());
                if (em.getTo().equals(this.name)) {
                    if (em.getType() == MessageType.POSITION_REPORT) {
                        String[] parts = em.getMessage().split(":")[1].split(",");
                        if (parts.length == 4) {
                            try {
                                int x = Integer.parseInt(parts[0]);
                                int y = Integer.parseInt(parts[1]);
                                int tileCount = Integer.parseInt(parts[2]);
                                double fuelLevel = Double.parseDouble(parts[3]);
                                String fromAgent = em.getFrom();
                                teamStatus.put(fromAgent, new AgentStatus(x, y, fuelLevel, tileCount));
                                System.out.println(name + " received status from " + fromAgent + 
                                                   ": x=" + x + ", y=" + y + ", tiles=" + tileCount + 
                                                   ", fuel=" + fuelLevel);
                            } catch (NumberFormatException e) {
                                System.out.println(name + " failed to parse status message: " + em.getMessage());
                            }
                        }
                    } else if (em.getType() == MessageType.TASK_COMPLETE) {
                        String[] parts = em.getMessage().split(":");
                        if (parts.length == 3) {
                            String taskType = parts[1];
                            String[] coords = parts[2].split(",");
                            int x = Integer.parseInt(coords[0]);
                            int y = Integer.parseInt(coords[1]);
                            TWEntity entity = (TWEntity) getEnvironment().getObjectGrid().get(x, y);
                            String fromAgent = em.getFrom();
                            if (entity != null) {
                                taskManager.completeTask(fromAgent, entity);
                                ((SmartMemory) memory).removeObject(entity);
                                System.out.println(name + " recorded task completion by " + fromAgent + 
                                                   ": " + taskType + " at (" + x + "," + y + ")");
                            } else {
                                System.out.println(name + " task at (" + x + "," + y + ") no longer exists");
                                if (taskType.equals("TILE")) {
                                    TWTile dummyTile = new TWTile(x, y, getEnvironment(), 0.0, 0.0);
                                    taskManager.completeTask(fromAgent, dummyTile);
                                    ((SmartMemory) memory).removeObject(dummyTile);
                                } else if (taskType.equals("HOLE")) {
                                    TWHole dummyHole = new TWHole(x, y, getEnvironment(), 0.0, 0.0);
                                    taskManager.completeTask(fromAgent, dummyHole);
                                    ((SmartMemory) memory).removeObject(dummyHole);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public boolean sameLocation(TWEntity target) {
        return getX() == target.getX() && getY() == target.getY();
    }

    public Map<String, AgentStatus> getTeamStatus() {
        return new HashMap<>(teamStatus);
    }
}
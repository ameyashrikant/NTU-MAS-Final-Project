package tileworld.agent;

import tileworld.Parameters;
import tileworld.environment.*;
import sim.field.grid.ObjectGrid2D;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class EnhancedAgentD extends AgentD implements MessageReceiver {
    private static final String BROADCAST_PREFIX = "INFO:";
    private static final int BROADCAST_INTERVAL = 7; // Less frequent
    private int stepsSinceLastBroadcast = 0;
    private final Set<Point> broadcastedLocations = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<Point, Long> exploredTimestamps = new HashMap<>();
    private static final long REVISIT_THRESHOLD = 35; // Steps before revisiting a location
    
    // Add exploration quadrants
    private int currentQuadrant = 0;
    private static final int QUADRANTS = 4;

    private static final int STATUS_INTERVAL = 3;
    private int stepsSinceLastStatus = 0;
    private final Map<String, AgentStatus> teamStatus = new ConcurrentHashMap<>(4);
    
    private Map<Integer, Integer> quadrantHoleCount = new HashMap<>();
    private Map<Integer, Double> quadrantSpawnRates = new HashMap<>();
    private static final double SPAWN_RATE_DECAY = 0.98; // Rate at which old spawn data decays
    private static final int SPAWN_RATE_WINDOW = 50; // Steps to consider for spawn rate

    // Add new fields for hole density tracking
    private final Map<Integer, HoleDensityTracker> quadrantDensity = new HashMap<>();
    private static final int DENSITY_UPDATE_INTERVAL = 12; // Even number for better synchronization
    private int stepsSinceDensityUpdate = 0;

    private static class Point {
        final int x, y;
        Point(int x, int y) { 
            this.x = x; 
            this.y = y; 
        }
        
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Point)) return false;
            Point p = (Point) o;
            return x == p.x && y == p.y;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    private static class AgentStatus {
        final int x, y;
        final int quadrant;
        final long timestamp;
        final double fuelLevel;

        AgentStatus(int x, int y, int quadrant, double fuelLevel) {
            this.x = x;
            this.y = y;
            this.quadrant = quadrant;
            this.fuelLevel = fuelLevel;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private static class HoleDensityTracker {
        private int holeCount;
        private int observationCount;
        private double lastDensity;

        public void addObservation(boolean hasHole) {
            if (hasHole) holeCount++;
            observationCount++;
            updateDensity();
        }

        private void updateDensity() {
            lastDensity = observationCount > 0 ? (double) holeCount / observationCount : 0;
        }

        public double getDensity() {
            return lastDensity;
        }

        public void decay(double factor) {
            holeCount = (int)(holeCount * factor);
            observationCount = (int)(observationCount * factor);
            updateDensity();
        }
    }

    public EnhancedAgentD(String name, int xpos, int ypos, TWEnvironment env, double fuelLevel) {
        super(name, xpos, ypos, env, fuelLevel);
    }

    @Override
    protected TWThought think() {
        long currentTime = getEnvironment().schedule.getSteps();

        // Combine updates to reduce frequent checks
        if (currentTime % DENSITY_UPDATE_INTERVAL == 0) {
            updateQuadrantDensities();
            broadcastStatus(); // Combine status update with density update
            broadcastDensityInfo();
        }

        // Use bitwise operations for faster interval checks
        if ((currentTime & 3) == 0) { // Equivalent to currentTime % 4 == 0
            broadcastNewDiscoveries();

            // Check quadrant changes periodically
            if (getCurrentQuadrantDensity() < 0.15) { // More persistent in current quadrant
                currentQuadrant = selectQuadrantByDensity();
            }
        }

        // Perform periodic cleanup
        cleanupOldData(currentTime);

        return getOptimizedMovement();
    }

    private double getCurrentQuadrantDensity() {
        return quadrantDensity.getOrDefault(currentQuadrant, 
            new HoleDensityTracker()).getDensity();
    }

    private TWThought getOptimizedMovement() {
        // Quick fuel check first
        if (getFuelLevel() < Parameters.defaultFuelLevel * 0.2) { // More aggressive
            return super.think();
        }

        // Cache environment dimensions
        int midX = getEnvironment().getxDimension() >> 1; // Faster division by 2
        int midY = getEnvironment().getyDimension() >> 1;

        // Quick quadrant check
        if (!isInQuadrant(getX(), getY(), midX, midY)) {
            return moveToQuadrantCenter(currentQuadrant);
        }

        // Find target only if we're in position
        TWEntity bestTarget = findBestTargetInQuadrant();
        if (bestTarget != null) {
            return moveTowardsLocation(bestTarget.getX(), bestTarget.getY());
        }

        // Simplified density check
        if (getCurrentQuadrantDensity() < 0.05) { // Stay longer in promising areas
            return moveToQuadrantCenter((currentQuadrant + 1) % QUADRANTS);
        }

        return getLocalSearchMovement();
    }

    private TWEntity findBestTargetInQuadrant() {
        ObjectGrid2D memoryGrid = getMemory().getMemoryGrid();
        int range = Parameters.defaultSensorRange;
        TWEntity bestTarget = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        int midX = getEnvironment().getxDimension() / 2;
        int midY = getEnvironment().getyDimension() / 2;

        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                int newX = getX() + dx;
                int newY = getY() + dy;
                
                if (!isInBounds(newX, newY) || !isInQuadrant(newX, newY, midX, midY)) {
                    continue;
                }
                
                Object obj = memoryGrid.get(newX, newY);
                if (obj instanceof TWHole) {
                    TWHole hole = (TWHole) obj;
                    double timeLeft = hole.getTimeLeft(getEnvironment().schedule.getTime());
                    double distance = Math.abs(dx) + Math.abs(dy);
                    double score = (timeLeft * timeLeft) / (distance + 0.5); // Favor holes with more time left
                    
                    if (score > bestScore) {
                        bestScore = score;
                        bestTarget = hole;
                    }
                }
            }
        }
        
        return bestTarget;
    }

    private TWThought moveToQuadrantCenter(int quadrant) {
        int midX = getEnvironment().getxDimension() / 2;
        int midY = getEnvironment().getyDimension() / 2;
        
        // Calculate quadrant center
        int targetX = (quadrant % 2 == 0) ? midX / 2 : midX + midX / 2;
        int targetY = (quadrant < 2) ? midY / 2 : midY + midY / 2;
        
        return moveTowardsLocation(targetX, targetY);
    }

    private TWThought getLocalSearchMovement() {
        int range = Parameters.defaultSensorRange;
        int bestX = getX(), bestY = getY();
        double bestScore = -1;

        // Search nearby cells for holes
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                int newX = getX() + dx;
                int newY = getY() + dy;
                if (isInBounds(newX, newY)) {
                    double score = evaluateLocation(newX, newY);
                    if (score > bestScore) {
                        bestScore = score;
                        bestX = newX;
                        bestY = newY;
                    }
                }
            }
        }

        return moveTowardsLocation(bestX, bestY);
    }

    private double evaluateLocation(int x, int y) {
        Object obj = getMemory().getMemoryGrid().get(x, y);
        if (obj instanceof TWHole) {
            TWHole hole = (TWHole) obj;
            return hole.getTimeLeft(getEnvironment().schedule.getTime());
        }
        return 0;
    }

    private TWThought moveTowardsLocation(int targetX, int targetY) {
        int dx = targetX - getX();
        int dy = targetY - getY();
        
        // If already at target, maintain position
        if (dx == 0 && dy == 0) {
            return new TWThought(TWAction.MOVE, TWDirection.Z);
        }

        // Prioritize horizontal movement first, then vertical
        TWDirection direction;
        int nextX = getX();
        int nextY = getY();

        if (dx != 0) {
            // Move horizontally first
            direction = dx > 0 ? TWDirection.E : TWDirection.W;
            nextX = getX() + Integer.signum(dx);
        } else {
            // Then move vertically
            direction = dy > 0 ? TWDirection.S : TWDirection.N;
            nextY = getY() + Integer.signum(dy);
        }

        // Verify the next position is valid
        if (isInBounds(nextX, nextY)) {
            return new TWThought(TWAction.MOVE, direction);
        }

        // If next position is invalid, stay in place
        return new TWThought(TWAction.MOVE, TWDirection.Z);
    }

    private void broadcastStatus() {
        String status = String.format("%sSTATUS:%s:%d,%d:%d:%f",
            BROADCAST_PREFIX,
            getName(),
            getX(),
            getY(),
            currentQuadrant,
            getFuelLevel()
        );
        getEnvironment().receiveMessage(new Message(getName(), "ALL", status));
    }

    private void broadcastNewDiscoveries() {
        ObjectGrid2D memoryGrid = getMemory().getMemoryGrid();
        int range = Parameters.defaultSensorRange;
        long currentTime = getEnvironment().schedule.getSteps();
        
        // Calculate quadrant bounds
        int midX = getEnvironment().getxDimension() / 2;
        int midY = getEnvironment().getyDimension() / 2;
        
        // Scan in the current quadrant with higher priority
        for (int r = 0; r <= range; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    int newX = getX() + dx;
                    int newY = getY() + dy;
                    
                    if (!isInQuadrant(newX, newY, midX, midY)) {
                        continue;
                    }
                    
                    if (shouldExplore(newX, newY, currentTime)) {
                        if (isInBounds(newX, newY)) {
                            broadcastEntityAtLocation(memoryGrid, newX, newY);
                            exploredTimestamps.put(new Point(newX, newY), currentTime);
                        }
                    }
                }
            }
        }
    }

    private boolean shouldExplore(int x, int y, long currentTime) {
        Point p = new Point(x, y);
        Long lastVisited = exploredTimestamps.get(p);
        return lastVisited == null || 
               (currentTime - lastVisited) >= REVISIT_THRESHOLD;
    }

    private boolean isInQuadrant(int x, int y, int midX, int midY) {
        boolean inUpperHalf = y < midY;
        boolean inLeftHalf = x < midX;
        
        switch (currentQuadrant) {
            case 0: return inUpperHalf && inLeftHalf;     // Top-left
            case 1: return inUpperHalf && !inLeftHalf;    // Top-right
            case 2: return !inUpperHalf && inLeftHalf;    // Bottom-left
            case 3: return !inUpperHalf && !inLeftHalf;   // Bottom-right
            default: return false;
        }
    }

    private boolean isInBounds(int x, int y) {
        return x >= 0 && x < getEnvironment().getxDimension() && 
               y >= 0 && y < getEnvironment().getyDimension();
    }

    private void broadcastEntityAtLocation(ObjectGrid2D memoryGrid, int x, int y) {
        Point p = new Point(x, y);
        // Only broadcast if location hasn't been broadcasted or needs refresh
        if (!shouldExplore(x, y, getEnvironment().schedule.getSteps())) {
            return;
        }

        Object obj = memoryGrid.get(x, y);
        String message = null;

        if (obj instanceof TWHole) {
            TWHole hole = (TWHole) obj;
            double timeLeft = hole.getTimeLeft(getEnvironment().schedule.getTime());
            // Only broadcast holes with significant time left
            if (timeLeft > 10) {
                message = String.format("%sHOLE:%d,%d:%f", 
                    BROADCAST_PREFIX, x, y, timeLeft);
                updateHoleSpawnRate(x, y);
            }
        } else if (obj instanceof TWTile) {
            message = String.format("%sTILE:%d,%d", 
                BROADCAST_PREFIX, x, y);
        } else if (obj instanceof TWFuelStation) {
            message = String.format("%sFUEL:%d,%d", 
                BROADCAST_PREFIX, x, y);
        }

        if (message != null) {
            getEnvironment().receiveMessage(new Message(getName(), "ALL", message));
            broadcastedLocations.add(p);
        }
    }

    private boolean isQuadrantOccupied(int quadrant) {
        // Remove old statuses (older than 10 steps)
        long currentTime = System.currentTimeMillis();
        teamStatus.entrySet().removeIf(e -> 
            (currentTime - e.getValue().timestamp) > (10 * 1000));

        // Check if any agent is in this quadrant
        return teamStatus.values().stream()
            .anyMatch(status -> 
                status.quadrant == quadrant && 
                !status.equals(getName()));
    }

    private int selectQuadrantByDensity() {
        if (currentQuadrant != -1 && !isQuadrantOvercrowded(currentQuadrant)) {
            HoleDensityTracker tracker = quadrantDensity.get(currentQuadrant);
            if (tracker != null && tracker.getDensity() > 0.2) {
                return currentQuadrant;
            }
        }

        Map<Integer, Double> quadrantScores = new HashMap<>();
        for (int q = 0; q < QUADRANTS; q++) {
            double score = calculateQuadrantScore(q);
            quadrantScores.put(q, score);
        }

        double totalScore = quadrantScores.values().stream().mapToDouble(Double::doubleValue).sum();
        double random = Math.random() * totalScore;
        double cumulative = 0.0;

        for (Map.Entry<Integer, Double> entry : quadrantScores.entrySet()) {
            cumulative += entry.getValue();
            if (random <= cumulative) {
                return entry.getKey();
            }
        }

        return (currentQuadrant + 1) % QUADRANTS;
    }

    private double calculateQuadrantScore(int quadrant) {
        HoleDensityTracker tracker = quadrantDensity.getOrDefault(quadrant, new HoleDensityTracker());
        double density = tracker.getDensity() * 2.0; // Increase weight of density

        long agentCount = teamStatus.values().stream()
            .filter(status -> status.quadrant == quadrant)
            .count();

        int midX = getEnvironment().getxDimension() / 2;
        int midY = getEnvironment().getyDimension() / 2;
        int quadrantCenterX = (quadrant % 2 == 0) ? midX / 2 : midX + midX / 2;
        int quadrantCenterY = (quadrant < 2) ? midY / 2 : midY + midY / 2;
        double distance = Math.sqrt(Math.pow(getX() - quadrantCenterX, 2) + 
                                  Math.pow(getY() - quadrantCenterY, 2));
        double distanceFactor = 1.0 / (1.0 + distance / 100.0);

        double agentDistributionFactor = 1.0 / Math.pow(agentCount + 1, 2);

        double score = (density * 3.0 + 0.2) * distanceFactor * agentDistributionFactor;
        score *= (0.9 + Math.random() * 0.2);

        return score;
    }

    private boolean isQuadrantOvercrowded(int quadrant) {
        long agentCount = teamStatus.values().stream()
            .filter(status -> status.quadrant == quadrant)
            .count();
        return agentCount >= Math.ceil(teamStatus.size() / 2.0);
    }

    private void updateHoleSpawnRate(int x, int y) {
        int midX = getEnvironment().getxDimension() / 2;
        int midY = getEnvironment().getyDimension() / 2;
        
        int quadrant = getQuadrantForLocation(x, y, midX, midY);
        quadrantHoleCount.merge(quadrant, 1, Integer::sum);
        
        long currentTime = getEnvironment().schedule.getSteps();
        double currentRate = quadrantHoleCount.get(quadrant) / 
                            (double)(currentTime % SPAWN_RATE_WINDOW + 1);
        
        quadrantSpawnRates.merge(quadrant, 
            currentRate, 
            (oldRate, newRate) -> (oldRate * SPAWN_RATE_DECAY + newRate * (1 - SPAWN_RATE_DECAY))
        );
    }

    private int getQuadrantForLocation(int x, int y, int midX, int midY) {
        boolean inUpperHalf = y < midY;
        boolean inLeftHalf = x < midX;
        
        if (inUpperHalf && inLeftHalf) return 0;      // Top-left
        if (inUpperHalf && !inLeftHalf) return 1;     // Top-right
        if (!inUpperHalf && inLeftHalf) return 2;     // Bottom-left
        return 3;                                     // Bottom-right
    }

    private void updateQuadrantDensities() {
        ObjectGrid2D memoryGrid = getMemory().getMemoryGrid();
        int midX = getEnvironment().getxDimension() / 2;
        int midY = getEnvironment().getyDimension() / 2;

        // Initialize trackers if needed
        for (int q = 0; q < QUADRANTS; q++) {
            quadrantDensity.putIfAbsent(q, new HoleDensityTracker());
        }

        // Scan current view
        for (int dx = -Parameters.defaultSensorRange; dx <= Parameters.defaultSensorRange; dx++) {
            for (int dy = -Parameters.defaultSensorRange; dy <= Parameters.defaultSensorRange; dy++) {
                int newX = getX() + dx;
                int newY = getY() + dy;
                
                if (isInBounds(newX, newY)) {
                    int quadrant = getQuadrantForLocation(newX, newY, midX, midY);
                    boolean hasHole = memoryGrid.get(newX, newY) instanceof TWHole;
                    quadrantDensity.get(quadrant).addObservation(hasHole);
                }
            }
        }

        // Apply decay to all quadrants
        quadrantDensity.values().forEach(tracker -> tracker.decay(0.95));
    }

    private void broadcastDensityInfo() {
        for (Map.Entry<Integer, HoleDensityTracker> entry : quadrantDensity.entrySet()) {
            int quadrant = entry.getKey();
            double density = entry.getValue().getDensity();
            String message = String.format("%sDENSITY:%d:%f",
                BROADCAST_PREFIX,
                quadrant,
                density
            );
            getEnvironment().receiveMessage(new Message(getName(), "ALL", message));
        }
    }

    private void cleanupOldData(long currentTime) {
        // Clean up old broadcast locations
        if ((currentTime & 31) == 0) { // Every 32 steps
            broadcastedLocations.clear();
            exploredTimestamps.entrySet().removeIf(e -> 
                currentTime - e.getValue() > REVISIT_THRESHOLD);
        }
    }

    public void receiveMessage(Message m) {
        String content = m.getMessage();
        if (content.startsWith(BROADCAST_PREFIX)) {
            if (content.contains("STATUS:")) {
                handleStatusMessage(content);
            } else if (content.contains("DENSITY:")) {
                handleDensityMessage(content);
            }
        }
    }

    private void handleStatusMessage(String message) {
        // Parse: INFO:STATUS:agentName:x,y:quadrant:fuelLevel
        String[] parts = message.substring(BROADCAST_PREFIX.length()).split(":");
        if (parts.length == 5 && parts[0].equals("STATUS")) {
            String agentName = parts[1];
            String[] coords = parts[2].split(",");
            int x = Integer.parseInt(coords[0]);
            int y = Integer.parseInt(coords[1]);
            int quadrant = Integer.parseInt(parts[3]);
            double fuelLevel = Double.parseDouble(parts[4]);

            teamStatus.put(agentName, new AgentStatus(x, y, quadrant, fuelLevel));
        }
    }

    private void handleDensityMessage(String message) {
        String[] parts = message.substring(BROADCAST_PREFIX.length()).split(":");
        if (parts.length == 3 && parts[0].equals("DENSITY")) {
            int quadrant = Integer.parseInt(parts[1]);
            double receivedDensity = Double.parseDouble(parts[2]);
            
            // Update the density tracker with weighted averaging
            HoleDensityTracker tracker = quadrantDensity.computeIfAbsent(quadrant, 
                k -> new HoleDensityTracker());
            
            // Add multiple observations based on the received density
            int observationCount = 10; // Use multiple observations for smoother integration
            for (int i = 0; i < observationCount; i++) {
                tracker.addObservation(Math.random() < receivedDensity);
            }
            
            // Debug output to verify density relay
            System.out.println(String.format("Agent %s received density %.2f for quadrant %d", 
                getName(), receivedDensity, quadrant));
        }
    }
}
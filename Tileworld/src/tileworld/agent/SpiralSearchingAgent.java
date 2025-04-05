// 📄 File: SpiralSearchingAgent.java
package tileworld.agent;

import tileworld.environment.TWDirection;
import tileworld.environment.TWFuelStation;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public abstract class SpiralSearchingAgent extends TWAgent {
    protected int stepCount = 0;
    protected TWDirection currentDir;
    protected int moveLength = 1;
    protected int movesInCurrentLength = 0;
    protected int directionChanges = 0;
    protected final int stepScale = 6;

    protected String name;

    public SpiralSearchingAgent(String name, int xpos, int ypos, tileworld.environment.TWEnvironment env, double fuelLevel, TWDirection initialDir) {
        super(xpos, ypos, env, fuelLevel);
        this.name = name;
        this.currentDir = initialDir;
        this.memory = new SmartMemory(this, env.schedule, env.getxDimension(), env.getyDimension());
    }

    public String getName() {
        return name;
    }

    private boolean isHeadingToFuelStation = false;
    private int fuelStationX = -1;
    private int fuelStationY = -1;
    private boolean hasReachedFuelStation = false;
    private boolean hasRefueled = false;
    private int broadcastAttempts = 0;
    private static final int MAX_BROADCAST_ATTEMPTS = 3;

    private Set<Point> knownObstacles = new HashSet<>(); // Add obstacle memory

    private static class Point {
        final int x, y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Point)) return false;
            Point p = (Point) o;
            return x == p.x && y == p.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    @Override
    protected TWThought think() {
        // First check messages regardless of current state
        if (!isHeadingToFuelStation) {
            for (Message m : getEnvironment().getMessages()) {
                if (m.getMessage().startsWith("FUEL:")) {
                    try {
                        String[] coords = m.getMessage().substring(5).split(",");
                        fuelStationX = Integer.parseInt(coords[0]);
                        fuelStationY = Integer.parseInt(coords[1]);
                        isHeadingToFuelStation = true;
                        TWAgent.setFuelStationFound();
                        System.out.println(name + " received fuel station coordinates: " + fuelStationX + "," + fuelStationY);
                        return new TWThought(TWAction.MOVE, calculateDirectionToFuelStation());
                    } catch (Exception e) {
                        continue; // Try next message if this one fails
                    }
                }
            }
        }

        // Handle fuel station discovery
        TWFuelStation station = detectFuelStationInRangeFromMemory();
        if (station != null && broadcastAttempts < MAX_BROADCAST_ATTEMPTS) {
            TWAgent.setFuelStationFound();
            fuelStationX = station.getX();
            fuelStationY = station.getY();
            isHeadingToFuelStation = true;
            String message = "FUEL:" + station.getX() + "," + station.getY();
            getEnvironment().receiveMessage(new Message(name, "ALL", message));
            broadcastAttempts++;
            System.out.println(name + " broadcasting fuel station location (attempt " + broadcastAttempts + ")");
            return new TWThought(TWAction.MOVE, calculateDirectionToFuelStation());
        }

        // Ensure agents stay still if they have reached the fuel station and refueled
        if (hasReachedFuelStation && hasRefueled) {
            return new TWThought(TWAction.MOVE, TWDirection.Z);
        }

        // If at fuel station but haven't refueled yet
        if (hasReachedFuelStation && !hasRefueled) {
            hasRefueled = true;
            System.out.println(name + " refueling once at fuel station");
            return new TWThought(TWAction.REFUEL, TWDirection.Z);
        }

        // If we're heading to fuel station, continue that path
        if (isHeadingToFuelStation) {
            // Check if we reached the fuel station
            if (getX() == fuelStationX && getY() == fuelStationY) {
                hasReachedFuelStation = true;
                return new TWThought(TWAction.REFUEL, TWDirection.Z);
            }
            return new TWThought(TWAction.MOVE, calculateDirectionToFuelStation());
        }

        // Only continue spiral search if we haven't found or received fuel station location
        if (!isHeadingToFuelStation) {
            if (fuelLevel <= 100 || stepCount >= 400) {
                return new TWThought(TWAction.MOVE, TWDirection.Z);
            }

            // Spiral search movement
            TWDirection dir = currentDir;
            movesInCurrentLength++;
            if (movesInCurrentLength == moveLength * stepScale) {
                movesInCurrentLength = 0;
                currentDir = currentDir.next();
                directionChanges++;
                if (directionChanges % 2 == 0) {
                    moveLength++;
                }
            }
            return new TWThought(TWAction.MOVE, dir);
        }

        return new TWThought(TWAction.MOVE, TWDirection.Z);
    }

    @Override
    protected void act(TWThought thought) {
        if (thought.getAction() == TWAction.REFUEL) {
            try {
                refuel();
                System.out.println(name + " refueled successfully");
            } catch (Exception ignored) {
                System.out.println(name + " failed to refuel");
            }
            return;
        }

        TWDirection direction = thought.getDirection();
        if (direction == TWDirection.Z) {
            return;
        }

        // Try to move with immediate obstacle avoidance
        if (!tryMoveWithAvoidance(direction)) {
            System.out.println(name + " blocked at " + getX() + "," + getY());
        }
    }

    private boolean tryMoveWithAvoidance(TWDirection primaryDirection) {
        // First try the primary direction
        if (tryMove(primaryDirection)) {
            return true;
        }

        // If primary direction fails, try perpendicular directions
        TWDirection[] perpendicularDirs = getPerpendicularDirections(primaryDirection);
        for (TWDirection altDir : perpendicularDirs) {
            if (tryMove(altDir)) {
                return true;
            }
        }

        // If all perpendicular directions fail, try opposite direction
        TWDirection opposite = getOppositeDirection(primaryDirection);
        return tryMove(opposite);
    }

    private TWDirection getOppositeDirection(TWDirection dir) {
        switch (dir) {
            case N: return TWDirection.S;
            case S: return TWDirection.N;
            case E: return TWDirection.W;
            case W: return TWDirection.E;
            default: return TWDirection.Z;
        }
    }

    private boolean tryMove(TWDirection direction) {
        try {
            move(direction);
            stepCount++;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private TWDirection[] getPerpendicularDirections(TWDirection dir) {
        switch (dir) {
            case N:
            case S:
                // If moving vertically, prioritize east or west based on fuel station location
                if (fuelStationX > getX()) {
                    return new TWDirection[]{TWDirection.E, TWDirection.W};
                } else {
                    return new TWDirection[]{TWDirection.W, TWDirection.E};
                }
            case E:
            case W:
                // If moving horizontally, prioritize north or south based on fuel station location
                if (fuelStationY > getY()) {
                    return new TWDirection[]{TWDirection.S, TWDirection.N};
                } else {
                    return new TWDirection[]{TWDirection.N, TWDirection.S};
                }
            default:
                return new TWDirection[]{};
        }
    }

    private TWDirection calculateDirectionToFuelStation() {
        int dx = fuelStationX - getX();
        int dy = fuelStationY - getY();

        // At fuel station
        if (dx == 0 && dy == 0) {
            return TWDirection.Z;
        }

        // Alternate between X and Y movement for diagonal paths
        if (stepCount % 2 == 0) {
            if (dx != 0) {
                return dx > 0 ? TWDirection.E : TWDirection.W;
            } else {
                return dy > 0 ? TWDirection.S : TWDirection.N;
            }
        } else {
            if (dy != 0) {
                return dy > 0 ? TWDirection.S : TWDirection.N;
            } else {
                return dx > 0 ? TWDirection.E : TWDirection.W;
            }
        }
    }
}
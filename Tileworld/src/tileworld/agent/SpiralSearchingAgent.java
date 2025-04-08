package tileworld.agent;
import tileworld.environment.TWDirection;
import tileworld.environment.TWFuelStation;
import tileworld.EnvParameters;
import tileworld.environment.TWTile;
import tileworld.environment.TWHole;
import tileworld.planners.AstarPathGenerator;
import tileworld.planners.TWPath;
import tileworld.planners.TWPathStep;

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
    private boolean isHeadingToFuelStation = false;
    private int fuelStationX = -1;
    private int fuelStationY = -1;
    private int broadcastAttempts = 0;
    private boolean isPickingUpTile = false;
    private TWTile tileToBePickedUp = null;
    private TWHole holeToBeFilled = null;
    private boolean fillHole = false;
    private double MIN_FUEL_LEVEL;
    private static final int MAX_BROADCAST_ATTEMPTS = 3;
    private AstarPathGenerator pathGenerator; // Initialize in constructor
    private TWPath currentPath = null;
    private int currentPathTargetX = -1;
    private int currentPathTargetY = -1;
    
    public SpiralSearchingAgent(String name, int xpos, int ypos, tileworld.environment.TWEnvironment env, double fuelLevel, TWDirection initialDir) {
        super(xpos, ypos, env, fuelLevel);
        this.name = name;
        this.currentDir = initialDir;
        this.memory = new SmartMemory(this, env.schedule, env.getxDimension(), env.getyDimension());
        this.pathGenerator = new AstarPathGenerator(env, this, env.getxDimension()*env.getyDimension());
        setMinFuelLevel();
    }
    public String getName() {
        return name;
    }
    
    
    @Override
    protected TWThought think() {
    	if (fuelStationX==-1) {
            for (Message m : getEnvironment().getMessages()) {
                if (m.getMessage().startsWith("FUEL:")) {
                    try {
                        String[] coords = m.getMessage().substring(5).split(",");
                        fuelStationX = Integer.parseInt(coords[0]);
                        fuelStationY = Integer.parseInt(coords[1]);
                        ((SmartMemory) this.memory).setFuelStationLocation(new TWFuelStation(fuelStationX, fuelStationY, getEnvironment()));;
//                        System.out.println(name + " received fuel station coordinates: " + fuelStationX + "," + fuelStationY);
                        return new TWThought(TWAction.MOVE, TWDirection.Z);
                    } catch (Exception e) {
                        continue; // Try next message if this one fails
                    }
                }
            }
            return findFuelStation();
        }
        broadcastFuelStationLocation();
        // --- Goal Prioritization ---

        // 1. Refueling Goal
        if (getFuelLevel() < MIN_FUEL_LEVEL || isHeadingToFuelStation) { // isHeadingToFuelStation might be replaced by checking currentPath target
             System.out.println("STAGE: REFUELLING");
             isHeadingToFuelStation = true; // Keep this to remember the high-level goal

             if (getEnvironment().inFuelStation(this)) {
                 currentPath = null; // Reached destination
                 isHeadingToFuelStation = false; // Goal achieved (will refuel in act)
                 return new TWThought(TWAction.REFUEL, TWDirection.Z);
             }

             if (currentPath == null || currentPathTargetX != fuelStationX || currentPathTargetY != fuelStationY) {
                 System.out.println(name + " calculating path to Fuel Station: " + fuelStationX + "," + fuelStationY);
                 currentPath = pathGenerator.findPath(getX(), getY(), fuelStationX, fuelStationY);
                 currentPathTargetX = fuelStationX;
                 currentPathTargetY = fuelStationY;
                 if (currentPath == null) {
                     System.out.println(name + " CANNOT FIND PATH to Fuel Station!");
                     isHeadingToFuelStation = false; // Cannot reach
                     return fallbackMovement(); // Fallback behavior
                 }
             }
             // If we have a path, follow it
             return followCurrentPath();
        }

        // 2. Pickup Tile Goal (if not carrying max tiles and not currently filling holes)
        if (carriedTiles.size() < 3 && !fillHole) {
             System.out.println("STAGE: SEEKING/PICKING UP TILES");
             if (isPickingUpTile && tileToBePickedUp != null) {
                  if (this.sameLocation(tileToBePickedUp)) {
                       currentPath = null; 
                       return new TWThought(TWAction.PICKUP, TWDirection.Z);
                  }
                  if (currentPath == null || currentPathTargetX != tileToBePickedUp.getX() || currentPathTargetY != tileToBePickedUp.getY()) {
                       System.out.println(name + " calculating path to Tile: " + tileToBePickedUp.getX() + "," + tileToBePickedUp.getY());
                       currentPath = pathGenerator.findPath(getX(), getY(), tileToBePickedUp.getX(), tileToBePickedUp.getY());
                       currentPathTargetX = tileToBePickedUp.getX();
                       currentPathTargetY = tileToBePickedUp.getY();
                       
                       if (currentPath == null) {
                            System.out.println(name + " CANNOT FIND PATH to Tile!");
                            this.memory.removeObject(tileToBePickedUp); 
                            tileToBePickedUp = null;
                            isPickingUpTile = false;
                            return fallbackMovement(); // Fallback
                       }
                  }
                  return followCurrentPath();
             } else {
                 // Find a new tile if not already targeting one
                 tileToBePickedUp = this.memory.getNearbyTile(getX(), getY(), 20);
                 if (tileToBePickedUp != null) {
                     isPickingUpTile = true;
                     // Path calculation will happen in the next cycle via the logic above
                     return new TWThought(TWAction.MOVE, TWDirection.Z); // Do nothing this cycle, wait for path calc next time
                 } else {
                     isPickingUpTile = false; // Ensure flag is off if no tile found
                     return fallbackMovement(); // Explore if no nearby tile
                 }
             }
        }

        // 3. Fill Hole Goal (if carrying tiles)
        if (carriedTiles.size() > 0 || fillHole) { // Should probably be fillHole && carriedTiles > 0
            System.out.println("STAGE: SEEKING/FILLING HOLES");
             fillHole = true; // Remember the high-level goal

             // Are we already moving to a hole?
             if (holeToBeFilled != null) {
                  if (this.sameLocation(holeToBeFilled)) {
                       currentPath = null; // Reached destination
                       // fillHole might be reset in act after successful putdown if out of tiles
                       return new TWThought(TWAction.PUTDOWN, TWDirection.Z);
                  }
                 // Continue path or calculate anew if needed
                 if (currentPath == null || currentPathTargetX != holeToBeFilled.getX() || currentPathTargetY != holeToBeFilled.getY()) {
                     System.out.println(name + " calculating path to Hole: " + holeToBeFilled.getX() + "," + holeToBeFilled.getY());
                     currentPath = pathGenerator.findPath(getX(), getY(), holeToBeFilled.getX(), holeToBeFilled.getY());
                     currentPathTargetX = holeToBeFilled.getX();
                     currentPathTargetY = holeToBeFilled.getY();
                     if (currentPath == null) {
                          System.out.println(name + " CANNOT FIND PATH to Hole!");
                          this.memory.removeObject(holeToBeFilled); // Forget unreachable hole
                          holeToBeFilled = null;
                          // Should we stop filling holes? Maybe look for another?
                          return fallbackMovement(); // Fallback
                     }
                 }
                 return followCurrentPath();
            } else {
                 // Find a new hole if not already targeting one
                 holeToBeFilled = this.memory.getNearbyHole(getX(), getY(), 20);
                 if (holeToBeFilled != null) {
                     // Path calculation will happen in the next cycle
                     return new TWThought(TWAction.MOVE, TWDirection.Z); // Wait for path calc
                 } else {
                     // No nearby hole found, maybe explore?
                     fillHole = false; // Stop trying to fill if none are found nearby? Decision needed.
                     return fallbackMovement();
                 }
             }
        }

        // --- Default Action: Explore ---
        System.out.println("STAGE: EXPLORING (SPIRAL)");
        currentPath = null; // Ensure no path is active if we default to spiral
        return fallbackMovement();
    }


    private TWThought followCurrentPath() {
        if (currentPath == null) {
            System.err.println(name + " Error: followCurrentPath called with null path. Reverting to fallback.");
            currentPathTargetX = -1;
            currentPathTargetY = -1;
            return fallbackMovement(); // Use fallback behavior
        }

        if (!currentPath.hasNext()) {
            System.out.println(name + " followCurrentPath found an empty path (likely reached destination).");
            currentPath = null;
            currentPathTargetX = -1;
            currentPathTargetY = -1;
            return new TWThought(TWAction.MOVE, TWDirection.Z);
        }
        TWPathStep nextStep = currentPath.popNext();
        TWDirection direction = nextStep.getDirection();
        return new TWThought(TWAction.MOVE, direction);
    }

        
    private void broadcastFuelStationLocation() {
    	if(broadcastAttempts < MAX_BROADCAST_ATTEMPTS) {
	    	String message = "FUEL:" + fuelStationX + "," + fuelStationY;
	        getEnvironment().receiveMessage(new Message(name, "ALL", message));
	        broadcastAttempts++;
//	        System.out.println(name + " broadcasting fuel station location (attempt " + broadcastAttempts + ")");
    	}
    }
    
    
    protected TWThought fallbackMovement() {
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
    
    
    private TWThought findFuelStation() {
    	if (((SmartMemory) this.memory).isFuelStationFound()){
    		fuelStationX = ((SmartMemory) this.memory).getFuelStationX();
    		fuelStationY = ((SmartMemory) this.memory).getFuelStationY();
    		return new TWThought(TWAction.MOVE, TWDirection.Z);
    	}
    	return fallbackMovement();
    }
    
    
    @Override
    protected void act(TWThought thought) {
        TWDirection direction = thought.getDirection();
        TWAction action = thought.getAction();

        if (action == TWAction.REFUEL) {
            try {
                refuel();
                System.out.println(name + " refueled successfully");
            } catch (Exception ignored) {
                System.out.println(name + " failed to refuel");
            }
            isHeadingToFuelStation = false; // Reset goal flag
            currentPath = null; // Clear path, goal achieved or failed
            currentPathTargetX = -1;
            currentPathTargetY = -1;
            return; 
        }

        if (action == TWAction.PICKUP) {
            if (tileToBePickedUp == null) {
                 System.out.println(name + " tried to pickup but tileToBePickedUp is null!");
                 isPickingUpTile = false; // Reset state
                 currentPath = null; // Clear any path associated with this failed goal
                 currentPathTargetX = -1;
                 currentPathTargetY = -1;
                 return;
            }
            if (getEnvironment().canPickupTile(tileToBePickedUp, this)) {
                try {
                    this.pickUpTile(tileToBePickedUp);
                    this.memory.removeObject(tileToBePickedUp); // Remove from memory ONLY on successful pickup
                    System.out.println(name + " picked up a tile. Contains " + carriedTiles.size() + " tiles");
                    if (carriedTiles.size() == 3) {
                        fillHole = true;
                        System.out.println(name + " will fill holes now");
                    }
                } catch (Exception e) {
                     System.out.println(name + " Exception during pickup for tile at ("+ tileToBePickedUp.getX() + "," + tileToBePickedUp.getY() +"): " + e.getMessage());
                     this.memory.removeObject(tileToBePickedUp);
                }
            } else {
                System.out.println(name + " Cannot pickup tile at ("+ tileToBePickedUp.getX() + "," + tileToBePickedUp.getY() +"), environment check failed.");
                this.memory.removeObject(tileToBePickedUp);
            }
            tileToBePickedUp = null;
            isPickingUpTile = false;
            currentPath = null; // Path objective completed or failed
            currentPathTargetX = -1;
            currentPathTargetY = -1;
            return; 
        }
        if (action == TWAction.PUTDOWN) {
            if (holeToBeFilled == null) {
                System.out.println(name + " tried to putdown but holeToBeFilled is null!");
                 currentPath = null; // Clear any path associated with this failed goal
                 currentPathTargetX = -1;
                 currentPathTargetY = -1;
                 return;
            }
            if (getEnvironment().canPutdownTile(holeToBeFilled, this)) {
                 try {
                      this.putTileInHole(holeToBeFilled);
                      this.memory.removeObject(holeToBeFilled); // Remove from memory ONLY on success
                      System.out.println(name + " has filled a hole at ("+ holeToBeFilled.getX() + "," + holeToBeFilled.getY() + ")");
                      if (carriedTiles.size() == 0) {
                          fillHole = false; // Stop filling if out of tiles
                          System.out.println(name + " has no more tiles, stopping hole filling mode.");
                      }
                 } catch (Exception e) {
                      System.out.println(name + " Exception during putdown at ("+ holeToBeFilled.getX() + "," + holeToBeFilled.getY() +"): " + e.getMessage());
                      this.memory.removeObject(holeToBeFilled);
                 }
            } else {
                 System.out.println(name + " Cannot putdown tile at ("+ holeToBeFilled.getX() + "," + holeToBeFilled.getY() +"), environment check failed.");
                 this.memory.removeObject(holeToBeFilled); // Remove invalid hole from memory
            }
            holeToBeFilled = null; // Current target processed
            currentPath = null; // Path objective completed or failed
            currentPathTargetX = -1;
            currentPathTargetY = -1;
            return; 
        }
        if (action == TWAction.MOVE) {
            if (direction == TWDirection.Z) {
                return;
            }
            try {
            	move(direction);
            	stepCount++;
            } catch (Exception e) {
            	currentPath = null;
            	currentPathTargetX = -1;
            	currentPathTargetY = -1;
            }
            return;
        }
        // If action is none of the above (shouldn't happen with current TWAction types)
        System.err.println(name + " Received unknown action type in act(): " + action);
    }
    
    /**
     * Calculates the minimum fuel level required for an agent to reach the fueling station.
     * This considers both the direct travel distance and the additional steps required 
     * due to obstacles encountered along the way.
     * 
     * @param x The x-coordinate of the fueling station.
     * @param y The y-coordinate of the fueling station.
     * @param mean The average object creation time (mean) that determines how obstacles 
     *             are distributed in the environment.
     * @param gridSize The total grid size (environment size) represented as x * y.
     * @return The minimum fuel level required to reach the fueling station, factoring in obstacles.
     */
    protected void setMinFuelLevel() {
        // Calculate the direct distance to the fueling station
    	int x = this.getEnvironment().getxDimension();
    	int y = this.getEnvironment().getyDimension();
    	double obstacleMean = EnvParameters.obstacleMean;
        double maxDiagonalDistance = x + y;
        
        // Calculate the expected number of obstacles encountered along the path
        double obstaclesEncountered = (maxDiagonalDistance) / (obstacleMean * (x*y));
        
        // Each obstacle adds 2 extra steps to the path
        double extraFuelForObstacles = obstaclesEncountered * 2;
        
        // Total fuel level is the direct distance plus the extra fuel due to obstacles
        double minFuelLevel = maxDiagonalDistance + extraFuelForObstacles;
        
        MIN_FUEL_LEVEL = minFuelLevel;
        System.out.println(name + "'s MIN_FUEL_LEVEL is: " + MIN_FUEL_LEVEL);
    }
}
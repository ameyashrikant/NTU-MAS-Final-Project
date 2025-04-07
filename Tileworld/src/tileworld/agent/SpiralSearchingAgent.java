package tileworld.agent;
import tileworld.environment.TWDirection;
import tileworld.environment.TWFuelStation;
import tileworld.Parameters;
import tileworld.environment.TWTile;
import tileworld.environment.TWHole;

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
    public SpiralSearchingAgent(String name, int xpos, int ypos, tileworld.environment.TWEnvironment env, double fuelLevel, TWDirection initialDir) {
        super(xpos, ypos, env, fuelLevel);
        this.name = name;
        this.currentDir = initialDir;
        this.memory = new SmartMemory(this, env.schedule, env.getxDimension(), env.getyDimension());
        setMinFuelLevel();
    }
    public String getName() {
        return name;
    }
    @Override
    protected TWThought think() {
        // First check messages regardless of current state
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
    	if (getFuelLevel()<MIN_FUEL_LEVEL && !isHeadingToFuelStation) {
    		System.out.println("STAGE: REFUELLING");
    		isHeadingToFuelStation = true;
    		return new TWThought(TWAction.MOVE, calculateDirectionToXY(fuelStationX, fuelStationY));
    	}
    	
    	if (isHeadingToFuelStation) {
    		if (getEnvironment().inFuelStation(this)) {
                return new TWThought(TWAction.REFUEL, TWDirection.Z);
            }
    		return new TWThought(TWAction.MOVE, calculateDirectionToXY(fuelStationX, fuelStationY));
    	}
    	// pick up tiles
    	if (carriedTiles.size()<3 && !isPickingUpTile && !fillHole) {
    		System.out.println("STAGE: PICKING UP TILES");
    		tileToBePickedUp = this.memory.getNearbyTile(getX(), getY(), 20);
    		if(tileToBePickedUp!=null) {
//    			System.out.println(name + " found a tile nearby, going to pick it up. Currently it has " + carriedTiles.size());
    			isPickingUpTile = true;
    			return new TWThought(TWAction.MOVE, calculateDirectionToXY(tileToBePickedUp.getX(), tileToBePickedUp.getY()));
    		}
    		else {
    			return moveSpiral();
    		}
    	}
    	if (isPickingUpTile) {
    		if (this.sameLocation(tileToBePickedUp)){
    			return new TWThought(TWAction.PICKUP, TWDirection.Z);
    		}
    		else {
    			return new TWThought(TWAction.MOVE, calculateDirectionToXY(tileToBePickedUp.getX(), tileToBePickedUp.getY()));
    		}
    	}
    	
    	if (fillHole) {
    		System.out.println("STAGE: FILLING HOLES");
    		if (holeToBeFilled == null) {
    			holeToBeFilled = this.memory.getNearbyHole(getX(), getY(), 20);
    			if(holeToBeFilled != null) {
//    				System.out.println(name + " found a hole neaby, going to fill it");
    				return new TWThought(TWAction.MOVE, calculateDirectionToXY(holeToBeFilled.getX(), holeToBeFilled.getY()));
    			}
    			else {
//    				System.out.println(name + " no hole nearby");
    				return moveSpiral();
    			}
    		}
    		else {
    			if (this.sameLocation(holeToBeFilled)) {
    				return new TWThought(TWAction.PUTDOWN, TWDirection.Z);
    			}
    			else {
    				return new TWThought(TWAction.MOVE, calculateDirectionToXY(holeToBeFilled.getX(), holeToBeFilled.getY()));
    			}
    		}
    	}
    	System.out.println("Here");
        return moveSpiral();
    }
    
    private void broadcastFuelStationLocation() {
    	if(broadcastAttempts < MAX_BROADCAST_ATTEMPTS) {
	    	String message = "FUEL:" + fuelStationX + "," + fuelStationY;
	        getEnvironment().receiveMessage(new Message(name, "ALL", message));
	        broadcastAttempts++;
//	        System.out.println(name + " broadcasting fuel station location (attempt " + broadcastAttempts + ")");
    	}
    }
    
    
    private TWThought moveSpiral() {
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
    	return moveSpiral();
    }
    
    @Override
    protected void act(TWThought thought) {
    	TWDirection direction = thought.getDirection();
        TWAction action = thought.getAction();
        if (action == TWAction.REFUEL) {
            try {
            	refuel();
            	isHeadingToFuelStation = false;
                System.out.println(name + " refueled successfully");
            } catch (Exception ignored) {
                System.out.println(name + " failed to refuel");
            }
            return;
        }
        
        
        if(action == TWAction.PICKUP) {
        	if (getEnvironment().canPickupTile(tileToBePickedUp, this)) {
        		this.pickUpTile(tileToBePickedUp);
        		this.memory.removeObject(tileToBePickedUp);
        		tileToBePickedUp = null;
        		isPickingUpTile = false;
//        		System.out.println(name + " picked up a tile. Contains " + carriedTiles.size() + " tiles");
//        		System.out.println(name + " current direction: " + currentDir);
        		if (carriedTiles.size()==3) {
        			fillHole = true;
        			System.out.println(name + " will fill holes now");
        		}
        	}
        	else {
        		this.memory.removeObject(tileToBePickedUp);
        		tileToBePickedUp = null;
        		isPickingUpTile = false;
        	}
        	return;
        }
        
        if (action == TWAction.PUTDOWN) {
        	if (getEnvironment().canPutdownTile(holeToBeFilled, this)) {
        		this.putTileInHole(holeToBeFilled);
        		this.memory.removeObject(holeToBeFilled);
        		holeToBeFilled = null;
//        		System.out.println(name + " has filled a hole");
        		if (carriedTiles.size()==0) {
        			fillHole = false;
        		}
        	}
        	else {
        		this.memory.removeObject(holeToBeFilled);
        		holeToBeFilled = null;
//        		fillHole = false;
        	}
        	return;
        }
        
        if (action==TWAction.MOVE) {
        	if(direction==TWDirection.Z) {
        		return;
        	}
        	else {
        		if(tryMoveWithAvoidance(direction)) {
        			return;
        		}
        		else {
        			System.out.println(name + " got stuck");
        		}
        	}
        }
        
    }
    private boolean tryMoveWithAvoidance(TWDirection primaryDirection) {
    	int MAX_RETRY = 3;
    	for(int i=0; i<MAX_RETRY; i++) {
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
	        if(tryMove(opposite)) {
	        	return true;
	        }
    	}
    	return false;
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
    private TWDirection calculateDirectionToXY(int x, int y) {
        int dx = x - getX();
        int dy = y - getY();
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
    	int x = Parameters.xDimension;
    	int y = Parameters.yDimension;
    	double obstacleMean = Parameters.obstacleMean;
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
package tileworld.agent;
import sim.util.Bag;
import sim.util.IntBag;
import tileworld.environment.TWFuelStation;

public class SmartMemory extends TWAgentWorkingMemory {
	private int fuelStationX = -1;
	private int fuelStationY = -1;
	private TWFuelStation cachedFuelStation = null;

    public SmartMemory(TWAgent agent, sim.engine.Schedule schedule, int xDim, int yDim) {
        super(agent, schedule, xDim, yDim);
    }

    /**
     * Convenience constructor that takes just the agent and extracts other parameters
     */
    public SmartMemory(TWAgent agent) {
        super(agent, 
              agent.getEnvironment().schedule, 
              agent.getEnvironment().getxDimension(), 
              agent.getEnvironment().getyDimension());
        
        // Initialize fields
        this.fuelStationX = -1;
        this.fuelStationY = -1;
        this.cachedFuelStation = null;
    }

    @Override
    public void updateMemory(Bag sensedObjects, IntBag objectXCoords, IntBag objectYCoords, Bag sensedAgents, IntBag agentXCoords, IntBag agentYCoords) {
        super.updateMemory(sensedObjects, objectXCoords, objectYCoords, sensedAgents, agentXCoords, agentYCoords);
        // Add support for detecting fuel station
        if (isFuelStationFound()) {
        	return;
        }
        for (int i = 0; i < sensedObjects.size(); i++) {
            Object o = sensedObjects.get(i);
            if (o instanceof TWFuelStation) {
                TWFuelStation tf = (TWFuelStation) o;
                this.getMemoryGrid().set(tf.getX(), tf.getY(), tf);
                fuelStationX = tf.getX();
                fuelStationY = tf.getY();
                System.out.println("Fuel station is present at: (" + fuelStationX + ", " + fuelStationY + ")");
                break;
            }       
        }
    }
    
    public boolean isFuelStationFound() {
    	return fuelStationX != -1;
    }

    public void setFuelStationLocation(TWFuelStation o) {
    	fuelStationX = o.getX();
    	fuelStationY = o.getY();
    }

    public int getFuelStationX() {
    	return fuelStationX;
    }

    public int getFuelStationY() {
    	return fuelStationY;
    }

    /**
     * Get the fuel station object from memory
     * @return The fuel station object, or null if not found
     */
    public TWFuelStation getFuelStation() {
        // If we've already found and cached the fuel station, return it
        if (cachedFuelStation != null) {
            return cachedFuelStation;
        }
        
        // If we know the coordinates but don't have the object cached
        if (isFuelStationFound()) {
            // Look in the memory grid
            Object obj = this.getMemoryGrid().get(fuelStationX, fuelStationY);
            if (obj instanceof TWFuelStation) {
                cachedFuelStation = (TWFuelStation) obj;
                return cachedFuelStation;
            }
        }
        
        return null;
    }
}
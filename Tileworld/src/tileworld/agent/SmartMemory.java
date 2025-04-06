package tileworld.agent;
import sim.util.Bag;
import sim.util.IntBag;
import tileworld.environment.TWFuelStation;
public class SmartMemory extends TWAgentWorkingMemory {
	private int fuelStationX = -1;
	private int fuelStationY = -1;
    public SmartMemory(TWAgent agent, sim.engine.Schedule schedule, int xDim, int yDim) {
        super(agent, schedule, xDim, yDim);
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
    	return fuelStationX!=-1;
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
}
// 📄 File: SmartMemory.java
package tileworld.agent;

import sim.util.Bag;
import sim.util.IntBag;
import tileworld.environment.TWFuelStation;
import tileworld.environment.TWEntity;
import tileworld.environment.TWObject;
import tileworld.environment.TWObstacle;

public class SmartMemory extends TWAgentWorkingMemory {

    public SmartMemory(TWAgent agent, sim.engine.Schedule schedule, int xDim, int yDim) {
        super(agent, schedule, xDim, yDim);
    }

    @Override
    public void updateMemory(Bag sensedObjects, IntBag objectXCoords, IntBag objectYCoords, Bag sensedAgents, IntBag agentXCoords, IntBag agentYCoords) {
        super.updateMemory(sensedObjects, objectXCoords, objectYCoords, sensedAgents, agentXCoords, agentYCoords);

        // Add support for detecting fuel station
        for (int i = 0; i < sensedObjects.size(); i++) {
            Object o = sensedObjects.get(i);
            if (o instanceof TWFuelStation) {
                TWFuelStation tf = (TWFuelStation) o;
                this.getMemoryGrid().set(tf.getX(), tf.getY(), tf);
            }
            
        }
    }
    
}

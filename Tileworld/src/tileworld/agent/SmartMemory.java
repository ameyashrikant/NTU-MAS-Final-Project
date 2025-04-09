package tileworld.agent;

import sim.util.Bag;
import sim.util.IntBag;
import tileworld.environment.TWEntity;
import tileworld.environment.TWFuelStation;
import tileworld.environment.TWTile;
import tileworld.environment.TWHole;
import tileworld.environment.TWObstacle;
import java.util.HashSet;
import java.util.Set;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Arrays;

public class SmartMemory extends TWAgentWorkingMemory {
    private int fuelStationX = -1;
    private int fuelStationY = -1;
    private TWFuelStation cachedFuelStation = null;
    private TWAgent agentRef;

    public SmartMemory(TWAgent agent, sim.engine.Schedule schedule, int xDim, int yDim) {
        super(agent, schedule, xDim, yDim);
        this.agentRef = agent;
    }

    public SmartMemory(TWAgent agent) {
        super(agent, agent.getEnvironment().schedule, agent.getEnvironment().getxDimension(), agent.getEnvironment().getyDimension());
        this.agentRef = agent;
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
                System.out.println(agentRef.getName() + " detected fuel station at: (" + fuelStationX + ", " + fuelStationY + ")");
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

    public TWFuelStation getFuelStation() {
        // If we've already found and cached the fuel station, return it
        if (cachedFuelStation != null) {
            return cachedFuelStation;
        }

        // If we know the coordinates but don't have the object cached
        if (isFuelStationFound()) {
            Object obj = this.getMemoryGrid().get(fuelStationX, fuelStationY);
            if (obj instanceof TWFuelStation) {
                cachedFuelStation = (TWFuelStation) obj;
                return cachedFuelStation;
            }
        }

        // Actively search the memory grid for a fuel station
        int width = getMemoryGrid().getWidth();
        int height = getMemoryGrid().getHeight();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Object obj = getMemoryGrid().get(x, y);
                if (obj instanceof TWFuelStation) {
                    TWFuelStation tf = (TWFuelStation) obj;
                    fuelStationX = tf.getX();
                    fuelStationY = tf.getY();
                    cachedFuelStation = tf;
                    System.out.println(agentRef.getName() + " found fuel station in memory at: (" + fuelStationX + ", " + fuelStationY + ")");
                    return cachedFuelStation;
                }
            }
        }

        return null;
    }

    public TWFuelStation findNearestFuelStation(int agentX, int agentY) {
        return getFuelStation();
    }

    public Set<TWEntity> getObjects() {
        Set<TWEntity> objects = new HashSet<>();
        int width = getMemoryGrid().getWidth();
        int height = getMemoryGrid().getHeight();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Object obj = getMemoryGrid().get(x, y);
                if (obj instanceof TWAgentPercept) {
                    TWAgentPercept percept = (TWAgentPercept) obj;
                    if (percept.getO() != null) {
                        objects.add(percept.getO());
                    }
                } else if (obj instanceof TWEntity) {
                    objects.add((TWEntity) obj);
                }
            }
        }
        return objects;
    }

    private Collection<TWAgentPercept> getAllPercepts() {
        Collection<TWAgentPercept> result = new ArrayList<>();
        int width = getMemoryGrid().getWidth();
        int height = getMemoryGrid().getHeight();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Object obj = getMemoryGrid().get(x, y);
                if (obj instanceof TWAgentPercept) {
                    TWAgentPercept percept = (TWAgentPercept) obj;
                    if (percept.getO() instanceof TWTile || 
                        percept.getO() instanceof TWHole || 
                        percept.getO() instanceof TWObstacle) {
                        result.add(percept);
                    }
                }
            }
        }
        return result;
    }

    private boolean isInBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < getMemoryGrid().getWidth() && y < getMemoryGrid().getHeight();
    }

    public void refreshNearbyPerception() {
        int x = agentRef.getX();
        int y = agentRef.getY();
        int range = 3;

        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                int nx = x + dx;
                int ny = y + dy;
                if (isInBounds(nx, ny)) {
                    Object obj = agentRef.getEnvironment().getObjectGrid().get(nx, ny);
                    if (obj != null) {
                        getMemoryGrid().set(nx, ny, new TWAgentPercept((TWEntity) obj, agentRef.getEnvironment().schedule.getSteps()));
                    } else {
                        getMemoryGrid().set(nx, ny, null);
                    }
                }
            }
        }
    }
}
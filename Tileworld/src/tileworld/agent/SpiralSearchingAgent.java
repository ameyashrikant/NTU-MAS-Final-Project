// 📄 File: SpiralSearchingAgent.java
package tileworld.agent;

import tileworld.environment.TWDirection;
import tileworld.environment.TWFuelStation;

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

    @Override
    protected TWThought think() {
        if (TWAgent.isFuelStationFound()) {
            return new TWThought(TWAction.MOVE, TWDirection.Z);
        }

        TWFuelStation station = detectFuelStationInRangeFromMemory();
        if (station != null) {
            TWAgent.setFuelStationFound();
            getEnvironment().receiveMessage(new Message(name, "ALL", "FUEL:" + station.getX() + "," + station.getY()));
            System.out.println("Fuel station found at (" + station.getX() + "," + station.getY() + ") by " + name);
            return new TWThought(TWAction.MOVE, TWDirection.Z);
        }

        if (fuelLevel <= 100 || stepCount >= 400) {
            return new TWThought(TWAction.MOVE, TWDirection.Z);
        }

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

    @Override
    protected void act(TWThought thought) {
        if (TWAgent.isFuelStationFound()) return;
        try {
            move(thought.getDirection());
            stepCount++;
        } catch (Exception ignored) {}
    }

    
}

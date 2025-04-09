package tileworld.agent;

import tileworld.environment.TWDirection;
import tileworld.environment.TWEntity;
import tileworld.environment.TWTile;
import tileworld.environment.TWHole;
import tileworld.environment.TWFuelStation;
import tileworld.planners.TWPath;
import tileworld.agent.TWAction;
import tileworld.agent.TWThought;

public class AgentA extends SpiralSearchingAgent {
    public AgentA(String name, int xpos, int ypos, tileworld.environment.TWEnvironment env, double fuelLevel) {
        super(name, xpos, ypos, env, fuelLevel, TWDirection.N);
    }

    @Override
    protected TWThought think() {
        long currentTime = getEnvironment().schedule.getSteps();

        // If fuel station not found, use default spiral search from SpiralSearchingAgent
        if (fuelStationX == -1) {
            return super.think();
        }

        // If fuel is low, head to fuel station (handled by super.think())
        if (getFuelLevel() < MIN_FUEL_LEVEL || isHeadingToFuelStation) {
            return super.think();
        }

        // Default task behavior if no path assigned (handled by super.think())
        return super.think();
    }

    @Override
    protected void act(TWThought thought) {
        TWDirection direction = thought.getDirection();
        TWAction action = thought.getAction();

        if (action == TWAction.PICKUP && tileToBePickedUp == null) {
            System.out.println(name + " skipped pickup due to null tileToBePickedUp");
        }
        if (action == TWAction.PUTDOWN && holeToBeFilled == null) {
            System.out.println(name + " skipped putdown due to null holeToBeFilled");
        }

        super.act(thought);

        if (action == TWAction.PICKUP || action == TWAction.PUTDOWN) {
            TWEntity entity = (TWEntity) getEnvironment().getObjectGrid().get(getX(), getY());
        }
    }
}
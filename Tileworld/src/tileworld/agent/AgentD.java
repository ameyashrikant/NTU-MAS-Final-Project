package tileworld.agent;

import tileworld.environment.TWDirection;
import tileworld.environment.TWEntity;
import tileworld.environment.TWTile;
import tileworld.environment.TWHole;
import tileworld.environment.TWFuelStation;
import tileworld.planners.TWPath;
import tileworld.agent.TWAction;
import tileworld.agent.TWThought;
import sim.util.Bag;
import java.util.HashMap;
import java.util.Map;

public class AgentD extends SpiralSearchingAgent {
    private Map<String, AgentStatus> teamStatus = new HashMap<>();

    private static class AgentStatus {
        final int x, y;
        final double fuelLevel;
        final int tileCount;
        final long timestamp;

        AgentStatus(int x, int y, double fuelLevel, int tileCount) {
            this.x = x;
            this.y = y;
            this.fuelLevel = fuelLevel;
            this.tileCount = tileCount;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public AgentD(String name, int xpos, int ypos, tileworld.environment.TWEnvironment env, double fuelLevel) {
        super(name, xpos, ypos, env, fuelLevel, TWDirection.W);
    }

    @Override
    protected TWThought think() {
        long currentTime = getEnvironment().schedule.getSteps();
        return super.think();
    }

    public boolean sameLocation(TWEntity target) {
        return getX() == target.getX() && getY() == target.getY();
    }
}
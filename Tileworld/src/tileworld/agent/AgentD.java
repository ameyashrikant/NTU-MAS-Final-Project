package tileworld.agent;

import tileworld.environment.TWDirection;

public class AgentD extends SpiralSearchingAgent {
    public AgentD(String name, int xpos, int ypos, tileworld.environment.TWEnvironment env, double fuelLevel) {
        super(name, xpos, ypos, env, fuelLevel, TWDirection.W); // A starts North
    }
    
}

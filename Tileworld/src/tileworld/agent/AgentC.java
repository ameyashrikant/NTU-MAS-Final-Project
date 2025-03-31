package tileworld.agent;

import tileworld.environment.TWDirection;

public class AgentC extends SpiralSearchingAgent {
    public AgentC(String name, int xpos, int ypos, tileworld.environment.TWEnvironment env, double fuelLevel) {
        super(name, xpos, ypos, env, fuelLevel, TWDirection.E); // A starts North
    }
    
}

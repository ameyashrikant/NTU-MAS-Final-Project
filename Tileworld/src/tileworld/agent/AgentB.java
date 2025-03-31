package tileworld.agent;

import tileworld.environment.TWDirection;

public class AgentB extends SpiralSearchingAgent {
    public AgentB(String name, int xpos, int ypos, tileworld.environment.TWEnvironment env, double fuelLevel) {
        super(name, xpos, ypos, env, fuelLevel, TWDirection.S); // A starts North
    }
    
}

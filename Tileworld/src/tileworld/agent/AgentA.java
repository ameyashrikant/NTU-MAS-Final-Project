package tileworld.agent;
import tileworld.environment.TWDirection;

public class AgentA extends SpiralSearchingAgent {
    public AgentA(String name, int xpos, int ypos, tileworld.environment.TWEnvironment env, double fuelLevel) {
        super(name, xpos, ypos, env, fuelLevel, TWDirection.N); // A starts North
    }

}

package tileworld.environment;

import sim.util.Int2D;
import tileworld.EnvParameters;

public class TWObject extends TWEntity{
	protected static final int lifeTime = EnvParameters.lifeTime;
	private double creationTime;
	private double dTime;
	public double getDeathTime() {
		return dTime;
	}
	public void setDeathTime(double d) {
		this.dTime = d;
	}
	public TWObject(int x, int y, TWEnvironment env, double creationTime, double deathTime) {
		super(x,y,env);
		this.creationTime = creationTime;
		this.dTime = deathTime;
	}
	public TWObject(Int2D pos, TWEnvironment env, Double creationTime, Double deathTime) {
		this(pos.x,pos.y,env,creationTime,deathTime);
	}
	public double getTimeLeft(double timeNow){
		return dTime - timeNow;
	}
	public TWObject(){
	}
	@Override
	protected void move(TWDirection d) {
		throw new UnsupportedOperationException("TWObjects are not movable.");
	}
}
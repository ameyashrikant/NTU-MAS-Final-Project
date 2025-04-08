package tileworld.agent;

import tileworld.environment.TWDirection;

public class AgentB extends SpiralSearchingAgent {
	
	
	private enum MovementState {
        SWEEPING_HORIZONTALLY, // Moving left or right
        SHIFTING_VERTICALLY    // Moving up or down by one step
    }
	
	
	MovementState currentState;
	TWDirection horizontalDirection, verticalDirection;
    
	public AgentB(String name, int xpos, int ypos, tileworld.environment.TWEnvironment env, double fuelLevel) {
        super(name, xpos, ypos, env, fuelLevel, TWDirection.S); // A starts North
        this.horizontalDirection = (xpos <= env.getxDimension() / 2) ? TWDirection.E : TWDirection.W;
        // Start shifting towards the 'far' vertical side
        this.verticalDirection = (ypos <= env.getyDimension() / 2) ? TWDirection.S : TWDirection.N;
    
        this.currentState = MovementState.SWEEPING_HORIZONTALLY;
        this.currentDir = this.horizontalDirection;
    	
    }

	@Override
    protected TWThought fallbackMovement() {
        int currentX = getX();
        int currentY = getY();
        int xDimension = getEnvironment().getxDimension();
        int yDimension = getEnvironment().getyDimension();
        int sensorRange = this.sensor.sensorRange;
        
        boolean willHitHorizontalWall =
            (horizontalDirection == TWDirection.E && currentX >= xDimension - sensorRange) ||
            (horizontalDirection == TWDirection.W && currentX <= sensorRange);

        boolean willHitVerticalWall =
            (verticalDirection == TWDirection.S && currentY >= yDimension - sensorRange) ||
            (verticalDirection == TWDirection.N && currentY <= sensorRange);


        if (currentState == MovementState.SWEEPING_HORIZONTALLY) {
            if (willHitHorizontalWall) {
                currentState = MovementState.SHIFTING_VERTICALLY;
                if (willHitVerticalWall) {
                    verticalDirection = (verticalDirection == TWDirection.S) ? TWDirection.N : TWDirection.S;
                }
                horizontalDirection = (horizontalDirection == TWDirection.E) ? TWDirection.W : TWDirection.E;
                this.currentDir = verticalDirection;
                return new TWThought(TWAction.MOVE, verticalDirection);
            } else {
                this.currentDir = horizontalDirection;
                return new TWThought(TWAction.MOVE, horizontalDirection);
            }
        } else { // currentState == MovementState.SHIFTING_VERTICALLY
            currentState = MovementState.SWEEPING_HORIZONTALLY;

             boolean startingSweepWillHitWall =
                 (horizontalDirection == TWDirection.E && currentX >= xDimension - 1) ||
                 (horizontalDirection == TWDirection.W && currentX <= 0);

             if (startingSweepWillHitWall && xDimension > 1) {
                 currentState = MovementState.SHIFTING_VERTICALLY;
                 if (willHitVerticalWall) {
                     verticalDirection = (verticalDirection == TWDirection.S) ? TWDirection.N : TWDirection.S;
                 }
                  horizontalDirection = (horizontalDirection == TWDirection.E) ? TWDirection.W : TWDirection.E;
                 this.currentDir = verticalDirection;
                 return new TWThought(TWAction.MOVE, verticalDirection);
             } else {
                 this.currentDir = horizontalDirection;
                 return new TWThought(TWAction.MOVE, horizontalDirection);
             }
        }
    }
    
}
package tileworld.agent;

import tileworld.environment.TWDirection;
import tileworld.environment.TWEntity;
import tileworld.environment.TWTile;
import tileworld.environment.TWHole;
import tileworld.environment.TWFuelStation;
import tileworld.planners.TWPath;
import tileworld.agent.TWAction;
import tileworld.agent.TWThought;
import tileworld.EnvParameters;

public class AgentA extends SpiralSearchingAgent {
    public AgentA(String name, int xpos, int ypos, tileworld.environment.TWEnvironment env, double fuelLevel) {
        super(name, xpos, ypos, env, fuelLevel, TWDirection.N);
    }

    @Override
    protected TWThought think() {
        long currentTime = getEnvironment().schedule.getSteps();

        // Send status update to AgentD every 10 steps
        if (currentTime % 10 == 0) {
            String statusMessage = String.format(
                "POSITION:(%d,%d),FUEL: %.2f",
                getX(), getY(), getFuelLevel()
            );
            ExtendedMessage message = new ExtendedMessage(
                this.name,           // From: this agent's name
                "manager",          // To: AgentD
                statusMessage,      // Message content
                MessageType.POSITION_REPORT, // Message type
                null                // No payload needed
            );
            getEnvironment().receiveMessage(message);
            System.out.println(name + " sent status to manager: " + statusMessage);
        }

        // Default behavior
        if (fuelStationX == -1) {
            return super.think();
        }
        if (getFuelLevel() < MIN_FUEL_LEVEL || isHeadingToFuelStation) {
            return super.think();
        }
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

        int prevTileCount = carriedTiles.size();
        System.out.println(name + " acting: " + action + ", Tiles before=" + prevTileCount);
        super.act(thought);
        System.out.println(name + " acted: " + action + ", Tiles after=" + carriedTiles.size());

        // Send task completion feedback after successful actions
        if (action == TWAction.PICKUP && tileToBePickedUp != null && carriedTiles.size() > prevTileCount) {
            sendTaskCompleteMessage("TILE", tileToBePickedUp.getX(), tileToBePickedUp.getY());
            tileToBePickedUp = null;
        } else if (action == TWAction.PUTDOWN && holeToBeFilled != null && carriedTiles.size() < prevTileCount) {
            sendTaskCompleteMessage("HOLE", holeToBeFilled.getX(), holeToBeFilled.getY());
            holeToBeFilled = null;
        }
    }

    private void sendTaskCompleteMessage(String taskType, int x, int y) {
        String message = String.format("COMPLETE:%s:%d,%d", taskType, x, y);
        ExtendedMessage em = new ExtendedMessage(
            this.name, "manager", message, MessageType.TASK_COMPLETE, null
        );
        getEnvironment().receiveMessage(em);
        System.out.println(name + " reported " + taskType + " task complete at (" + x + "," + y + ")");
    }

    @Override
    protected void processMessages() {
        super.processMessages(); // Handle inherited messages (e.g., PICKUP, FILL)

        for (Message m : getEnvironment().getMessages()) {
            if (m instanceof ExtendedMessage) {
                ExtendedMessage em = (ExtendedMessage) m;
                if (em.getTo().equals(this.name) && em.getFrom().equals("manager") &&
                    em.getType() == MessageType.TARGET_ASSIGNMENT) {
                    String[] parts = em.getMessage().split(":");
                    if (parts.length == 3) {
                        String taskType = parts[1];
                        String[] coords = parts[2].split(",");
                        int x = Integer.parseInt(coords[0]);
                        int y = Integer.parseInt(coords[1]);

                        if (taskType.equals("FUEL")) {
                            isHeadingToFuelStation = true;
                            fuelStationX = x;
                            fuelStationY = y;
                            currentPath = null; // Force recalculation
                            System.out.println(name + " received task: head to fuel station at " + x + "," + y);
                        } else if (taskType.equals("TILE")) {
                            TWEntity entity = (TWEntity) getEnvironment().getObjectGrid().get(x, y);
                            if (entity instanceof TWTile) {
                                tileToBePickedUp = (TWTile) entity;
                                isPickingUpTile = true;
                                currentPath = null;
                                System.out.println(name + " received task: pick up tile at " + x + "," + y);
                            } else {
                                System.out.println(name + " no tile found at " + x + "," + y);
                            }
                        } else if (taskType.equals("HOLE")) {
                            TWEntity entity = (TWEntity) getEnvironment().getObjectGrid().get(x, y);
                            if (entity instanceof TWHole) {
                                holeToBeFilled = (TWHole) entity;
                                fillHole = true;
                                currentPath = null;
                                System.out.println(name + " received task: fill hole at " + x + "," + y);
                            } else {
                                System.out.println(name + " no hole found at " + x + "," + y);
                            }
                        }
                    }
                }
            }
        }
    }
}
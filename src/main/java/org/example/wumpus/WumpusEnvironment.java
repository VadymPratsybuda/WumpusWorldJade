package org.example.wumpus;

import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.core.behaviours.CyclicBehaviour;
import java.util.*;

import org.example.Room;
import org.example.agents.AgentPosition;

/**
 * Wumpus World environment implemented as a JADE agent.
 */

public class WumpusEnvironment extends Agent {

    private WumpusCave cave;
    private boolean isWumpusAlive = true;
    private boolean isGoldGrabbed = false;
    private Map<AID, AgentPosition> agentPositions = new HashMap<>();
    private Set<AID> bumpedAgents = new HashSet<>();
    private Set<AID> agentsHavingArrow = new HashSet<>();
    private AID agentJustKillingWumpus;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0 && args[0] instanceof WumpusCave) {
            cave = (WumpusCave) args[0];
        } else {
            System.out.println("No cave provided. Shutting down.");
            doDelete();
            return;
        }

        addBehaviour(new EnvironmentBehaviour());
    }

    private class EnvironmentBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg != null) {
                String content = msg.getContent();
                AID sender = msg.getSender();

                switch (content) {
                    case "register":
                        agentPositions.put(sender, cave.getStart());
                        agentsHavingArrow.add(sender);
                        break;
                    case "forward":
                        moveForward(sender);
                        break;
                    case "turn_left":
                        turnLeft(sender);
                        break;
                    case "turn_right":
                        turnRight(sender);
                        break;
                    case "grab":
                        grabGold(sender);
                        break;
                    case "shoot":
                        shootArrow(sender);
                        break;
                    case "climb":
                        climb(sender);
                        break;
                    case "percept":
                        sendPercept(sender);
                        break;
                    default:
                        System.out.println("Unknown action: " + content);
                }
            } else {
                block();
            }
        }
    }

    private void moveForward(AID agent) {
        bumpedAgents.remove(agent);
        if (agent.equals(agentJustKillingWumpus))
            agentJustKillingWumpus = null;

        AgentPosition pos = agentPositions.get(agent);
        AgentPosition newPos = cave.moveForward(pos);
        agentPositions.put(agent, newPos);

        if (newPos.equals(pos)) {
            bumpedAgents.add(agent);
        } else if (cave.isPit(newPos.getRoom()) || (newPos.getRoom().equals(cave.getWumpus()) && isWumpusAlive)) {
            sendDeath(agent);
        }
    }

    private void turnLeft(AID agent) {
        AgentPosition pos = agentPositions.get(agent);
        agentPositions.put(agent, cave.turnLeft(pos));
    }

    private void turnRight(AID agent) {
        AgentPosition pos = agentPositions.get(agent);
        agentPositions.put(agent, cave.turnRight(pos));
    }

    private void grabGold(AID agent) {
        AgentPosition pos = agentPositions.get(agent);
        if (!isGoldGrabbed && pos.getRoom().equals(cave.getGold())) {
            isGoldGrabbed = true;
        }
    }

    private void shootArrow(AID agent) {
        if (agentsHavingArrow.contains(agent)) {
            AgentPosition pos = agentPositions.get(agent);
            if (isAgentFacingWumpus(pos)) {
                isWumpusAlive = false;
                agentJustKillingWumpus = agent;
            }
            agentsHavingArrow.remove(agent);
        }
    }

    private void climb(AID agent) {
        sendDeath(agent);
    }

    private void sendPercept(AID agent) {
        WumpusPercept percept = new WumpusPercept();
        AgentPosition pos = agentPositions.get(agent);

        List<Room> adjacentRooms = Arrays.asList(
                new Room(pos.getX() - 1, pos.getY()),
                new Room(pos.getX() + 1, pos.getY()),
                new Room(pos.getX(), pos.getY() - 1),
                new Room(pos.getX(), pos.getY() + 1)
        );

        for (Room r : adjacentRooms) {
            if (r.equals(cave.getWumpus()))
                percept.setStench();
            if (cave.isPit(r))
                percept.setBreeze();
        }

        if (pos.getRoom().equals(cave.getGold()))
            percept.setGlitter();
        if (bumpedAgents.contains(agent))
            percept.setBump();
        if (agentJustKillingWumpus != null)
            percept.setScream();

        ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
        reply.addReceiver(agent);
        reply.setContent(percept.toString());
        send(reply);
    }

    private boolean isAgentFacingWumpus(AgentPosition pos) {
        Room wumpus = cave.getWumpus();
        switch (pos.getOrientation()) {
            case FACING_NORTH:
                return pos.getX() == wumpus.getX() && pos.getY() < wumpus.getY();
            case FACING_SOUTH:
                return pos.getX() == wumpus.getX() && pos.getY() > wumpus.getY();
            case FACING_EAST:
                return pos.getY() == wumpus.getY() && pos.getX() < wumpus.getX();
            case FACING_WEST:
                return pos.getY() == wumpus.getY() && pos.getX() > wumpus.getX();
        }
        return false;
    }

    private void sendDeath(AID agent) {
        ACLMessage deathMsg = new ACLMessage(ACLMessage.INFORM);
        deathMsg.addReceiver(agent);
        deathMsg.setContent("dead");
        send(deathMsg);
    }
}
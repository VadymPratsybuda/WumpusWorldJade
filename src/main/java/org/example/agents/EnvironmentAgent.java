package org.example.agents;

import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;
import jade.lang.acl.MessageTemplate;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;
import org.example.Room;
import org.example.agents.AgentPosition;
import org.example.wumpus.WumpusAction;
import org.example.wumpus.WumpusCave;
import org.example.wumpus.WumpusPercept;

import java.awt.*;
import java.util.*;
import java.util.List;

import static org.example.wumpus.WumpusAction.*;

public class EnvironmentAgent extends Agent {
    private WumpusCave cave;
    private AgentPosition speleologistPos = new AgentPosition(1, 1, AgentPosition.Orientation.FACING_NORTH);
    private int timeTick = 0;
    private boolean wumpusAlive = true;
    private boolean goldGrabbed = false;
    private boolean agentHavingArrow = true;
    private boolean agentBumped = false;
    private boolean agentJustKilledWumpus = false;
    private boolean agentAlive = true;


    public WumpusCave getCave() {
        return cave;
    }

    public boolean isWumpusAlive() {return wumpusAlive;}
    public boolean isGoldGrabbed() {return goldGrabbed;}
    public boolean isAgentHavingArrow() {return agentHavingArrow;}
    public boolean isAgentBumped() {return agentBumped;}
    public boolean isAgentJustKilledWumpus() {return agentJustKilledWumpus;}
    public boolean isAgentAlive() {return agentAlive;}

    @Override
    protected void setup() {
        System.out.println(getLocalName() + ": initializing environment...");

        cave = new WumpusCave();


        registerInDF("environment");

        generateCave(0.2);

        try {
            createAgent("navigator", "org.example.agents.NavigatorAgent");
            createAgent("speleologist", "org.example.agents.SpeleologistAgent");
        } catch (Exception e) {
            e.printStackTrace();
        }


        addBehaviour(new CyclicMailboxBehaviour());
    }

    private void generateCave(double pitRatio) {
        int x = cave.getX();
        int y = cave.getY();
        int pitNum = (int)((x*y)*pitRatio);
        Set<Room> availableRooms;
        Set<Room> usedRooms = new HashSet<>();

        availableRooms = cave.getAllRooms();

        Room startRoom = new Room(1, 1);
        usedRooms.add(startRoom);
        availableRooms.remove(startRoom);

        Random random = new Random();
        for (int i = 0; i < pitNum; i++) {
            if (usedRooms.size() == x*y) break;

            Room pitRoom = getRandomRoom(availableRooms, random);
            cave.setPit(pitRoom, true);
            usedRooms.add(pitRoom);
            availableRooms.remove(pitRoom);
        }

        if (!availableRooms.isEmpty()) {
            Room wumpusRoom = getRandomRoom(availableRooms, random);
            cave.setWumpus(wumpusRoom);
            usedRooms.add(wumpusRoom);
            availableRooms.remove(wumpusRoom);
        }

        if (!availableRooms.isEmpty()) {
            Room goldRoom = getRandomRoom(availableRooms, random);
            cave.setGold(goldRoom);
        }


    }

    private Room getRandomRoom(Set<Room> availableRooms, Random random) {
        int index = random.nextInt(availableRooms.size());
        int i = 0;

        for (Room room : availableRooms) {
            if (i == index) {
                return room;
            }
            i++;
        }

        return null;
    }

    private void registerInDF(String serviceType) {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType(serviceType);
        sd.setName(getLocalName());
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }

    private void createAgent(String name, String className) throws StaleProxyException {
        ContainerController container = getContainerController();
        AgentController agent = container.createNewAgent(name, className, null);
        agent.start();
        System.out.println(getLocalName() + ": Created agent " + name);
    }

    private class CyclicMailboxBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg != null) {
                switch (msg.getPerformative()) {

                    case ACLMessage.REQUEST:
                        addBehaviour(new OneShotDescribeBehaviour(msg));
                        break;

                    case ACLMessage.CFP:
                        addBehaviour(new OneShotActionBehaviour(msg));
                        break;

                    default:
                        System.out.println(getLocalName() + ": Received unknown message");
                        break;
                }
            } else {
                block();
            }
        }
    }

    private class OneShotDescribeBehaviour extends OneShotBehaviour {
        private ACLMessage request;

        public OneShotDescribeBehaviour(ACLMessage request) {
            this.request = request;
        }

        @Override
        public void action() {
            AgentPosition pos = speleologistPos;
            WumpusPercept percept = getPerceptSeenBy();
            String agentName = request.getSender().getLocalName();
            String percept_str = percept.toString();

            ACLMessage reply = request.createReply();

            reply.setPerformative(ACLMessage.INFORM);
            reply.setContent(percept_str);
            send(reply);
            System.out.println(getLocalName() + ": Sent percept to " + agentName);
        }
    }

    private class OneShotActionBehaviour extends OneShotBehaviour {
        private ACLMessage cfp;

        public OneShotActionBehaviour(ACLMessage cfp) {
            this.cfp = cfp;
        }

        @Override
        public void action() {
            String action = cfp.getContent();
            String agentName = cfp.getSender().getLocalName();
            AgentPosition pos = speleologistPos;
            timeTick += 1;

            takeAction(speleologistPos, WumpusAction.fromSymbol(action));


            ACLMessage reply = cfp.createReply();
            if (isAgentAlive()){
                reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                reply.setContent("Action " + action + " executed.");
                send(reply);
            }
            else {
                reply.setPerformative(ACLMessage.DISCONFIRM);
                reply.setContent("You're dead");
                send(reply);
            }
            System.out.println(getLocalName() + ": Executed action for " + agentName + " in " + timeTick + " step");
        }
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

    private void takeAction(AgentPosition agentPosition, WumpusAction action) {

        if(isAgentJustKilledWumpus()){
            agentJustKilledWumpus = false;
        }

        switch (action) {
            case FORWARD:
                AgentPosition newPos = cave.moveForward(agentPosition);
                speleologistPos = newPos;
                if (newPos.equals(agentPosition)) {
                    agentBumped = true;
                } else if (cave.isPit(newPos.getRoom()) || newPos.getRoom().equals(cave.getWumpus()) && isWumpusAlive())
                    agentAlive = false;
                break;
            case TURN_LEFT:
                speleologistPos = cave.turnLeft(agentPosition);
                agentBumped = false;
                break;
            case TURN_RIGHT:
                speleologistPos = cave.turnRight(agentPosition);
                agentBumped = false;
                break;
            case GRAB:
                if (!isGoldGrabbed() && agentPosition.getRoom().equals(cave.getGold()))
                    goldGrabbed = true;
                agentBumped = false;
                cave.removeGold();
                break;
            case SHOOT:
                if (isAgentHavingArrow() && isAgentFacingWumpus(agentPosition)) {
                    wumpusAlive = false;
                    agentHavingArrow = false;
                    agentJustKilledWumpus = true;
                agentBumped=false;
                }
                break;
            case CLIMB:
                agentAlive = false;
                agentBumped = false;
        }
    }

    public WumpusPercept getPerceptSeenBy() {
        WumpusPercept result = new WumpusPercept();

        AgentPosition pos = speleologistPos;

        List<Room> adjacentRooms = Arrays.asList(
                new Room(pos.getX()-1, pos.getY()), new Room(pos.getX()+1, pos.getY()),
                new Room(pos.getX(), pos.getY()-1), new Room(pos.getX(), pos.getY()+1)
        );
        for (Room r : adjacentRooms) {
            if (r.equals(cave.getWumpus()))
                result.setStench();
            if (cave.isPit(r))
                result.setBreeze();
        }
        if (pos.getRoom().equals(cave.getGold()))
            result.setGlitter();
        if (isAgentBumped())
            result.setBump();
        if (isAgentJustKilledWumpus())
            result.setScream();

        return result;
    }


}
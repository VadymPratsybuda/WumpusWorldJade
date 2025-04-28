package org.example.agents;

import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.core.behaviours.CyclicBehaviour;
import org.example.util.PerceptParser;
import org.example.wumpus.WumpusAction;
import org.example.wumpus.WumpusCave;
import org.example.wumpus.WumpusFunctions;
import org.example.wumpus.WumpusPercept;

import java.util.*;
import java.util.function.Function;

public class NavigatorAgent extends Agent {

    private Map<String, List<String>> synonymDictionary = new HashMap<>();

    private boolean goldGrabbed = false;
    private boolean agentHavingArrow = true;
    private AgentPosition currentPosition = new AgentPosition(1, 1, AgentPosition.Orientation.FACING_NORTH);
    private WumpusCave cave = new WumpusCave();
    private Set<AgentPosition> visitedPositions = new HashSet<>();
    private Set<AgentPosition> safePositions = new HashSet<>();
    private Set<AgentPosition> dangerousPositions = new HashSet<>();
    private Set<AgentPosition> potentialWumpusPositions = new HashSet<>();
    private Set<AgentPosition> potentialPitPositions = new HashSet<>();
    private Set<AgentPosition> cellsWithBreeze = new HashSet<>();
    private Set<AgentPosition> cellsWithStench = new HashSet<>();
    private Set<AgentPosition> definitivePitPositions = new HashSet<>();
    private Set<AgentPosition> definitiveWumpusPositions = new HashSet<>();
    private boolean wumpusAlive = true;
    private boolean stunchBefore = true;

    @Override
    protected void setup() {
        System.out.println("Navigator started.");

        fillSynonymDictionary();

        safePositions.add(currentPosition);
        visitedPositions.add(currentPosition);

        registerInDF("navigator");

        addBehaviour(new PerceptAnalysis());
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

    private void fillSynonymDictionary() {
        synonymDictionary.put("Forward", Arrays.asList(
                "Move forward",
                "I need to go ahead",
                "Going straight"
        ));
        synonymDictionary.put("TurnLeft", Arrays.asList(
                "I turn left",
                "Turning leftward",
                "Facing left"
        ));
        synonymDictionary.put("TurnRight", Arrays.asList(
                "I turn right",
                "Turning rightward",
                "Facing right"
        ));
        synonymDictionary.put("Grab", Arrays.asList(
                "I grab the gold",
                "Collecting the gold",
                "Picking up the gold"
        ));
        synonymDictionary.put("Shoot", Arrays.asList(
                "I shoot an arrow",
                "Launching the arrow",
                "Firing the arrow"
        ));
        synonymDictionary.put("Climb", Arrays.asList(
                "I climb out of cave",
                "Escaping upwards",
                "I climb up"
        ));
    }

    private String getRandomSynonym(String key) {
        List<String> synonyms = synonymDictionary.get(key);
        Random random = new Random();
        int index = random.nextInt(synonyms.size());
        return synonyms.get(index);
    }

    private String createNaturalLanguageMessage(WumpusAction action) {
        String message = "";
        if (synonymDictionary.containsKey(action.getSymbol())) {
            message = getRandomSynonym(action.getSymbol());
        }
        return message;
    }

    private WumpusPercept processNaturalLanguageMessage(String message) {
        return PerceptParser.parsePerceptMessage(message);
    }

    private boolean isAtStart() {
        return currentPosition.getX() == 1 && currentPosition.getY() == 1;
    }

    private void updateKnowledgeBase(WumpusPercept percept) {
        visitedPositions.add(new AgentPosition(currentPosition.getX(), currentPosition.getY(), AgentPosition.Orientation.FACING_NORTH));
        safePositions.add(new AgentPosition(currentPosition.getX(), currentPosition.getY(), AgentPosition.Orientation.FACING_NORTH));

        if (percept.isBreeze()) {
            cellsWithBreeze.add(new AgentPosition(currentPosition.getX(), currentPosition.getY(), AgentPosition.Orientation.FACING_NORTH));
            markAdjacentCellsAsPotentialPits();
        }

        if (percept.isStench() && wumpusAlive) {
            cellsWithStench.add(new AgentPosition(currentPosition.getX(), currentPosition.getY(), AgentPosition.Orientation.FACING_NORTH));
            markAdjacentCellsAsPotentialWumpus();
        }

        if (!percept.isBreeze() && !percept.isStench() || !percept.isBreeze() && (percept.isStench() && !wumpusAlive)) {
            markAdjacentCellsAsSafe();
        }

        updateInferences();
    }

    private void markAdjacentCellsAsPotentialPits() {
        List<AgentPosition> adjacentCells = getAdjacentPositions(currentPosition);
        for (AgentPosition pos : adjacentCells) {
            if (!visitedPositions.contains(new AgentPosition(pos.getX(), pos.getY(), AgentPosition.Orientation.FACING_NORTH))) {
                potentialPitPositions.add(new AgentPosition(pos.getX(), pos.getY(), AgentPosition.Orientation.FACING_NORTH));
            }
        }
    }

    private void markAdjacentCellsAsPotentialWumpus() {
        List<AgentPosition> adjacentCells = getAdjacentPositions(currentPosition);
        for (AgentPosition pos : adjacentCells) {
            if (!visitedPositions.contains(new AgentPosition(pos.getX(), pos.getY(), AgentPosition.Orientation.FACING_NORTH))) {
                potentialWumpusPositions.add(new AgentPosition(pos.getX(), pos.getY(), AgentPosition.Orientation.FACING_NORTH));
            }
        }
    }

    private void markAdjacentCellsAsSafe() {
        List<AgentPosition> adjacentCells = getAdjacentPositions(currentPosition);
        for (AgentPosition pos : adjacentCells) {
            safePositions.add(new AgentPosition(pos.getX(), pos.getY(), AgentPosition.Orientation.FACING_NORTH));
            potentialPitPositions.remove(new AgentPosition(pos.getX(), pos.getY(), AgentPosition.Orientation.FACING_NORTH));
            potentialWumpusPositions.remove(new AgentPosition(pos.getX(), pos.getY(), AgentPosition.Orientation.FACING_NORTH));
        }
    }

    private List<AgentPosition> getAdjacentPositions(AgentPosition pos) {
        List<AgentPosition> adjacentPos = new ArrayList<>();
        int x = pos.getX();
        int y = pos.getY();

        if (x > 1) adjacentPos.add(new AgentPosition(x-1, y, AgentPosition.Orientation.FACING_NORTH));
        if (x < cave.getX()) adjacentPos.add(new AgentPosition(x+1, y, AgentPosition.Orientation.FACING_NORTH));
        if (y > 1) adjacentPos.add(new AgentPosition(x, y-1, AgentPosition.Orientation.FACING_NORTH));
        if (y < cave.getY()) adjacentPos.add(new AgentPosition(x, y+1, AgentPosition.Orientation.FACING_NORTH));

        return adjacentPos;
    }



    private class PerceptAnalysis extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg != null) {
                String percept_str = msg.getContent();
                WumpusPercept percept = processNaturalLanguageMessage(percept_str);

                WumpusAction action = decideNextAction(percept);

                String action_str = createNaturalLanguageMessage(action);

                ACLMessage reply = new ACLMessage(ACLMessage.PROPOSE);
                reply.addReceiver(msg.getSender());
                reply.setContent(action_str);
                send(reply);
            } else {
                block();
            }
        }

        private WumpusAction decideNextAction(WumpusPercept percept) {
            System.out.println("Current position" + currentPosition);
            if(percept.isBump()){
                int newY = currentPosition.getY();
                int newX = currentPosition.getX();
                switch (currentPosition.getOrientation()) {
                    case FACING_NORTH -> newY -= 1;
                    case FACING_SOUTH -> newY += 1;
                    case FACING_EAST -> newX -= 1;
                    case FACING_WEST -> newX += 1;
                }
                currentPosition = new AgentPosition(newX, newY, currentPosition.getOrientation());
            }


            if (percept.isGlitter()) {
                goldGrabbed=true;
                return WumpusAction.GRAB;
            }

            if (goldGrabbed && isAtStart()) {
                return WumpusAction.CLIMB;
            }

            if(goldGrabbed) {
                return planPathTo(cave.getStart());
            }

            if (percept.isBump()) {
                currentPosition = new AgentPosition(currentPosition.getX(), currentPosition.getY(), turnRight(currentPosition.getOrientation()));
                return WumpusAction.TURN_RIGHT;
            }

            if(agentHavingArrow && !definitiveWumpusPositions.isEmpty()) {
                agentHavingArrow = false;
                System.out.println("Agent on way to Wumpus");
                return shootWumpus();
            }

            updateKnowledgeBase(percept);


            System.out.println("Agent check safe cells");
            if (hasSafeUnexploredCells()) {
                return navigateToSafeUnexploredCell();
            }


            Function<AgentPosition, List<WumpusAction>> actionsFn = WumpusFunctions.createActionsFunction(cave);
            List<WumpusAction> possibleActions = actionsFn.apply(currentPosition);

            System.out.println("Agent choose from possible actions");
            if (possibleActions.contains(WumpusAction.FORWARD)) {
                return WumpusAction.FORWARD;
            } else {
                return WumpusAction.TURN_RIGHT;
            }
        }

        private WumpusAction shootWumpus(){
            for (AgentPosition wumpus : definitiveWumpusPositions) {
                if(wumpus.getX() == currentPosition.getX() && wumpus.getY() == currentPosition.getY()){
                    if(wumpus.getX() - currentPosition.getX() > 0 && currentPosition.getOrientation() != AgentPosition.Orientation.FACING_EAST){
                        return WumpusAction.TURN_RIGHT;
                    }
                    else if(wumpus.getX() - currentPosition.getX() < 0 && currentPosition.getOrientation() != AgentPosition.Orientation.FACING_WEST){
                        return WumpusAction.TURN_RIGHT;
                    }
                    else if(wumpus.getY() - currentPosition.getY() > 0 && wumpus.getOrientation() != AgentPosition.Orientation.FACING_NORTH){
                        return WumpusAction.TURN_RIGHT;
                    }
                    else if (wumpus.getY() - currentPosition.getY() < 0 && wumpus.getOrientation() != AgentPosition.Orientation.FACING_SOUTH){
                        return WumpusAction.TURN_RIGHT;
                    }
                    else return WumpusAction.SHOOT;
                }
                planPathTo(wumpus);
            }
            return WumpusAction.FORWARD;
        }

        private boolean hasSafeUnexploredCells() {
            for (AgentPosition pos : safePositions) {
                if (!visitedPositions.contains(new AgentPosition(pos.getX(), pos.getY(), AgentPosition.Orientation.FACING_NORTH))) {
                    return true;
                }
            }
            return false;
        }

        private WumpusAction navigateToSafeUnexploredCell() {
            System.out.println(safePositions);
            AgentPosition target = findNearestSafeUnexploredCell();
            return planPathTo(target);
        }

        private AgentPosition findNearestSafeUnexploredCell() {
            AgentPosition nearest = null;
            int minDistance = Integer.MAX_VALUE;

            for (AgentPosition pos : safePositions) {
                if (!visitedPositions.contains(new AgentPosition(pos.getX(), pos.getY(), AgentPosition.Orientation.FACING_NORTH))) {
                    int distance = manhattanDistance(currentPosition, pos);
                    if (distance < minDistance) {
                        minDistance = distance;
                        nearest = pos;
                    }
                }
            }

            return nearest != null ? nearest : currentPosition;
        }

        private int manhattanDistance(AgentPosition a, AgentPosition b) {
            return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
        }

        private WumpusAction planPathTo(AgentPosition target) {
            int dx = target.getX() - currentPosition.getX();
            int dy = target.getY() - currentPosition.getY();

            AgentPosition.Orientation neededOrientation;

            if (Math.abs(dx) > Math.abs(dy)) {
                neededOrientation = dx > 0 ? AgentPosition.Orientation.FACING_EAST :
                        AgentPosition.Orientation.FACING_WEST;
            } else {
                neededOrientation = dy > 0 ? AgentPosition.Orientation.FACING_NORTH :
                        AgentPosition.Orientation.FACING_SOUTH;
            }


            if (currentPosition.getOrientation() != neededOrientation) {
                currentPosition = new AgentPosition(
                        currentPosition.getX(),
                        currentPosition.getY(),
                        turnRight(currentPosition.getOrientation())
                );
                return WumpusAction.TURN_RIGHT;
            } else {
                int newX = currentPosition.getX();
                int newY = currentPosition.getY();

                switch (currentPosition.getOrientation()) {
                    case FACING_NORTH -> newY += 1;
                    case FACING_SOUTH -> newY -= 1;
                    case FACING_EAST -> newX += 1;
                    case FACING_WEST -> newX -= 1;
                }

                currentPosition = new AgentPosition(newX, newY, currentPosition.getOrientation());
                return WumpusAction.FORWARD;
            }
        }

        private AgentPosition.Orientation turnRight(AgentPosition.Orientation orientation) {
            return switch (orientation) {
                case FACING_NORTH -> AgentPosition.Orientation.FACING_EAST;
                case FACING_EAST -> AgentPosition.Orientation.FACING_SOUTH;
                case FACING_SOUTH -> AgentPosition.Orientation.FACING_WEST;
                case FACING_WEST -> AgentPosition.Orientation.FACING_NORTH;
            };
        }
    }

    private void updateInferences() {

        inferSafeCellsFromVisitedCells();

        identifyDefiniteDangers();

        removeDangersFromSafeCells();

        removeBorderSafeCells();
    }

    private void removeBorderSafeCells() {
        for (AgentPosition pos : safePositions) {
            if(pos.getY() > cave.getCaveYDimension() || pos.getX() > cave.getCaveXDimension()) {
                safePositions.remove(pos);
            }
            if (pos.getY() < cave.getStart().getY() || pos.getX() < cave.getStart().getX()) {
                safePositions.remove(pos);
            }
        }
    }

    private void inferSafeCellsFromVisitedCells() {
        for (AgentPosition pos : visitedPositions) {
            List<AgentPosition> adjacentCells = getAdjacentPositions(pos);

            if (!cellsWithBreeze.contains(new AgentPosition(pos.getX(), pos.getY(), AgentPosition.Orientation.FACING_NORTH))) {
                for (AgentPosition adj : adjacentCells) {
                    potentialPitPositions.remove(adj);
                }
            }

            if (!cellsWithStench.contains(new AgentPosition(pos.getX(), pos.getY(), AgentPosition.Orientation.FACING_NORTH))) {
                for (AgentPosition adj : adjacentCells) {
                    potentialWumpusPositions.remove(adj);
                }
            }
        }
    }

    private void identifyDefiniteDangers() {
        for (AgentPosition pos : cellsWithBreeze) {
            List<AgentPosition> adjacentCells = getAdjacentPositions(pos);
            List<AgentPosition> potentialPits = new ArrayList<>();

            for (AgentPosition adj : adjacentCells) {
                if (potentialPitPositions.contains(new AgentPosition(pos.getX(), pos.getY(), AgentPosition.Orientation.FACING_NORTH))) {
                    potentialPits.add(new AgentPosition(pos.getX(), pos.getY(), AgentPosition.Orientation.FACING_NORTH));
                }
            }

            if (potentialPits.size() == 1) {
                definitivePitPositions.add(new AgentPosition(potentialPits.getFirst().getX(), potentialPits.getFirst().getY(), AgentPosition.Orientation.FACING_NORTH));
            }
        }

        if (wumpusAlive) {
            Set<AgentPosition> commonWumpusPositions = new HashSet<>();

            boolean first = true;
            for (AgentPosition pos : cellsWithStench) {
                List<AgentPosition> adjacentCells = getAdjacentPositions(pos);
                Set<AgentPosition> localWumpusCandidates = new HashSet<>();

                for (AgentPosition adj : adjacentCells) {
                    if (potentialWumpusPositions.contains(new AgentPosition(pos.getX(), pos.getY(), AgentPosition.Orientation.FACING_NORTH))) {
                        localWumpusCandidates.add(new AgentPosition(pos.getX(), pos.getY(), AgentPosition.Orientation.FACING_NORTH));
                    }
                }

                if (first) {
                    commonWumpusPositions.addAll(localWumpusCandidates);
                    first = false;
                } else {
                    commonWumpusPositions.retainAll(localWumpusCandidates);
                }
            }

            if (commonWumpusPositions.size() == 1) {
                definitiveWumpusPositions.add(new AgentPosition(commonWumpusPositions.iterator().next().getX(), commonWumpusPositions.iterator().next().getY(), AgentPosition.Orientation.FACING_NORTH));
            }
        } else {
            definitiveWumpusPositions = new HashSet<>();
        }

        dangerousPositions.addAll(definitivePitPositions);
        dangerousPositions.addAll(definitiveWumpusPositions);
    }

    private void removeDangersFromSafeCells() {
        potentialPitPositions.removeAll(safePositions);
        potentialWumpusPositions.removeAll(safePositions);

        definitivePitPositions.removeAll(safePositions);
        definitiveWumpusPositions.removeAll(safePositions);


    }
}

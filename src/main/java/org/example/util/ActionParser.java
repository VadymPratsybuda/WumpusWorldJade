package org.example.util;

import org.example.wumpus.WumpusAction;
import org.example.wumpus.WumpusPercept;

import java.util.HashMap;
import java.util.Map;

public class ActionParser {
    private static final Map<String, String> keywordToPercept = new HashMap<>();

    static {
        keywordToPercept.put("forward", "Forward");
        keywordToPercept.put("ahead", "Forward");
        keywordToPercept.put("straight", "Forward");

        keywordToPercept.put("left", "TurnLeft");
        keywordToPercept.put("leftward", "TurnLeft");

        keywordToPercept.put("right", "TurnRight");
        keywordToPercept.put("rightward", "TurnRight");

        keywordToPercept.put("grab", "Grab");
        keywordToPercept.put("Collecting", "Grab");
        keywordToPercept.put("Picking", "Grab");

        keywordToPercept.put("shoot", "Shoot");
        keywordToPercept.put("Launching", "Shoot");
        keywordToPercept.put("Firing", "Shoot");

        keywordToPercept.put("climb", "Climb");
        keywordToPercept.put("Escaping", "Climb");
    }

    public static WumpusAction parseActionMessage(String message) {

        message = message.trim();
            for (Map.Entry<String, String> entry : keywordToPercept.entrySet()) {
                if (message.contains(entry.getKey())) {
                    switch (entry.getValue()) {
                        case "Forward":
                            return WumpusAction.FORWARD;
                        case "TurnLeft":
                            return WumpusAction.TURN_LEFT;
                        case "TurnRight":
                            return WumpusAction.TURN_RIGHT;
                        case "Grab":
                            return WumpusAction.GRAB;
                        case "Shoot":
                            return WumpusAction.SHOOT;
                        case "Climb":
                            return WumpusAction.CLIMB;
                    }
                }
            }

        return null;
    }
}

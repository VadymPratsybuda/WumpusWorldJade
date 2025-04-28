package org.example.util;

import org.example.wumpus.WumpusPercept;

import java.util.HashMap;
import java.util.Map;

public class PerceptParser {

    private static final Map<String, String> keywordToPercept = new HashMap<>();

    static {
        keywordToPercept.put("breeze", "Breeze");
        keywordToPercept.put("breath", "Breeze");
        keywordToPercept.put("puff of air", "Breeze");

        keywordToPercept.put("smell", "Stench");
        keywordToPercept.put("stench", "Stench");
        keywordToPercept.put("stinks", "Stench");

        keywordToPercept.put("glitter", "Glitter");
        keywordToPercept.put("shining", "Glitter");
        keywordToPercept.put("glimmer of light", "Glitter");

        keywordToPercept.put("scream", "Scream");
        keywordToPercept.put("yell", "Scream");
        keywordToPercept.put("shouting", "Scream");

        keywordToPercept.put("hitting", "Bump");
        keywordToPercept.put("bumping", "Bump");
        keywordToPercept.put("collided", "Bump");
    }

    public static WumpusPercept parsePerceptMessage(String message) {
        WumpusPercept percept = new WumpusPercept();
        String[] sentences = message.toLowerCase().split("\\."); // розбиваємо за крапкою

        for (String sentence : sentences) {
            sentence = sentence.trim();
            for (Map.Entry<String, String> entry : keywordToPercept.entrySet()) {
                if (sentence.contains(entry.getKey())) {
                    switch (entry.getValue()) {
                        case "Breeze":
                            percept.setBreeze();
                            break;
                        case "Stench":
                            percept.setStench();
                            break;
                        case "Glitter":
                            percept.setGlitter();
                            break;
                        case "Scream":
                            percept.setScream();
                            break;
                        case "Bump":
                            percept.setBump();
                            break;
                    }
                }
            }
        }

        return percept;
    }
}
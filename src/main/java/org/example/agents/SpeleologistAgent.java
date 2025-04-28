package org.example.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.core.behaviours.*;
import jade.lang.acl.MessageTemplate;
import org.example.wumpus.WumpusAction;

import java.util.*;
import static org.example.util.ActionParser.parseActionMessage;

public class SpeleologistAgent extends Agent {

    private AID environment;
    private AID navigator;
    private Map<String, List<String>> synonymDictionary = new HashMap<>();

    @Override
    protected void setup() {
        fillSynonymDictionary();

        registerInDF("speleologist");

        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                environment = initializeAgent("environment");
                navigator = initializeAgent("navigator");
            }

            public AID initializeAgent(String type){
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType(type);
                template.addServices(sd);
                try{
                    DFAgentDescription[] result = DFService.search(myAgent, template);
                    AID agent = new AID();
                    if (result.length > 0) {
                        agent = result[0].getName();
                    }
                    return agent;
                }
                catch (FIPAException e) {e.printStackTrace();}
                return null;
            }
        });

        addBehaviour(new MainBehaviour());
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
        synonymDictionary.put("Breeze", Arrays.asList(
                "I feel breeze here",
                "There is a breath of wind",
                "It's a puff of air here"
        ));
        synonymDictionary.put("Stench", Arrays.asList(
                "I sense a bad smell",
                "There's a stench in the air",
                "Something stinks here"
        ));
        synonymDictionary.put("Glitter", Arrays.asList(
                "I see a glitter nearby",
                "There is a shining object here",
                "I notice a glimmer of light"
        ));
        synonymDictionary.put("Scream", Arrays.asList(
                "I heard a scream",
                "I've heard an yell nearby",
                "Something is shouting"
        ));
        synonymDictionary.put("Bump", Arrays.asList(
                "I'm hitting a wall",
                "I'm bumping into something",
                "I collided the wall"
        ));
    }

    private String createNaturalLanguageMessage(String percept) {
        percept = percept.replace('{', ' ');
        percept = percept.replace('}', ' ');
        String[] properties = percept.split(", ");
        List<String> messages = new ArrayList<>();

        for (String property : properties) {
            String key = property.trim();
            if (synonymDictionary.containsKey(key)) {
                messages.add(getRandomSynonym(key));
            }
        }
        return String.join(". ", messages);
    }

    private String getRandomSynonym(String key) {
        List<String> synonyms = synonymDictionary.get(key);
        Random random = new Random();
        int index = random.nextInt(synonyms.size());
        return synonyms.get(index);
    }

    private WumpusAction processNaturalLanguageMessage(String percept) {
        return parseActionMessage(percept);
    }

    private class MainBehaviour extends CyclicBehaviour {

        @Override
        public void action() {

            ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
            request.addReceiver(environment);
            request.setContent("Describe environment");
            send(request);


            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
            ACLMessage perceptMsg = blockingReceive(mt);
            String percept = perceptMsg.getContent();
            System.out.println(getLocalName() + ": Got percept: " + percept);
            percept = createNaturalLanguageMessage(percept);


            ACLMessage navRequest = new ACLMessage(ACLMessage.REQUEST);
            navRequest.addReceiver(navigator);
            navRequest.setContent(percept);
            send(navRequest);


            MessageTemplate mt2 = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
            ACLMessage navReply = blockingReceive(mt2);
            String action = navReply.getContent();
            System.out.println(getLocalName() + ": Received action: " + action);
            WumpusAction action_w = processNaturalLanguageMessage(action);


            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            cfp.addReceiver(environment);
            cfp.setContent(action_w.getSymbol());
            send(cfp);

            MessageTemplate mt3 = MessageTemplate.or(
                    MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL),
                    MessageTemplate.MatchPerformative(ACLMessage.DISCONFIRM)
            );

            ACLMessage reply = blockingReceive(mt3);

            if (reply.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                System.out.println(getLocalName() + ": Action confirmation: " + reply.getContent());
            } else if (reply.getPerformative() == ACLMessage.DISCONFIRM) {
                System.out.println(getLocalName() + ": " + reply.getContent());
                doDelete();
            }
        }
    }

}
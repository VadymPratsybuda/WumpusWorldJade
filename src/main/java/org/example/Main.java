package org.example;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;
import jade.core.Runtime;

public class Main {
    public static void main(String[] args) {

        Runtime rt = Runtime.instance();
        Profile p = new ProfileImpl();
        p.setParameter(Profile.GUI, "true");

        ContainerController container = rt.createMainContainer(p);

        try {
            AgentController environmentAgent = container.createNewAgent(
                    "environment",
                    "org.example.agents.EnvironmentAgent",
                    null
            );
            environmentAgent.start();

        } catch (StaleProxyException e) {
            e.printStackTrace();
        }
    }
}
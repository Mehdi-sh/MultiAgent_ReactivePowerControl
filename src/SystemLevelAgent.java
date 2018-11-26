import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

/**
 * Created by Mehdi Shahtalebi on 1/23/2016.
 */
public class SystemLevelAgent extends Agent {

    private AID[] SubSystemAgents;


    protected void setup() {

        // Register the System-Agent in the yellow pages
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("System-Agent");
        sd.setName("Reactive_Power_Control");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new TickerBehaviour(this, 30000) {
            protected void onTick() {
                // Update the list of SubSystem agents
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("SubSystem-Agent");
                template.addServices(sd);
                try {
                    DFAgentDescription[] result = DFService.search(myAgent, template);
                    System.out.println("Found the following SubSystem agents:");
                    SubSystemAgents = new AID[result.length];
                    for (int i = 0; i < result.length; ++i) {
                        SubSystemAgents[i] = result[i].getName();
                        System.out.println(SubSystemAgents[i].getName());
                    }
                } catch (FIPAException fe) {
                    fe.printStackTrace();
                }


            }
        });
    }
}

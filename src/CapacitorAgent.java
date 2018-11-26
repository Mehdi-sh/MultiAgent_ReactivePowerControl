import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

/**
 * Created by Mehdi Shahtalebi on 1/23/2016.
 */
public class CapacitorAgent extends Agent {

    private Integer totalNumOfUnits = 10;
    private Integer capacitorUnitQ = 100;
    private Integer numOfUnitsOnline = 0;

    private String ownerSubSystem;

    protected void setup() {

        Object[] args = getArguments();

        if (args != null && args.length > 0) {
            ownerSubSystem = (String) args[0];

            // Register the Capacity-Bank Service in the yellow pages
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType("CapacitorBank-Agent");
            sd.setName("Reactive_Power_Control");
            sd.setOwnership(ownerSubSystem);
            dfd.addServices(sd);
            try {
                DFService.register(this, dfd);
            } catch (FIPAException fe) {
                fe.printStackTrace();
            }


        } else {
            // Make the agent terminate
            System.out.println("No owner SubSystem specified");
            doDelete();
        }

        addBehaviour(new RequestsServer());
    }

    private class RequestsServer extends CyclicBehaviour {
        private int QMadeOnline;

        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                // CFP Message received. Process it
                Integer requestedQ = Integer.parseInt(msg.getContent());
                ACLMessage reply = msg.createReply();

                if (numOfUnitsOnline != totalNumOfUnits) {
                    // Reactive Power is available
                    int requestedNumOfUnits = Math.round(requestedQ / capacitorUnitQ);
                    for (int i = 1; i <= requestedNumOfUnits; ++i) {
                        ++numOfUnitsOnline;
                        if (numOfUnitsOnline == totalNumOfUnits) {
                            QMadeOnline = i * capacitorUnitQ;
                            break;
                        }
                        QMadeOnline = i * capacitorUnitQ;
                    }

                    System.out.println(getAID().getName() + ": " + Integer.toString(QMadeOnline) + " reactive power made online in response to "+msg.getSender().getName());

                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContent(String.valueOf(QMadeOnline));
                } else {
                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setContent("not-available");
                    System.out.println(getAID().getName() + ": No reactive power available");

                }
                myAgent.send(reply);
            } else {
                block();
            }
        }
    }  // End of inner class OfferRequestsServer

}

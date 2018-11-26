import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

/**
 * Created by Mehdi Shahtalebi on 1/23/2016.
 */
public class LoadAgent extends Agent {

    private Integer activeLoad;
    private Integer reactiveLoad;

    private String ownerSubSystemName;
    private AID ownerSubSystem;
    private AID[] CapacitorAgents;
    private boolean responseFlag = true;


    protected void setup() {

        Object[] args = getArguments();

        if (args != null && args.length > 0) {
            ownerSubSystemName = (String) args[0];
            reactiveLoad = Integer.parseInt((String) args[1]);

            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("SubSystem-Agent");
            sd.setOwnership(ownerSubSystemName);
            template.addServices(sd);
            try {
                DFAgentDescription[] result = DFService.search(this, template);
                System.out.println(getAID().getName()+": Found the Owner SubSystem agent:");

                ownerSubSystem = result[0].getName();
                System.out.println(ownerSubSystem.getName());

            } catch (FIPAException fe) {
                fe.printStackTrace();
            }

        }

        addBehaviour(new TickerBehaviour(this, 10000) {
            protected void onTick() {
                // Update the list of Capacitor Agents agents
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("CapacitorBank-Agent");
                sd.setOwnership(ownerSubSystemName);
                template.addServices(sd);
                try {
                    DFAgentDescription[] result = DFService.search(myAgent, template);
                    System.out.println(getAID().getName()+": Found the following CapacitorBank agents:");
                    CapacitorAgents = new AID[result.length];
                    for (int i = 0; i < result.length; ++i) {
                        CapacitorAgents[i] = result[i].getName();
                        System.out.println(CapacitorAgents[i].getName());
                    }
                } catch (FIPAException fe) {
                    fe.printStackTrace();
                }

                if (reactiveLoad > 0 && (CapacitorAgents.length != 0)) {
                    myAgent.addBehaviour(new QRequestPerformer());
                    }

            }
        });


    }

    private class QRequestPerformer extends Behaviour {

        private MessageTemplate mt; // The template to receive replies
        private int step = 0;
        private int k = 0;

        public void action() {
            switch (step) {
                case 0:
                    // Send the request to  the i'th capacitor agent
                    ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                    request.addReceiver(CapacitorAgents[k]);
                    request.setContent(Integer.toString(reactiveLoad));
                    request.setConversationId("intra-SubSystem_Reactive_Load");
                    request.setReplyWith("request" + System.currentTimeMillis()); // Unique value
                    myAgent.send(request);
                    // Prepare the template to get responses
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("intra-SubSystem_Reactive_Load"),
                            MessageTemplate.MatchInReplyTo(request.getReplyWith()));
                    step = 1;
                    break;
                case 1:
                    // Receive response from Capacitor Agent
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        ++k;
                        step = 0;
                        // Reply received
                        if (reply.getPerformative() == ACLMessage.INFORM) {
                            // This is an Informing message, informing the Capacitor bank getting online
                            int tempReactiveLoad = Integer.parseInt(reply.getContent());
                            reactiveLoad = reactiveLoad - tempReactiveLoad;
                            System.out.println(getAID().getName()+": currently needed reactive load is "+Integer.toString(reactiveLoad));
                        }

                        if (reactiveLoad <= 0) {
                            // We don't need any more active power
                            break;
                        }
                        if (reply.getPerformative() == ACLMessage.REFUSE){
                            System.out.println(getAID().getName()+": Refuse message received");

                            break;
                        }
                    } else {
                        block();
                    }
            }

        }


        public boolean done() {
            if (reactiveLoad > 0 && (k == CapacitorAgents.length) && (responseFlag == true)) {
                System.out.println(getAID().getName()+": No more local reactive power available, need to send request to SubSystem agent");
                addBehaviour(new QRequestFromSubSystem());
                responseFlag = false;
            }
            return (reactiveLoad <= 0 || (k) == CapacitorAgents.length);
        }
    }
    private class QRequestFromSubSystem extends Behaviour {
        private MessageTemplate mt; // The template to receive replies
        private int step = 0;

        public void action() {
            switch (step) {
                case 0:
                    // Send the request to the SubSystem agent
                    ACLMessage request = new ACLMessage(ACLMessage.CFP);
                    request.addReceiver(ownerSubSystem);
                    request.setContent(Integer.toString(reactiveLoad));
                    System.out.println(getAID().getName() + "Sending request to SubSystem agent for "+reactiveLoad.toString());
                    request.setConversationId("load-SubSystem_Reactive_Load");
                    request.setReplyWith("loadRequest" + System.currentTimeMillis()); // Unique value
                    myAgent.send(request);
                    // Prepare the template to get responses
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("load-SubSystem_Reactive_Load"),
                            MessageTemplate.MatchInReplyTo(request.getReplyWith()));
                    step = 1;
                    break;
                case 1:
                    // Receive response from SubSystem Agent
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        step = 2;
                        responseFlag = true;
                        // Reply received
                        if (reply.getPerformative() == ACLMessage.INFORM) {
                            // This is an Informing message, informing the Capacitor bank getting online
                            int tempReactiveLoad = Integer.parseInt(reply.getContent());
                            reactiveLoad = reactiveLoad - tempReactiveLoad;
                            System.out.println(getAID().getName()+": YES!!!!! After SubSystem Level Compensation, Remaining reactive load is "+Integer.toString(reactiveLoad));
                        }

                        if (reactiveLoad <= 0) {
                            // We don't need any more active power
                            break;
                        }
                        if (reply.getPerformative() == ACLMessage.REFUSE){
                            System.out.println(getAID().getName()+": Refuse message received");

                            break;
                        }
                    } else {
                        block();
                    }
            }

        }


        public boolean done() {
//            if (reactiveLoad > 0 ) {
//                System.out.println(getAID().getName()+": Attempt failed, Currently there is uncompensated reactive power");
//                }
            return (step == 2);
        }
    }
}

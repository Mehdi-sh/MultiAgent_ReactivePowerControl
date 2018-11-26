import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
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
public class SubSystemAgent extends Agent {

    private AID[] CapacitorAgents;
    private AID[] SubSystemAgents;

    protected void setup() {


        // Register the SubSystem Agent in the yellow pages
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("SubSystem-Agent");
        sd.setName("Reactive_Power_Control");
        sd.setOwnership(getLocalName());
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new TickerBehaviour(this, 10000) {
            protected void onTick() {
                // Update the list of Capacitor Agents agents
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("SubSystem-Agent");
                template.addServices(sd);
                try {
                    DFAgentDescription[] result = DFService.search(myAgent, template);
                    System.out.println(getAID().getName() + ": Found the following SubSystem agents:");
                    SubSystemAgents = new AID[result.length];
                    int z = 0;
                    for (int i = 0; i < result.length; ++i) {
                        if (!getAID().getName().equals(result[i].getName().getName())) {
                            SubSystemAgents[z] = result[i].getName();
                            System.out.println(SubSystemAgents[z].getName());
                            ++z;
                        }
                    }
                } catch (FIPAException fe) {
                    fe.printStackTrace();
                }

            }
        });

        addBehaviour(new TickerBehaviour(this, 10000) {
            protected void onTick() {
                // Update the list of Capacitor Agents agents
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("CapacitorBank-Agent");
                sd.setOwnership(getLocalName());
                template.addServices(sd);
                try {
                    DFAgentDescription[] result = DFService.search(myAgent, template);
                    System.out.println(getAID().getName() + ": Found the following CapacitorBank agents:");
                    CapacitorAgents = new AID[result.length];
                    for (int i = 0; i < result.length; ++i) {
                        CapacitorAgents[i] = result[i].getName();
                        System.out.println(CapacitorAgents[i].getName());
                    }
                } catch (FIPAException fe) {
                    fe.printStackTrace();
                }

            }
        });

        addBehaviour(new RequestsFromLoadServer());

        addBehaviour(new RequestsFromSubSystemServer());
    }

    private class RequestsFromLoadServer extends CyclicBehaviour {
        private MessageTemplate loadMT; // The template to receive replies
        private MessageTemplate ReplyToSubSystemMessage; // The template to receive replies
        private ACLMessage ReplyToLoadMessage;

        private int requestedQ;
        private int step = 0;
        private int k = 0;
        private int totalQMadeOnline = 0;

        public void action() {

            switch (step) {
                case 0:

                    loadMT = MessageTemplate.MatchPerformative(ACLMessage.CFP);
                    loadMT.MatchConversationId("load-SubSystem_Reactive_Load");
                    ACLMessage loadMsg = myAgent.receive(loadMT);
                    if (loadMsg != null) {

                        System.out.println(getAID().getName() + " message received from " + loadMsg.getSender().getName());


                        requestedQ = Integer.parseInt(loadMsg.getContent());
                        ReplyToLoadMessage = loadMsg.createReply();

                        //System.out.println(getAID().getName() + " 111");


                        ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                        request.addReceiver(SubSystemAgents[k]);
                        request.setContent(Integer.toString(requestedQ));
                        request.setConversationId("inter-SubSystem_Reactive_Load");
                        request.setReplyWith("request" + System.currentTimeMillis()); // Unique value
                        myAgent.send(request);


                        //System.out.println(getAID().getName() + " 222");
                        // Prepare the template to get responses
                        ReplyToSubSystemMessage = MessageTemplate.and(MessageTemplate.MatchConversationId("inter-SubSystem_Reactive_Load"),
                                MessageTemplate.MatchInReplyTo(request.getReplyWith()));
                        step = 1;
                    } else {
                        block();
                    }
                    break;
                case 1:

                    ACLMessage reply = myAgent.receive(ReplyToSubSystemMessage);
                    if (reply != null) {

                        //System.out.println(getAID().getName() + " 777");

                        // Reply received
                        if (reply.getPerformative() == ACLMessage.INFORM) {
                            // This is an Informing message, informing the Capacitor bank getting online
                            int tempReactiveLoad = Integer.parseInt(reply.getContent());
                            totalQMadeOnline = totalQMadeOnline + tempReactiveLoad;
                            requestedQ = requestedQ - tempReactiveLoad;
                            System.out.println(getAID().getName() + ": Reactive Power made Online by " + reply.getSender().getName() + ": " + Integer.toString(totalQMadeOnline));
                        }


                        if (reply.getPerformative() == ACLMessage.REFUSE) {
                            System.out.println(getAID().getName() + ": Refuse message received from SubSystem Agent" + reply.getSender().getName());
                        }

                        ++k;
                        if (k == SubSystemAgents.length || requestedQ <=0) {
                            step = 2;
                            break;
                        }
                    } else {
                        block();
                    }
                    break;
                case 2:
                    //System.out.println(getAID().getName() + " 888");

                    if (totalQMadeOnline != 0) {
                        // Reactive Power is available
                        System.out.println(getAID().getName() + ": " + Integer.toString(totalQMadeOnline) + " reactive power made online");

                        ReplyToLoadMessage.setPerformative(ACLMessage.INFORM);
                        ReplyToLoadMessage.setContent(String.valueOf(totalQMadeOnline));
                    } else {
                        ReplyToLoadMessage.setPerformative(ACLMessage.REFUSE);
                        ReplyToLoadMessage.setContent("not-available");
                        System.out.println(getAID().getName() + ": No reactive power available at all!");

                    }
                    myAgent.send(ReplyToLoadMessage);
                    step = 0;
                    break;
            }
        }
    }

    private class RequestsFromSubSystemServer extends CyclicBehaviour {
        private MessageTemplate mt; // The template to receive replies
        private int requestedQ;
        private int step = 0;
        private int k = 0;
        private ACLMessage subSystemReply;
        private int totalQMadeOnline = 0;

        public void action() {

            switch (step) {
                case 0:

                    mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
                    mt.MatchConversationId("inter-SubSystem_Reactive_Load");
                    ACLMessage loadMsg = myAgent.receive(mt);
                    if (loadMsg != null) {
                        // CFP Message received. Process it
                        requestedQ = Integer.parseInt(loadMsg.getContent());
                        subSystemReply = loadMsg.createReply();

                        //System.out.println(getAID().getName() + " 333");

                        ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                        request.addReceiver(CapacitorAgents[k]);
                        request.setContent(Integer.toString(requestedQ));
                        request.setConversationId("inter-SubSystem_Reactive_Load");
                        request.setReplyWith("request" + System.currentTimeMillis()); // Unique value
                        myAgent.send(request);
                        //System.out.println(getAID().getName() + " 444");
                        // Prepare the template to get responses
                        mt = MessageTemplate.and(MessageTemplate.MatchConversationId("inter-SubSystem_Reactive_Load"),
                                MessageTemplate.MatchInReplyTo(request.getReplyWith()));
                        step = 1;
                    } else {
                        block();
                    }
                    break;
                case 1:

                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {

                        //System.out.println(getAID().getName() + " 555");
                        System.out.println(getAID().getName() + Integer.toString(k) + " " + Integer.toString(CapacitorAgents.length));

                        // Reply received
                        if (reply.getPerformative() == ACLMessage.INFORM) {
                            // This is an Informing message, informing the Capacitor bank getting online
                            int tempReactiveLoad = Integer.parseInt(reply.getContent());
                            totalQMadeOnline = totalQMadeOnline + tempReactiveLoad;
                            requestedQ = requestedQ - tempReactiveLoad;
                            System.out.println(getAID().getName() + ": currently needed reactive load is " + Integer.toString(requestedQ));
                        }

                        if (reply.getPerformative() == ACLMessage.REFUSE) {
                            System.out.println(getAID().getName() + ": Refuse message received from capacitor agent");
                        }

                        ++k;
                        if (k == CapacitorAgents.length || requestedQ <= 0) {
                            step = 2;
                            break;
                        } else {
                            step = 0;
                        }
                    } else {
                        block();
                    }
                    break;
                case 2:

                    //System.out.println(getAID().getName() + " 666");

                    if (totalQMadeOnline != 0) {
                        // Reactive Power is available
                        System.out.println(getAID().getName() + ": " + Integer.toString(totalQMadeOnline) + " reactive power made online!!!");

                        subSystemReply.setPerformative(ACLMessage.INFORM);
                        subSystemReply.setContent(String.valueOf(totalQMadeOnline));
                    } else {
                        subSystemReply.setPerformative(ACLMessage.REFUSE);
                        subSystemReply.setContent("not-available");
                        System.out.println(getAID().getName() + ": No reactive power available from me");

                    }
                    myAgent.send(subSystemReply);
                    step = 0;
                    break;
            }
        }
    }
}

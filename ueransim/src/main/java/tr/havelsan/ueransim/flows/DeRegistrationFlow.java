package tr.havelsan.ueransim.flows;

import tr.havelsan.ueransim.*;
import tr.havelsan.ueransim.api.UeDeRegistration;
import tr.havelsan.ueransim.configs.DeRegistrationConfig;
import tr.havelsan.ueransim.core.SimulationContext;
import tr.havelsan.ueransim.nas.impl.messages.DeRegistrationAcceptUeOriginating;
import tr.havelsan.ueransim.nas.impl.messages.DeRegistrationRequestUeOriginating;
import tr.havelsan.ueransim.ngap.ngap_pdu_contents.UEContextReleaseCommand;
import tr.havelsan.ueransim.ngap2.NgapBuilder;
import tr.havelsan.ueransim.ngap2.NgapCriticality;
import tr.havelsan.ueransim.ngap2.NgapProcedure;

public class DeRegistrationFlow extends BaseFlow {

    DeRegistrationConfig config;

    public DeRegistrationFlow(SimulationContext simContext, DeRegistrationConfig config) {
        super(simContext);
        this.config=config;
    }

    //Valid GUTI

    //Valid Suci

    @Override
    public State main(IncomingMessage message) {
        UeDeRegistration.sendDeRegistration(ctx,config);
        return this::loop;
    }

    private State loop(IncomingMessage message) {
        return this::waitDeRegistrationAccept;
    }

    private State waitDeRegistrationAccept(IncomingMessage message) {
        var deRegistrationAcceptUeOriginating = message.getNasMessage(DeRegistrationAcceptUeOriginating.class);
        if (deRegistrationAcceptUeOriginating == null) {
            FlowLogging.logUnhandledMessage(message, DeRegistrationRequestUeOriginating.class);
            return this::waitDeRegistrationAccept;
        }
        return this::waitUeContextReleaseCommand;
    }

    private State waitUeContextReleaseCommand(IncomingMessage message) {
        var command = message.getNgapMessage(UEContextReleaseCommand.class);
        if (command == null) {
            FlowLogging.logUnhandledMessage(message, UEContextReleaseCommand.class);
            return this::waitDeRegistrationAccept;
        }

        send(new SendingMessage(new NgapBuilder(NgapProcedure.UEContextReleaseComplete, NgapCriticality.REJECT), null));

        return flowComplete();
    }

    @Override
    public void onReceive(IncomingMessage incomingMessage) {

    }

    @Override
    public void onSent(OutgoingMessage outgoingMessage) {

    }
}

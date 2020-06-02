package tr.havelsan.ueransim.configs;

import tr.havelsan.ueransim.nas.impl.ies.IE5gGutiMobileIdentity;
import tr.havelsan.ueransim.nas.impl.ies.IEDeRegistrationType;
import tr.havelsan.ueransim.utils.bits.Bit3;

public class DeRegistrationConfig {
    public final IEDeRegistrationType deRegistrationType;
    public final Bit3 ngKSI;
    public final IE5gGutiMobileIdentity guti;

    public DeRegistrationConfig(IEDeRegistrationType deRegistrationType, Bit3 ngKSI, IE5gGutiMobileIdentity guti) {
        this.deRegistrationType = deRegistrationType;
        this.ngKSI = ngKSI;
        this.guti = guti;
    }
}

/*
 * MIT License
 *
 * Copyright (c) 2020 ALİ GÜNGÖR
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * @author Ali Güngör (aligng1620@gmail.com)
 */

package tr.havelsan.ueransim.api;

import tr.havelsan.ueransim.core.NasSecurityContext;
import tr.havelsan.ueransim.core.exceptions.IncorrectImplementationException;
import tr.havelsan.ueransim.crypto.*;
import tr.havelsan.ueransim.enums.EConnectionIdentifier;
import tr.havelsan.ueransim.nas.NasDecoder;
import tr.havelsan.ueransim.nas.NasEncoder;
import tr.havelsan.ueransim.nas.core.messages.NasMessage;
import tr.havelsan.ueransim.nas.core.messages.PlainMmMessage;
import tr.havelsan.ueransim.nas.core.messages.PlainSmMessage;
import tr.havelsan.ueransim.nas.core.messages.SecuredMmMessage;
import tr.havelsan.ueransim.nas.impl.enums.EMessageType;
import tr.havelsan.ueransim.nas.impl.enums.ESecurityHeaderType;
import tr.havelsan.ueransim.nas.impl.enums.ETypeOfCipheringAlgorithm;
import tr.havelsan.ueransim.nas.impl.enums.ETypeOfIntegrityProtectionAlgorithm;
import tr.havelsan.ueransim.structs.NasCount;
import tr.havelsan.ueransim.utils.Logging;
import tr.havelsan.ueransim.utils.Tag;
import tr.havelsan.ueransim.utils.bits.Bit;
import tr.havelsan.ueransim.utils.bits.Bit5;
import tr.havelsan.ueransim.utils.bits.BitString;
import tr.havelsan.ueransim.utils.octets.Octet4;
import tr.havelsan.ueransim.utils.octets.OctetString;

public class NasEncryption {

    //======================================================================================================
    //                                          ENCRYPTION
    //======================================================================================================

    public static SecuredMmMessage encrypt(NasMessage plainNasMessage, NasSecurityContext securityContext) {
        EMessageType msgType;
        if (plainNasMessage instanceof PlainMmMessage) {
            msgType = ((PlainMmMessage) plainNasMessage).messageType;
        } else if (plainNasMessage instanceof PlainSmMessage) {
            msgType = ((PlainSmMessage) plainNasMessage).messageType;
        } else {
            throw new IncorrectImplementationException();
        }

        return encrypt(NasEncoder.nasPdu(plainNasMessage), msgType, securityContext);
    }

    private static SecuredMmMessage encrypt(byte[] plainNasMessage, EMessageType messageType, NasSecurityContext securityContext) {
        var sht = makeSecurityHeaderType(securityContext, messageType);
        var count = securityContext.uplinkCount;
        var cnId = securityContext.connectionIdentifier;
        var intKey = securityContext.keys.kNasInt;
        var encKey = securityContext.keys.kNasEnc;
        var intAlg = securityContext.selectedAlgorithms.integrity;
        var encAlg = securityContext.selectedAlgorithms.ciphering;

        var encryptedData = encryptData(encAlg, count, cnId, plainNasMessage, encKey);
        var mac = computeMac(intAlg, count, cnId, true, intKey, plainNasMessage);

        var secured = new SecuredMmMessage();
        secured.securityHeaderType = sht;
        secured.messageAuthenticationCode = mac;
        secured.sequenceNumber = count.sqn;
        secured.plainNasMessage = encryptedData;

        securityContext.countOnEncrypt();

        return secured;
    }

    private static OctetString encryptData(ETypeOfCipheringAlgorithm alg, NasCount count, EConnectionIdentifier cnId,
                                           byte[] data, OctetString key) {
        Bit5 bearer = new Bit5(cnId.intValue());
        Bit direction = Bit.ZERO;
        BitString message = BitString.from(data);

        byte[] result;

        if (alg.equals(ETypeOfCipheringAlgorithm.EA0)) {
            result = data;
        } else if (alg.equals(ETypeOfCipheringAlgorithm.EA1_128)) {
            result = NEA1_128.encrypt(count.toOctet4(), bearer, direction, message, key).toByteArray();
        } else if (alg.equals(ETypeOfCipheringAlgorithm.EA2_128)) {
            result = NEA2_128.encrypt(count.toOctet4(), bearer, direction, message, key).toByteArray();
        } else if (alg.equals(ETypeOfCipheringAlgorithm.EA3_128)) {
            result = NEA3_128.encrypt(count.toOctet4(), bearer, direction, message, key).toByteArray();
        } else {
            result = null;
        }

        if (result == null) {
            throw new RuntimeException("invalid ciphering alg");
        } else {
            return new OctetString(result);
        }
    }

    //======================================================================================================
    //                                          DECRYPTION
    //======================================================================================================

    public static NasMessage decrypt(SecuredMmMessage protectedNasMessage, NasSecurityContext securityContext) {
        Logging.funcIn("NasEncryption.decrypt");

        var count = securityContext.downlinkCount;
        var cnId = securityContext.connectionIdentifier;
        var intKey = securityContext.keys.kNasInt;
        var encKey = securityContext.keys.kNasEnc;
        var intAlg = securityContext.selectedAlgorithms.integrity;
        var encAlg = securityContext.selectedAlgorithms.ciphering;

        var mac = computeMac(intAlg, count, cnId, false, intKey, protectedNasMessage.plainNasMessage.toByteArray());

        Logging.debug(Tag.VALUE, "computed mac: %s", mac);
        Logging.debug(Tag.VALUE, "mac received in message: %s", protectedNasMessage.messageAuthenticationCode);

        if (!mac.equals(protectedNasMessage.messageAuthenticationCode)) {
            if (!intAlg.equals(ETypeOfIntegrityProtectionAlgorithm.IA0)) {
                Logging.funcOut();
                return null;
            }
        }

        securityContext.countOnDecrypt(protectedNasMessage.sequenceNumber);

        var decryptedData = decryptData(encAlg, count, cnId, encKey, protectedNasMessage.securityHeaderType,
                protectedNasMessage.plainNasMessage.toByteArray());
        var decryptedMsg = NasDecoder.nasPdu(decryptedData);

        Logging.funcOut();
        return decryptedMsg;
    }

    private static OctetString decryptData(ETypeOfCipheringAlgorithm alg, NasCount count, EConnectionIdentifier cnId,
                                           OctetString key, ESecurityHeaderType sht, byte[] data) {
        Logging.funcIn("NasEncryption.decryptData");

        if (!sht.isCiphered()) {
            Logging.funcOut();
            return new OctetString(data);
        }

        Bit5 bearer = new Bit5(cnId.intValue());
        Bit direction = Bit.ONE;
        BitString message = BitString.from(data);

        byte[] res = null;

        if (alg.equals(ETypeOfCipheringAlgorithm.EA0)) {
            res = data;
        }
        if (alg.equals(ETypeOfCipheringAlgorithm.EA1_128)) {
            res = NEA1_128.decrypt(count.toOctet4(), bearer, direction, message, key).toByteArray();
        }
        if (alg.equals(ETypeOfCipheringAlgorithm.EA2_128)) {
            res = NEA2_128.decrypt(count.toOctet4(), bearer, direction, message, key).toByteArray();
        }
        if (alg.equals(ETypeOfCipheringAlgorithm.EA3_128)) {
            res = NEA3_128.decrypt(count.toOctet4(), bearer, direction, message, key).toByteArray();
        }

        if (res == null) {
            Logging.funcOut();
            throw new RuntimeException("invalid ciphering alg");
        }

        Logging.funcOut();
        return new OctetString(res);
    }

    //======================================================================================================
    //                                          COMMON
    //======================================================================================================

    private static Octet4 computeMac(ETypeOfIntegrityProtectionAlgorithm alg, NasCount count, EConnectionIdentifier cnId,
                                     boolean isUplink, OctetString key, byte[] plainMessage) {
        Logging.funcIn("Computing Mac");

        var data = OctetString.concat(new OctetString(count.sqn), new OctetString(plainMessage));

        Logging.debug(Tag.VALUE, "alg: %s", alg);

        if (alg.equals(ETypeOfIntegrityProtectionAlgorithm.IA0)) {
            Logging.funcOut();
            return new Octet4(0);
        }

        Bit5 bearer = new Bit5(cnId.intValue());
        Bit direction = new Bit(isUplink ? 0 : 1);
        BitString message = BitString.from(data);

        Logging.debug(Tag.VALUE, "count: %s", count);
        Logging.debug(Tag.VALUE, "bearer: %s", bearer);
        Logging.debug(Tag.VALUE, "direction: %s", direction);
        Logging.debug(Tag.VALUE, "message: %s", message.toHexString(false));
        Logging.debug(Tag.VALUE, "key: %s", key);

        Octet4 res = null;

        if (alg.equals(ETypeOfIntegrityProtectionAlgorithm.IA1_128)) {
            res = NIA1_128.computeMac(count.toOctet4(), bearer, direction, message, key);
        }
        if (alg.equals(ETypeOfIntegrityProtectionAlgorithm.IA2_128)) {
            res = NIA2_128.computeMac(count.toOctet4(), bearer, direction, message, key);
        }
        if (alg.equals(ETypeOfIntegrityProtectionAlgorithm.IA3_128)) {
            res = NIA3_128.computeMac(count.toOctet4(), bearer, direction, message, key);
        }

        if (res == null) {
            Logging.funcOut();
            throw new RuntimeException("invalid integrity alg");
        }

        Logging.funcOut();
        return res;
    }

    private static ESecurityHeaderType makeSecurityHeaderType(NasSecurityContext securityContext, EMessageType messageType) {
        var encKey = securityContext.keys.kNasEnc;
        var intKey = securityContext.keys.kNasInt;

        boolean ciphered = encKey != null && encKey.length > 0;
        boolean integrityProtected = intKey != null && intKey.length > 0;

        if (!ciphered && !integrityProtected) {
            return ESecurityHeaderType.NOT_PROTECTED;
        }

        if (messageType.equals(EMessageType.SECURITY_MODE_COMPLETE)) {
            return ESecurityHeaderType.INTEGRITY_PROTECTED_AND_CIPHERED_WITH_NEW_SECURITY_CONTEXT;
        }

        // SecurityModeCommand message is always sent by network, so this should be not the case actually.
        // if (messageType.equals(EMessageType.SECURITY_MODE_COMMAND)) {
        //     return ESecurityHeaderType.INTEGRITY_PROTECTED_WITH_NEW_SECURITY_CONTEXT;
        // }

        return ciphered ? ESecurityHeaderType.INTEGRITY_PROTECTED_AND_CIPHERED : ESecurityHeaderType.INTEGRITY_PROTECTED;
    }
}

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

package tr.havelsan.ueransim.api.ue.mm;

import tr.havelsan.ueransim.core.NasSecurityContext;
import tr.havelsan.ueransim.crypto.KDF;
import tr.havelsan.ueransim.crypto.Mac;
import tr.havelsan.ueransim.crypto.PRF;
import tr.havelsan.ueransim.nas.EapEncoder;
import tr.havelsan.ueransim.nas.eap.EapAkaPrime;
import tr.havelsan.ueransim.structs.Supi;
import tr.havelsan.ueransim.structs.UeData;
import tr.havelsan.ueransim.utils.Logging;
import tr.havelsan.ueransim.utils.Tag;
import tr.havelsan.ueransim.utils.octets.Octet;
import tr.havelsan.ueransim.utils.octets.OctetString;

import java.nio.charset.StandardCharsets;

public class UeKeyManagement {
    private static final int N_NAS_enc_alg = 0x01;
    private static final int N_NAS_int_alg = 0x02;
    private static final int N_RRC_enc_alg = 0x03;
    private static final int N_RRC_int_alg = 0x04;
    private static final int N_UP_enc_alg = 0x05;
    private static final int N_UP_int_alg = 0x06;

    public static void deriveKeysSeafAmf(UeData ueData, NasSecurityContext nasSecurityContext) {
        var keys = nasSecurityContext.keys;
        keys.kSeaf = KDF.calculateKey(keys.kAusf, 0x6C, KDF.encodeString(ueData.snn));
        keys.kAmf = KDF.calculateKey(keys.kSeaf, 0x6D, KDF.encodeString(ueData.supi.value), new OctetString("0000"));

        Logging.debug(Tag.VALUE, "kSeaf: %s", nasSecurityContext.keys.kSeaf);
        Logging.debug(Tag.VALUE, "kAmf: %s", nasSecurityContext.keys.kAmf);
    }

    public static void deriveNasKeys(NasSecurityContext securityContext) {
        var kdfEnc = KDF.calculateKey(securityContext.keys.kAmf, 0x69, new Octet(N_NAS_enc_alg).toOctetString(),
                new Octet(securityContext.selectedAlgorithms.ciphering.intValue()).toOctetString());

        var kdfInt = KDF.calculateKey(securityContext.keys.kAmf, 0x69, new Octet(N_NAS_int_alg).toOctetString(),
                new Octet(securityContext.selectedAlgorithms.integrity.intValue()).toOctetString());

        securityContext.keys.kNasEnc = kdfEnc.substring(16, 16);
        securityContext.keys.kNasInt = kdfInt.substring(16, 16);
    }

    /**
     * Calculates K_AUSF for 5G-AKA according to given parameters as specified in 3GPP TS 33.501 Annex A.2
     */
    public static OctetString calculateKAusfFor5gAka(OctetString ck, OctetString ik, String snn, OctetString sqnXorAk) {
        return KDF.calculateKey(OctetString.concat(ck, ik), 0x6A, KDF.encodeString(snn), sqnXorAk);
    }

    /**
     * Calculates CK' and IK' according to given parameters as specified in 3GPP TS 33.501 Annex A.3
     */
    public static OctetString[] calculateCkPrimeIkPrime(OctetString ck, OctetString ik, String snn, OctetString sqnXorAk) {
        var res = KDF.calculateKey(OctetString.concat(ck, ik), 0x20, KDF.encodeString(snn), sqnXorAk);
        return new OctetString[]{res.substring(0, ck.length), res.substring(ck.length)};
    }

    /**
     * Calculates mk for EAP-AKA' according to given parameters as specified in RFC 5448.
     */
    public static OctetString calculateMk(OctetString ckPrime, OctetString ikPrime, Supi supiIdentity) {
        OctetString key = OctetString.concat(ikPrime, ckPrime);
        OctetString input = new OctetString(("EAP-AKA'" + supiIdentity).getBytes(StandardCharsets.US_ASCII));

        // Calculating the 208-octet output
        return PRF.calculatePrfPrime(key, input, 208);
    }

    /**
     * Calculates MAC for EAP-AKA' according to given parameters.
     */
    public static OctetString calculateMacForEapAkaPrime(OctetString kaut, EapAkaPrime message) {
        var eap = message.makeCopy();
        eap.attributes.putMac(new OctetString(new byte[16]));

        var input = new OctetString(EapEncoder.eapPdu(eap));

        var sha = Mac.hmacSha256(kaut, input);
        return sha.substring(0, 16);
    }

    /**
     * Calculates K_AUSF for EAP-AKA' according to given parameters as specified in 3GPP TS 33.501 Annex F.
     */
    public static OctetString calculateKAusfForEapAkaPrime(OctetString mk) {
        // Octets [144...207] are EMSK
        var emsk = mk.substring(144);
        // The most significant 256 bits of EMSK is K_AUSF
        return emsk.substring(0, 32);
    }

    /**
     * Calculates RES* according to given parameters as specified in 3GPP TS 33.501
     *
     * @param key  The input key KEY shall be equal to the concatenation CK || IK of CK and IK.
     * @param snn  The serving network name shall be constructed as specified in the TS.
     * @param rand RAND value
     * @param res  RES value
     */
    public static OctetString calculateResStar(OctetString key, String snn, OctetString rand, OctetString res) {
        var params = new OctetString[]{KDF.encodeString(snn), rand, res};
        var output = KDF.calculateKey(key, 0x6B, params);
        // The (X)RES* is identified with the 128 least significant bits of the output of the KDF.
        return output.substring(output.length - 16);
    }
}

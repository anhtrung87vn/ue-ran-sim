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

package tr.havelsan.ueransim.nas.impl.ies;

import tr.havelsan.ueransim.nas.core.ies.InformationElement3;
import tr.havelsan.ueransim.utils.OctetInputStream;
import tr.havelsan.ueransim.utils.OctetOutputStream;
import tr.havelsan.ueransim.utils.octets.Octet;

public class IEPduSessionIdentity2 extends InformationElement3 {
    public Octet value;

    public IEPduSessionIdentity2() {
    }

    public IEPduSessionIdentity2(Octet value) {
        this.value = value;
    }

    public IEPduSessionIdentity2(int value) {
        this(new Octet(value));
    }

    @Override
    protected IEPduSessionIdentity2 decodeIE3(OctetInputStream stream) {
        var res = new IEPduSessionIdentity2();
        res.value = stream.readOctet();
        return res;
    }

    @Override
    public void encodeIE3(OctetOutputStream stream) {
        stream.writeOctet(value);
    }
}

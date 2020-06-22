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

package tr.havelsan.ueransim.utils;

import tr.havelsan.ueransim.utils.bits.Bit;
import tr.havelsan.ueransim.utils.octets.*;

public class OctetInputStream {
    private final byte[] data;
    private final int length;
    private final boolean isBigEndian;
    private int index;

    public OctetInputStream(byte[] data, boolean isBigEndian) {
        this.data = data;
        this.length = data.length;
        this.index = 0;
        this.isBigEndian = isBigEndian;
    }

    public OctetInputStream(byte[] data) {
        this(data, true);
    }

    /************ Peek Bit ************/

    public int peekBitI(int offset) {
        return peekOctetI(offset / 8) >> (offset % 8) & 1;
    }

    public int peekBitI() {
        return peekBitI(0);
    }

    public boolean peekBitB(int offset) {
        return peekBitI(offset) != 0;
    }

    public boolean peekBitB() {
        return peekBitB(0);
    }

    public Bit peekBit(int offset) {
        return new Bit(peekBitI(offset));
    }

    public Bit peekBit() {
        return peekBit(0);
    }

    /************ Peek Octet ************/

    public int peekOctetI(int offset) {
        return data[index + offset] & 0xFF;
    }

    public int peekOctetI() {
        return peekOctetI(0);
    }

    public Octet peekOctet(int offset) {
        return new Octet(peekOctetI(offset));
    }

    public Octet peekOctet() {
        return peekOctet(0);
    }

    /************ Peek Octet 2 ************/

    public int peekOctet2I(int offset) {
        int big = peekOctetI(isBigEndian ? offset : offset + 1);
        int little = peekOctetI(isBigEndian ? offset + 1 : offset);
        return (big << 8) | little;
    }

    public int peekOctet2I() {
        return peekOctet2I(0);
    }

    public Octet2 peekOctet2(int offset) {
        return new Octet2(peekOctet2I(offset));
    }

    public Octet2 peekOctet2() {
        return peekOctet2(0);
    }

    /************ Peek Octet Array ************/

    public int[] peekOctetArrayI(int offset, int length) {
        int[] res = new int[length];
        for (int i = 0; i < length; i++)
            res[i] = peekOctetI(offset + i);
        return res;
    }

    public Octet[] peekOctetArray(int offset, int length) {
        Octet[] res = new Octet[length];
        for (int i = 0; i < length; i++)
            res[i] = peekOctet(offset + i);
        return res;
    }

    public OctetString peekOctetString(int offset, int length) {
        return new OctetString(peekOctetArray(offset, length));
    }

    public OctetString peekOctetString(int length) {
        return new OctetString(peekOctetArray(0, length));
    }

    /************ Read Octet Array ************/

    public int[] readOctetArrayI(int length) {
        int[] res = new int[length];
        for (int i = 0; i < length; i++)
            res[i] = readOctetI();
        return res;
    }

    public byte[] readOctetArrayB(int length) {
        byte[] res = new byte[length];
        for (int i = 0; i < length; i++)
            res[i] = (byte) readOctetI();
        return res;
    }

    public Octet[] readOctetArray(int length) {
        Octet[] res = new Octet[length];
        for (int i = 0; i < length; i++)
            res[i] = readOctet();
        return res;
    }

    public OctetString readOctetString(int length) {
        return new OctetString(readOctetArray(length));
    }

    public OctetString readOctetString() {
        return readOctetString(length - index);
    }

    /************ Read Octet ************/

    public int readOctetI() {
        int res = data[index] & 0xFF;
        index++;
        return res;
    }

    public Octet readOctet() {
        return new Octet(readOctetI());
    }

    /************ Read Octet 2 ************/

    public int readOctet2I() {
        int big = peekOctetI(isBigEndian ? 0 : 1);
        int little = peekOctetI(isBigEndian ? 1 : 0);
        readOctet();
        readOctet();
        return (big << 8) | little;
    }


    public Octet2 readOctet2() {
        return new Octet2(readOctet2I());
    }

    /************ Read Octet 3 ************/

    public int readOctet3I() {
        int big = peekOctetI(isBigEndian ? 0 : 2);
        int middle = peekOctetI(isBigEndian ? 1 : 1);
        int little = peekOctetI(isBigEndian ? 2 : 0);
        readOctet();
        readOctet();
        readOctet();
        return (big << 16) | (middle << 8) | little;
    }


    public Octet3 readOctet3() {
        return new Octet3(readOctet3I());
    }

    /************ Read Octet 4 ************/

    public Octet4 readOctet4() {
        var octets = readOctetArray(4);
        if (isBigEndian) {
            return new Octet4(octets[0], octets[1], octets[2], octets[3]);
        } else {
            return new Octet4(octets[3], octets[2], octets[1], octets[0]);
        }
    }

    /************ Others ************/

    public boolean hasNext() {
        return index < length;
    }

    public int length() {
        return length;
    }

    public int remaining() {
        return length - index;
    }

    public int currentIndex() { return index; }
}

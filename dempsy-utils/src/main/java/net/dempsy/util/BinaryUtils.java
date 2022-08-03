/*
 * Copyright 2022 Jim Carroll
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dempsy.util;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * A collection of static utilities and values to help with manipulating binary
 * data in java.
 */
public class BinaryUtils {
    /**
     * Holds the highest unsigned short value that can be intified.
     */
    public static final int MAX_UNSIGNED_SHORT = intify((short)-1);

    /**
     * Holds the highest unsigned short value that can be intified.
     */
    public static final long MAX_UNSIGNED_INT = longify(-1);

    /**
     * Holds the highest unsigned short value that can be intified.
     */
    public static final int MAX_UNSIGNED_BYTE = intify((byte)-1);

    /**
     * Mask that will mask in all of the bits in a short
     */
    public static final int SHORT_MASK = (1 << Short.SIZE) - 1;

    /**
     * Mask that will mask in all of the bits in a byte
     */
    public static final int BYTE_MASK = (1 << Byte.SIZE) - 1;

    /**
     * Mask that will mask in all of the bits in an int for a long
     */
    public static final long INT_MASK = (1L << Integer.SIZE) - 1;

    /**
     * convert an int that runs from 0 to 255 into a byte
     */
    public static byte byteify(final int i) {
        return (byte)(i & BYTE_MASK);
    }

    /**
     * Treat a byte as if it's unsigned and store the value which would run from
     * 0 to 255 into an int.
     */
    public static int intify(final byte b) {
        return b & BYTE_MASK;
    }

    /**
     * Treat a short as if it's unsigned and store the value which would run
     * from 0 to 65535 into an int.
     */
    public static int intify(final short s) {
        return s & SHORT_MASK;
    }

    /**
     * Treat an int as if it's unsigned and store the value into a long.
     */
    public static long longify(final int i) {
        return i & INT_MASK;
    }

    /**
     * This will read a fixed length string from the input stream into a String object.
     */
    public static String readByteString(final DataInputStream dis, final int numBytes) throws IOException {
        final byte[] tmpbytes = new byte[numBytes];
        int index = 0;
        for(int i = 0; i < numBytes; i++) {
            final byte tmpbyte = dis.readByte();
            if(tmpbyte != 0) tmpbytes[index++] = tmpbyte;
        }
        final byte[] retStringBytes = new byte[index];
        for(int i = 0; i < index; i++)
            retStringBytes[i] = tmpbytes[i];

        return new String(retStringBytes);
    }
}

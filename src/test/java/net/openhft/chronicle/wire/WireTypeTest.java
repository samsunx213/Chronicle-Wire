/*
 * Copyright 2016-2020 chronicle.software
 *
 *       https://chronicle.software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.openhft.chronicle.wire;

import net.openhft.chronicle.bytes.BytesUtil;
import net.openhft.chronicle.core.OS;
import net.openhft.chronicle.core.pool.ClassAliasPool;
import net.openhft.chronicle.core.util.Time;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

@SuppressWarnings("deprecation")
public class WireTypeTest extends WireTestCommon {

    // Add alias for TestMarshallable class for the test
    static {
        ClassAliasPool.CLASS_ALIASES.addAlias(TestMarshallable.class);
    }

    // Test if the WireType enum is correctly identified by its name
    @Test
    public void testNameFor() {
        // Add alias for WireType class
        ClassAliasPool.CLASS_ALIASES.addAlias(WireType.class);

        // Iterate over each WireType and check if its name is "WireType"
        for (WireType wireType : WireType.values()) {
            assertEquals("WireType", Wires.typeNameFor(wireType));
        }
    }

    // Test conversion from a TestMarshallable object to String representations
    @Test
    public void testAsString() {
        @NotNull TestMarshallable tm = new TestMarshallable();
        tm.setCount(1);
        tm.setName("name");

        // Test Text-based WireType
        assertEquals("!TestMarshallable {\n" +
                "  name: name,\n" +
                "  count: 1\n" +
                "}\n", WireType.TEXT.asString(tm));
        // Test Binary-based WireType
        assertEquals("" +
                        "00000000 b6 10 54 65 73 74 4d 61  72 73 68 61 6c 6c 61 62 ··TestMa rshallab\n" +
                        "00000010 6c 65 82 12 00 00 00 c4  6e 61 6d 65 e4 6e 61 6d le······ name·nam\n" +
                        "00000020 65 c5 63 6f 75 6e 74 a1  01                      e·count· ·       \n",
                WireType.BINARY.asString(tm));

        assertEquals("00000000 10 54 65 73 74 4d 61 72  73 68 61 6c 6c 61 62 6c ·TestMar shallabl\n" +
                "00000010 65 09 00 00 00 04 6e 61  6d 65 01 00 00 00       e·····na me····  \n", WireType.RAW.asString(tm));
    }

    // Test conversion from String representations to a TestMarshallable object
    @Test
    public void testFromString() {
        // Define the text representation
        @NotNull String asText = "!TestMarshallable {\n" +
                "  name: name,\n" +
                "  count: 1\n" +
                "}\n";

        // Create a TestMarshallable object
        @NotNull TestMarshallable tm = new TestMarshallable();
        tm.setCount(1);
        tm.setName("name");

        // Validate Text-based WireType
        assertEquals(tm, WireType.TEXT.fromString(asText));

        // Define the binary representation
        @NotNull String asBinary = "00000000 B6 10 54 65 73 74 4D 61  72 73 68 61 6C 6C 61 62 ··TestMa rshallab\n" +
                "00000010 6C 65 82 11 00 00 00 C4  6E 61 6D 65 E4 6E 61 6D le······ name·nam\n" +
                "00000020 65 C5 63 6F 75 6E 74 01                          e·count·         \n";
        // Validate Binary-based WireType
        assertEquals(tm, WireType.BINARY.fromString(asBinary));

/* NOT Supported
        String asRaw = "00000000 10 54 65 73 74 4D 61 72  73 68 61 6C 6C 61 62 6C ·TestMar shallabl\n" +
                "00000010 65 09 00 00 00 04 6E 61  6D 65 01 00 00 00       e·····na me····  \n";
        assertEquals(tm, WireType.RAW.fromString(asRaw));
*/
    }

    // Test WireType's ability to write and read from a file
    @Test
    public void testFromFile() throws IOException {
        // Create a TestMarshallable object
        @NotNull TestMarshallable tm = new TestMarshallable();
        tm.setCount(1);
        tm.setName("name");

        // Iterate over each WireType for file-based tests
        for (@NotNull WireType wt : WireType.values()) {
            // Skip unsupported WireTypes
            if (wt == WireType.RAW
                    || wt == WireType.READ_ANY
                    || wt == WireType.CSV)
                continue;

            // Create a temporary file
            @NotNull String tmp = OS.getTarget() + "/testFromFile-" + Time.uniqueId();

            // Write the TestMarshallable object to the file
            wt.toFile(tmp, tm);

            // Read the object back from the file and validate
            @Nullable Object o;
            if (wt == WireType.JSON || wt == WireType.JSON_ONLY)
                o = wt.apply(BytesUtil.readFile(tmp)).getValueIn().object(TestMarshallable.class);
            else
                o = wt.fromFile(tmp);

            assertEquals(tm, o);
        }
    }
}

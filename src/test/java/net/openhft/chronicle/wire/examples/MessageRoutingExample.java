/*
 * Copyright 2016-2022 chronicle.software
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

package net.openhft.chronicle.wire.examples;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.bytes.MethodReader;
import net.openhft.chronicle.bytes.util.BinaryLengthLength;
import net.openhft.chronicle.wire.SelfDescribingMarshallable;
import net.openhft.chronicle.wire.Wire;
import net.openhft.chronicle.wire.WireType;

import java.util.HashMap;
import java.util.Map;

import static net.openhft.chronicle.bytes.util.BinaryLengthLength.LENGTH_8BIT;
import static net.openhft.chronicle.core.pool.ClassAliasPool.CLASS_ALIASES;

public class MessageRoutingExample {

    static {
        // used to allow for the objects in the streamed data to take the simple name rather than the full path name of the class
        CLASS_ALIASES.addAlias(Product.class, ProductHandler.class);
    }

    public static void main(String[] args) {
        new MessageRoutingExample().demo();
    }

    /**
     * used to route products to the destination by providing the appropriate ProductHandler
     */
    @FunctionalInterface
    interface Routing {
        ProductHandler to(String destination);
    }

    /**
     * Implement this to read the process of the product events.
     */
    @FunctionalInterface
    interface ProductHandler {
        void product(Product product);
    }

    /**
     * A example of a simple business event
     */
    static class Product extends SelfDescribingMarshallable {
        String name;

        public Product(String name) {
            this.name = name;
        }

        @Override
        public BinaryLengthLength binaryLengthLength() {
            return LENGTH_8BIT;
        }
    }

    // the serialized data gets written to 'wire'
    //  private final Wire wire = new BinaryWire(new HexDumpBytes());
    private final Wire wire = WireType.TEXT.apply(Bytes.allocateElasticOnHeap());

    private void demo() {

        final Map<String, ProductHandler> destinationMap = new HashMap<>();

        // add ProductHandler to handle messages routed to each destination
        destinationMap.put("Italy", product -> System.out.println("Sends the product to Italy, product=" + product));
        destinationMap.put("France", product -> System.out.println("Sends the product to France, product=" + product));
        destinationMap.put("America", product -> System.out.println("Sends the product to America, product=" + product));

        final Routing routing = wire.methodWriter(Routing.class);
        routing.to("Italy").product(new Product("Coffee"));
        routing.to("France").product(new Product("Cheese"));
        routing.to("America").product(new Product("Popcorn"));

        //   System.out.println(wire);
        System.out.println(wire.bytes().toHexString());

        MethodReader methodReader = wire.methodReader((Routing) destinationMap::get);

        boolean success;
        do {
            // true if a message was read
            success = methodReader.readOne();
        } while (success);

    }
}

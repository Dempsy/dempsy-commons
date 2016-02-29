/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dempsy.serialization;

import java.io.IOException;

import net.dempsy.util.io.MessageBufferInput;
import net.dempsy.util.io.MessageBufferOutput;

/**
 * <p>
 * In order to support pluggable serialization, Dempsy (the container and the distributor) can be configured with a serialization scheme. Note, the serialize call is used on the output side and the deserialize
 * is used on the input side but this interface defines them both right now to make it easy to plug in different implementations without managing many objects.
 * </p>
 * 
 * The implementation should be thread safe.
 */
public abstract class Serializer {
    public abstract <T> void serialize(T object, MessageBufferOutput buf) throws IOException;

    public abstract <T> T deserialize(MessageBufferInput is, Class<T> clazz) throws IOException;

    public <T> byte[] serialize(final T object) throws IOException {
        try (MessageBufferOutput out = new MessageBufferOutput()) {
            serialize(object, out);
            out.flush();
            return out.toByteArray();
        }
    }

    public <T> T deserialize(final byte[] data, final Class<T> clazz) throws IOException {
        try (MessageBufferInput is = new MessageBufferInput(data)) {
            return deserialize(is, clazz);
        }
    }
}

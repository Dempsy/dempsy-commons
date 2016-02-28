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

package net.dempsy.serialization.java;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.nokia.dempsy.util.io.MessageBufferInput;
import com.nokia.dempsy.util.io.MessageBufferOutput;

import net.dempsy.serialization.Serializer;

public class JavaSerializer extends Serializer {

    @Override
    public <T> T deserialize(final MessageBufferInput is, final Class<T> clazz) throws IOException {
        try {
            final InputStream in = new ObjectInputStream(is); // don't want to close the underlying stream
            @SuppressWarnings("unchecked")
            final T readObject = (T) ((ObjectInputStream) in).readObject();
            return readObject;
        } catch (final ClassNotFoundException cnfe) {
            throw new IOException(cnfe);
        }
    }

    @Override
    public <T> void serialize(final T object, final MessageBufferOutput buf) throws IOException {
        final ObjectOutputStream out = new ObjectOutputStream(buf);
        out.writeObject(object); // no need to reset the stream since we're tossing it.
        out.flush();
    }
}

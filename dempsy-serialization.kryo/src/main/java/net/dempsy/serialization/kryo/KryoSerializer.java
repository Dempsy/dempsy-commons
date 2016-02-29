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

package net.dempsy.serialization.kryo;

import static net.dempsy.util.SafeString.objectDescription;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import net.dempsy.serialization.Serializer;
import net.dempsy.util.io.MessageBufferInput;
import net.dempsy.util.io.MessageBufferOutput;

/**
 * This is the implementation of the Kryo based serialization for Dempsy. It can be configured with registered classes using Spring by passing a list of {@link Registration} instances to the constructor.
 */
public class KryoSerializer extends Serializer {
    private static Logger logger = LoggerFactory.getLogger(KryoSerializer.class);

    private static final byte[] park = new byte[0];

    public class Holder implements AutoCloseable {
        public final Kryo kryo;
        public final Output output = new Output(0, -1);
        public final Input input = new Input();

        Holder() {
            kryo = new Kryo();
        }

        @Override
        public void close() {
            output.close();
            input.close();
            input.setBuffer(park); // clean input
            output.setBuffer(park, Integer.MAX_VALUE); // clear output
            kryopool.offer(this);
        }
    }

    // need an object pool of Kryo instances since Kryo is not thread safe
    private final ConcurrentLinkedQueue<Holder> kryopool = new ConcurrentLinkedQueue<Holder>();
    private final List<Registration> registrations;
    private final RunKryo kryoRunner;
    private KryoOptimizer optimizer = null;
    private boolean requireRegistration = false;

    public KryoSerializer() {
        this(true);
    }

    /**
     * Create an unconfigured default {@link KryoSerializer} with no registered classes. If you set manageExactClasses then the result of serializing and then deserializing a class by referring to its
     * superclass will result in the original class. Otherwise the class passed to the deserialize call will be the actual class that's deserialized.
     */
    public KryoSerializer(final boolean manageExactClasses) {
        this(manageExactClasses, (Registration[]) null);
    }

    /**
     * Create an {@link KryoSerializer} with the provided registrations. This can be used from a Spring configuration.
     */
    public KryoSerializer(final boolean manageExactClasses, final Registration... regs) {
        this(manageExactClasses, null, regs);
    }

    /**
     * Create an {@link KryoSerializer} with the provided registrations and Application specific Optimizer. This can be used from a Spring configuration.
     */
    public KryoSerializer(final boolean manageExactClasses, final KryoOptimizer optimizer, final Registration... regs) {
        registrations = regs == null ? null : Arrays.asList(regs);
        this.optimizer = optimizer;
        this.kryoRunner = manageExactClasses ? new RunKryo() {

            @Override
            public <T> void doSerialize(final Holder k, final Output output, final T object) {
                k.kryo.writeClassAndObject(output, object);
            }

            @SuppressWarnings("unchecked")
            @Override
            public <T> T doDeserialize(final Holder k, final Input input, final Class<T> clazz) {
                return (T) k.kryo.readClassAndObject(input);
            }
        } : new RunKryo() {

            @Override
            public <T> void doSerialize(final Holder k, final Output output, final T object) {
                k.kryo.writeObject(output, object);
            }

            @Override
            public <T> T doDeserialize(final Holder k, final Input input, final Class<T> clazz) {
                return k.kryo.readObject(input, clazz);
            }
        };
    }

    /**
     * Set the optimizer. This is provided for a dependency injection framework to use. If it's called
     * 
     * @param optimizer
     */
    public synchronized void setKryoOptimizer(final KryoOptimizer optimizer) {
        this.optimizer = optimizer;
        kryopool.clear(); // need to create new Kryo's.
    }

    /**
     * You can require Kryo to serialize only registered classes by passing '<code>true</code>' to setKryoRegistrationRequired. The default is '<code>false</code>'.
     */
    public synchronized void setKryoRegistrationRequired(final boolean requireRegistration) {
        if (this.requireRegistration != requireRegistration) {
            this.requireRegistration = requireRegistration;
            kryopool.clear();
        }
    }

    @Override
    public <T> void serialize(final T object, final MessageBufferOutput buffer) throws IOException {
        try (Holder k = getKryoHolder()) {
            final Output output = k.output;
            // this will allow kryo to grow the buffer as needed.
            output.setBuffer(buffer.getBuffer(), Integer.MAX_VALUE);
            output.setPosition(buffer.getPosition()); // set the position to where we already are.
            kryoRunner.doSerialize(k, output, object);
            // if we resized then we need to adjust the message buffer
            if (output.getBuffer() != buffer.getBuffer())
                buffer.replace(output.getBuffer());
            buffer.setPosition(output.position());
        } catch (final KryoException ke) {
            throw new IOException("Failed to serialize.", ke);
        } catch (final IllegalArgumentException e) { // this happens when requiring registration but serializing an unregistered class
            throw new IOException("Failed to serialize " + objectDescription(object) +
                    " (did you require registration and attempt to serialize an unregistered class?)", e);
        }
    }

    @Override
    public <T> T deserialize(final MessageBufferInput data, final Class<T> clazz) throws IOException {
        try (Holder k = getKryoHolder()) {
            final Input input = k.input;
            input.setBuffer(data.getBuffer(), data.getPosition(), data.getLimit());
            final T ret = kryoRunner.doDeserialize(k, input, clazz);
            data.setPosition(input.position()); // forward to where Kryo finished.
            return ret;
        } catch (final KryoException ke) {
            throw new IOException("Failed to deserialize.", ke);
        } catch (final IllegalArgumentException e) { // this happens when requiring registration but deserializing an unregistered class
            throw new IOException("Failed to deserialize. Did you require registration and attempt to deserialize an unregistered class?", e);
        }
    }

    protected Holder getKryoHolder() {
        Holder ret = kryopool.poll();

        if (ret == null) {
            ret = new Holder();

            if (requireRegistration)
                ret.kryo.setRegistrationRequired(requireRegistration);

            if (optimizer != null) {
                try {
                    optimizer.preRegister(ret.kryo);
                } catch (final Throwable th) {
                    logger.error("Optimizer for KryoSerializer \"" + (optimizer == null ? "[null object]" : optimizer.getClass().getName()) +
                            "\" threw and unepexcted exception.... continuing.", th);
                }
            }

            if (registrations != null) {
                for (final Registration reg : registrations) {
                    try {
                        if (reg.id == -1)
                            ret.kryo.register(Class.forName(reg.classname));
                        else ret.kryo.register(Class.forName(reg.classname), reg.id);
                    } catch (final ClassNotFoundException cnfe) {
                        logger.error("Cannot register the class " + Optional.ofNullable(reg.classname).orElse("null")
                                + " with Kryo because the class couldn't be found.");
                    }
                }
            }

            if (optimizer != null) {
                try {
                    optimizer.postRegister(ret.kryo);
                } catch (final Throwable th) {
                    logger.error("Optimizer for KryoSerializer \"" + (optimizer == null ? "[null object]" : optimizer.getClass().getName()) +
                            "\" threw and unepexcted exception.... continuing.", th);
                }
            }
        }
        return ret;
    }

    private static interface RunKryo {
        <T> void doSerialize(final Holder k, final Output output, final T object);

        <T> T doDeserialize(final Holder k, final Input input, final Class<T> clazz);
    }

}

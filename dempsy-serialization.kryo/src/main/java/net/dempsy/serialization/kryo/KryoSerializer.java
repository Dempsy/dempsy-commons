/*
 * Copyright 2012 the original author or authors.
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

package net.dempsy.serialization.kryo;

import static net.dempsy.util.SafeString.objectDescription;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.serialization.Serializer;
import net.dempsy.util.io.MessageBufferInput;
import net.dempsy.util.io.MessageBufferOutput;

/**
 * <p>
 * This is the implementation of the Kryo based serialization for Dempsy.
 * It can be configured with registered classes using Spring by passing a
 * list of {@link Registration} instances to the constructor.
 * </p>
 *
 * <p>
 * The serializer will also pick up registrations from a file on the classpath
 * if it's provided. It will default to {@link KryoSerializer#KRYO_REGISTRATION_FILE}
 * but can be overridden by supplying the system property with the name
 * {@link KryoSerializer#SYS_PROP_REGISTRAION_RESOURCE}.
 * </p>
 */
public class KryoSerializer extends Serializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(KryoSerializer.class);

    public static final String KRYO_REGISTRATION_FILE = "kryo-registrations.txt";
    public static final String SYS_PROP_REGISTRAION_RESOURCE = "kryo-registration";
    private static final byte[] park = new byte[0];

    private class Holder implements AutoCloseable {
        public final Kryo kryo;
        public final Output output = new Output(0, -1);
        public final Input input = new Input();

        Holder() {
            kryo = createKryoInstance();
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
     * Create an unconfigured default {@link KryoSerializer} with no registered classes.
     * If you set manageExactClasses then the result of serializing and then deserializing
     * a class by referring to its superclass will result in the original class.
     * Otherwise the class passed to the deserialize call will be the actual class that's
     * deserialized.
     */
    public KryoSerializer(final boolean manageExactClasses) {
        this(manageExactClasses, (Registration[])null);
    }

    /**
     * Create an {@link KryoSerializer} with the provided registrations.
     * This can be used from a Spring configuration.
     */
    public KryoSerializer(final boolean manageExactClasses, final Registration... regs) {
        this(manageExactClasses, null, regs);
    }

    /**
     * Create an {@link KryoSerializer} with the provided registrations and
     * Application specific Optimizer. This can be used from a Spring configuration.
     */
    public KryoSerializer(final boolean manageExactClasses, final KryoOptimizer optimizer, final Registration... regs) {
        registrations = loadRegistrations(regs);
        this.optimizer = optimizer;
        kryoRunner = manageExactClasses ? new RunKryo() {

            @Override
            public <T> void doSerialize(final Holder k, final Output output, final T object) {
                k.kryo.writeClassAndObject(output, object);
            }

            @SuppressWarnings("unchecked")
            @Override
            public <T> T doDeserialize(final Holder k, final Input input, final Class<T> clazz) {
                return (T)k.kryo.readClassAndObject(input);
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
     * This can be overloaded by a subclass to produce a specific configured Kryo.
     * Keep in mind this will be called for each thread doing serialization so it
     * needs to return a consistent but unshared instance.
     */
    protected Kryo createKryoInstance() {
        return new Kryo();
    }

    private static List<Registration> loadRegistrations(final Registration... regs) {
        // if there are any registrations passed in then those go first.
        final List<Registration> ret = regs == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(regs));

        // now see if there's a registration file on the classpath.
        final String regResource = System.getProperty(SYS_PROP_REGISTRAION_RESOURCE, KRYO_REGISTRATION_FILE);
        final InputStream in = KryoSerializer.class.getClassLoader().getResourceAsStream(regResource);
        if(in != null) {
            // then we have a text file to read.
            final BufferedReader br = new BufferedReader(new InputStreamReader(in, Charset.forName("UTF-8")));
            String line;
            try {
                while((line = br.readLine()) != null) {
                    line = line.trim();
                    if(!("".equals(line) || " ".equals(line))) {
                        LOGGER.debug("Adding the class {}", line);
                        ret.add(new Registration(line));
                    }
                }
            } catch(final IOException ioe) {
                LOGGER.error("Failed to read the file " + KRYO_REGISTRATION_FILE + " from the classpath.", ioe);
                throw new RuntimeException("Failed to read the file " + KRYO_REGISTRATION_FILE + " from the classpath.", ioe);
            }
        } else {
            final String wasSet = System.getProperty(SYS_PROP_REGISTRAION_RESOURCE);
            if(wasSet != null)
                throw new RuntimeException("Can't find " + wasSet + " on the classpath.");
            // otherwise just log there was no registration
            LOGGER.debug("No regisration resource found.");
        }

        return ret;
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
     * You can require Kryo to serialize only registered classes by passing '<code>true</code>' to setKryoRegistrationRequired. The default is
     * '<code>false</code>'.
     */
    public synchronized void setKryoRegistrationRequired(final boolean requireRegistration) {
        if(this.requireRegistration != requireRegistration) {
            this.requireRegistration = requireRegistration;
            kryopool.clear();
        }
    }

    @Override
    public <T> void serialize(final T object, final MessageBufferOutput buffer) throws IOException {
        try(Holder k = getKryoHolder()) {
            final Output output = k.output;
            // this will allow kryo to grow the buffer as needed.
            output.setBuffer(buffer.getBuffer(), Integer.MAX_VALUE);
            output.setPosition(buffer.getPosition()); // set the position to where we already are.
            kryoRunner.doSerialize(k, output, object);
            // if we resized then we need to adjust the message buffer
            if(output.getBuffer() != buffer.getBuffer())
                buffer.replace(output.getBuffer());
            buffer.setPosition(output.position());
        } catch(final KryoException ke) {
            throw new IOException("Failed to serialize.", ke);
        } catch(final IllegalArgumentException e) { // this happens when requiring registration but serializing an unregistered class
            throw new IOException("Failed to serialize " + objectDescription(object) +
                " (did you require registration and attempt to serialize an unregistered class?)", e);
        }
    }

    @Override
    public <T> T deserialize(final MessageBufferInput data, final Class<T> clazz) throws IOException {
        try(Holder k = getKryoHolder()) {
            final Input input = k.input;
            input.setBuffer(data.getBuffer(), data.getPosition(), data.getLimit());
            final T ret = kryoRunner.doDeserialize(k, input, clazz);
            data.setPosition(input.position()); // forward to where Kryo finished.
            return ret;
        } catch(final KryoException ke) {
            throw new IOException("Failed to deserialize.", ke);
        } catch(final IllegalArgumentException e) { // this happens when requiring registration but deserializing an unregistered class
            throw new IOException("Failed to deserialize. Did you require registration and attempt to deserialize an unregistered class?", e);
        }
    }

    protected Holder getKryoHolder() {
        Holder ret = kryopool.poll();

        if(ret == null) {
            ret = new Holder();

            if(requireRegistration)
                ret.kryo.setRegistrationRequired(requireRegistration);

            if(optimizer != null) {
                try {
                    optimizer.preRegister(ret.kryo);
                } catch(final Throwable th) {
                    LOGGER.error("Optimizer for KryoSerializer \"" + (optimizer == null ? "[null object]" : optimizer.getClass().getName()) +
                        "\" threw and unepexcted exception.... continuing.", th);
                }
            }

            if(registrations != null) {
                for(final Registration reg: registrations) {
                    if(LOGGER.isTraceEnabled())
                        LOGGER.trace("Registering classname " + reg.classname + (reg.id >= 0 ? (" with an id of " + reg.id) : "") + " with Kryo.");
                    try {
                        if(reg.id == -1)
                            ret.kryo.register(Class.forName(reg.classname));
                        else
                            ret.kryo.register(Class.forName(reg.classname), reg.id);
                    } catch(final ClassNotFoundException cnfe) {
                        LOGGER.error("Cannot register the class " + Optional.ofNullable(reg.classname).orElse("null")
                            + " with Kryo because the class couldn't be found.");
                    }
                }
            }

            if(optimizer != null) {
                try {
                    optimizer.postRegister(ret.kryo);
                } catch(final Throwable th) {
                    LOGGER.error("Optimizer for KryoSerializer \"" + (optimizer == null ? "[null object]" : optimizer.getClass().getName()) +
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

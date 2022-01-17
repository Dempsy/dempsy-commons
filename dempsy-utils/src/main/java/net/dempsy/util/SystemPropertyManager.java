package net.dempsy.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * This class allows the setting and then unsetting of System properties within the scope of a test.
 */
public class SystemPropertyManager implements QuietCloseable {

    private static class OldProperty {
        public final boolean hasOldValue;
        public final String oldValue;
        public final String name;

        public OldProperty(final String name, final boolean hasOldValue, final String oldValue) {
            this.hasOldValue = hasOldValue;
            this.oldValue = oldValue;
            this.name = name;
        }
    }

    private final List<OldProperty> oldProperties = new ArrayList<>();

    public SystemPropertyManager optionally(final boolean doIt, final Consumer<SystemPropertyManager> c) {
        if(doIt)
            c.accept(this);
        return this;
    }

    public SystemPropertyManager set(final String name, final String value) {
        synchronized(SystemPropertyManager.class) {
            return internSet(name, value);
        }
    }

    public SystemPropertyManager remove(final String name) {
        synchronized(SystemPropertyManager.class) {
            return internRemove(name);
        }
    }

    public SystemPropertyManager setIfAbsent(final String name, final String value) {
        synchronized(SystemPropertyManager.class) {
            return System.getProperties().containsKey(name) ? this : internSet(name, value);
        }
    }

    public SystemPropertyManager load(final String file) {
        final Properties props = new Properties();
        try(FileInputStream fis = new FileInputStream(file)) {
            props.load(fis);
            synchronized(SystemPropertyManager.class) {
                props.entrySet().stream().forEach(e -> internSet((String)e.getKey(), (String)e.getValue()));
            }
            return this;
        } catch(final IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    @Override
    public void close() {
        final int num = oldProperties.size();
        synchronized(SystemPropertyManager.class) {
            IntStream.range(0, num).forEach(i -> revert(oldProperties.get(num - i - 1)));
        }
    }

    private static void revert(final OldProperty op) {
        if(op.hasOldValue)
            System.setProperty(op.name, op.oldValue);
        else
            System.clearProperty(op.name);
    }

    private SystemPropertyManager internSet(final String name, final String value) {
        oldProperties.add(new OldProperty(name, System.getProperties().containsKey(name), System.getProperty(name)));
        System.setProperty(name, value);
        return this;
    }

    private SystemPropertyManager internRemove(final String name) {
        oldProperties.add(new OldProperty(name, System.getProperties().containsKey(name), System.getProperty(name)));
        System.getProperties().remove(name);
        return this;
    }
}

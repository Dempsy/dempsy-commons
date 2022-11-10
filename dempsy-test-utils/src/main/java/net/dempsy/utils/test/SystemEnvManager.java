package net.dempsy.utils.test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SystemEnvManager implements AutoCloseable {

    private final Map<String, String> formerValues = new HashMap<>();
    private final Set<String> vars = new HashSet<>();

    @Override
    public void close() {
        vars.forEach(k -> {
            if(formerValues.containsKey(k)) {
                final String oldVal = formerValues.get(k);
                set(k, oldVal);
            } else
                set(k, null, true);
        });
    }

    public SystemEnvManager set(final String key, final String val) {
        return set(key, val, false);
    }

    public SystemEnvManager clear(final String key) {
        return set(key, null, true);
    }

    @SuppressWarnings("unchecked")
    private SystemEnvManager set(final String key, final String val, final boolean clear) {
        try {
            final Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
            final Field theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment");
            theEnvironmentField.setAccessible(true);

            final Map<String, String> env = (Map<String, String>)theEnvironmentField.get(null);
            if(env.containsKey(key))
                formerValues.put(key, env.get(key));
            if(clear)
                env.remove(key);
            else
                env.put(key, val);
            final Field theCaseInsensitiveEnvironmentField = processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment");
            theCaseInsensitiveEnvironmentField.setAccessible(true);
            final Map<String, String> cienv = (Map<String, String>)theCaseInsensitiveEnvironmentField.get(null);
            if(clear)
                cienv.remove(key);
            else
                cienv.put(key, val);
            vars.add(key);
        } catch(final NoSuchFieldException e) {
            try {
                final Class<?>[] classes = Collections.class.getDeclaredClasses();
                final Map<String, String> env = System.getenv();
                for(final Class<?> cl: classes) {
                    if("java.util.Collections$UnmodifiableMap".equals(cl.getName())) {
                        final Field field = cl.getDeclaredField("m");
                        field.setAccessible(true);
                        final Object obj = field.get(env);
                        final Map<String, String> map = (Map<String, String>)obj;
                        if(map.containsKey(key))
                            formerValues.put(key, map.get(key));
                        if(clear)
                            map.remove(key);
                        else
                            map.put(key, val);
                    }
                }
                vars.add(key);
            } catch(final NoSuchFieldException | IllegalAccessException nsf) {
                throw new RuntimeException("Something changed in java itself, or this is an unsupported platform.", nsf);
            }
        } catch(final ClassNotFoundException cnfe) {
            throw new RuntimeException("Something changed in java itself, or this is an unsupported platform.", cnfe);
        } catch(final IllegalAccessException iae) {
            throw new RuntimeException("Something changed in java itself, or this is an unsupported platform.", iae);
        } catch(final Exception iae) {
            throw new RuntimeException("Something changed in java itself, or this is an unsupported platform.", iae);
        }
        return this;
    }

}

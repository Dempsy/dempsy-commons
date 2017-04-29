package net.dempsy.serialization.util;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import net.dempsy.serialization.Serializer;
import net.dempsy.util.io.MessageBufferInput;
import net.dempsy.util.io.MessageBufferOutput;

/**
 * Some serializations (a.k.a, Kryo) can be made more efficient if you register 
 * classes that will be serialized and deserialized. This class will allow you 
 * to track which classes are serialized and deserialized and how many of each.
 */
public class ClassTracker extends Serializer {
    public final Serializer proxied;

    private static final ConcurrentHashMap<Class<?>, AtomicLong> counts = new ConcurrentHashMap<>();

    public ClassTracker(final Serializer proxied) {
        this.proxied = proxied;
    }

    @Override
    public <T> void serialize(final T object, final MessageBufferOutput buf) throws IOException {
        proxied.serialize(track(object), buf);
    }

    @Override
    public <T> T deserialize(final MessageBufferInput is, final Class<T> clazz) throws IOException {
        return track(proxied.deserialize(is, clazz));
    }

    private <T> T track(final T object) {
        if (object == null)
            return null;
        return track(object, object.getClass());
    }

    private List<Field> getAllFields(final Class<?> clazz) {
        final List<Field> ret = new ArrayList<>();
        ret.addAll(Arrays.asList(clazz.getDeclaredFields()));
        final Class<?> parent = clazz.getSuperclass();
        if (parent != null && !parent.isInterface())
            ret.addAll(getAllFields(parent));
        return ret;
    }

    private <T> T track(final T object, final Class<?> clazz) {
        final AtomicLong newOne;
        final AtomicLong there = counts.putIfAbsent(clazz, newOne = new AtomicLong(0));
        final AtomicLong cur = there == null ? newOne : there;
        cur.incrementAndGet();
        if (object == null)
            return null;
        final List<Field> fields = getAllFields(clazz);
        for (final Field f : fields) {
            if (!Modifier.isTransient(f.getModifiers()) && !Modifier.isStatic(f.getModifiers())) {
                if (f.getType().isPrimitive()) {
                    return track(null, f.getType());
                } else {

                    boolean iSetAccessible = false;
                    if (!f.isAccessible()) {
                        iSetAccessible = true;
                        f.setAccessible(true);
                    }
                    try {

                        track(f.get(object));
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                        e.printStackTrace();
                    }
                    if (iSetAccessible)
                        f.setAccessible(false);
                }
            }
        }

        return object;
    }

    private static class Pair {
        public final Class<?> clazz;
        public final long count;

        public Pair(final Class<?> clazz, final long count) {
            this.clazz = clazz;
            this.count = count;
        }
    }

    public static void dumpResults() {
        final List<Pair> pairs = new ArrayList<>();
        counts.forEach((c, l) -> pairs.add(new Pair(c, l.get())));
        // sort descending---------v
        Collections.sort(pairs, (o2, o1) -> (o1.count > o2.count ? 1 : (o1.count < o2.count ? -1 : 0)));
        pairs.forEach(p -> System.out.println(p.clazz.getName() + "      " + p.count));
        counts.clear();
    }
}

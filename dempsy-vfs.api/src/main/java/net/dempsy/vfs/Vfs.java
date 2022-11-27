package net.dempsy.vfs;

import static net.dempsy.util.Functional.recheck;
import static net.dempsy.util.Functional.uncheck;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.QuietCloseable;
import net.dempsy.vfs.apache.ApacheVfsFileSystem;
import net.dempsy.vfs.impl.ClasspathFileSystem;
import net.dempsy.vfs.local.LocalFileSystem;

public class Vfs implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Vfs.class);

    private final ConcurrentHashMap<String, FileSystem> fileSystems = new ConcurrentHashMap<>();
    private Context context = null;

    public Vfs(final FileSystem... fileSystems) throws IOException {
        if(fileSystems != null && fileSystems.length > 0)
            recheck(() -> Arrays.stream(fileSystems).forEach(s -> uncheck(() -> register(s))), IOException.class);

        registerStandardFileSystems();
    }

    public String[] supportedSchemes() {
        return fileSystems.keySet().stream().toArray(String[]::new);
    }

    public FileSystem fileSystemFromScheme(final String scheme) {
        if(scheme == null)
            throw new NullPointerException("Null scheme used to look up a FileSystem");

        return fileSystems.get(scheme);
    }

    public FileSystem fileSystem(final URI uri) {
        final String scheme = uri.getScheme();
        if(scheme == null) {
            final String msg = String.format("URI (%s) has no scheme.", uri.toString());
            LOGGER.info(msg);
            throw new IllegalArgumentException(msg);
        }
        return fileSystems.get(scheme);
    }

    public Path toPath(final URI uri) throws IOException {
        final FileSystem fs = fileSystem(uri);
        if(fs == null)
            throw new IOException("Unsupported scheme \"" + uri.getScheme() + "\" for URI " + uri);

        return fs.createPath(uri);
    }

    public File toFile(final URI uri) throws IOException {
        final FileSystem fs = fileSystem(uri);
        if(fs == null)
            throw new IOException("Unsupported scheme \"" + uri.getScheme() + "\" for URI " + uri);
        return fs.toFile(uri);
    }

    public Context context() {
        if(context == null)
            context = new Context();

        context.count++;
        return context;
    }

    public class Context implements QuietCloseable {
        private final LinkedList<AutoCloseable> toClose = new LinkedList<>();
        private final Map<String, AutoCloseable> lookup = new HashMap<>();
        private int count = 0;

        public <T extends AutoCloseable> T add(final String key, final T resource) {
            if(resource != null) {
                toClose.add(0, resource);
                lookup.put(key, resource);
            }
            return resource;
        }

        public <T extends AutoCloseable> T get(final String key, final Supplier<T> makeIfAbsent) {
            @SuppressWarnings("unchecked")
            T ret = (T)lookup.get(key);
            if(ret == null && makeIfAbsent != null) {
                ret = makeIfAbsent.get();
                if(ret != null)
                    add(key, ret);
            }
            return ret;
        }

        @Override
        public void close() {
            count--;
            if(count <= 0) {
                toClose.stream().forEach(r -> uncheck(() -> r.close()));
                context = null;
            }
        }
    }

    @Override
    public void close() throws IOException {
        // recheck(() -> fileSystems.values().forEach(fs -> uncheck(() -> fs.close())), IOException.class);
    }

    public static class SpringHackDummyFs extends FileSystem {

        @Override
        protected Path doCreatePath(final URI uri) throws IOException {
            return null;
        }

        @Override
        public String[] supportedSchemes() {
            return new String[] {};
        }
    }

    private void register(final FileSystem fs) {
        Arrays.stream(fs.supportedSchemes()).forEach(s -> register(s, fs));
    }

    private void register(final String scheme, final FileSystem fs) {
        if(fileSystems.containsKey(scheme))
            LOGGER.warn(
                "File system for scheme \"{}\" of type {} is being ignored because a previously registered filesystem of type {} is already handling it.",
                scheme, valueOfClass(fs), valueOfClass(fileSystems.get(scheme)));
        else {
            fileSystems.put(scheme, fs);
            fs.setVfs(this);
        }
    }

    private void registerStandardFileSystems() throws IOException {
        final Set<String> knownSchemes = fileSystems.keySet();
        if(!knownSchemes.contains("classpath"))
            register("classpath", new ClasspathFileSystem());

        if(!knownSchemes.contains("file"))
            register("file", new LocalFileSystem());

        final ApacheVfsFileSystem afs = new ApacheVfsFileSystem();
        for(final String scheme: afs.supportedSchemes())
            if(!knownSchemes.contains(scheme) && !"hdfs".equals(scheme))
                register(scheme, afs);
    }

    private static String valueOfClass(final Object o) {
        return o == null ? "[null object has no class]" : o.getClass().getName();
    }
}

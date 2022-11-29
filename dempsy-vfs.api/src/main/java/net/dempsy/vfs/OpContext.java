package net.dempsy.vfs;

import static net.dempsy.util.Functional.uncheck;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.Functional;
import net.dempsy.util.QuietCloseable;

public class OpContext implements QuietCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpContext.class);

    private final Map<Path, Map<String, QuietCloseable>> pathData = new HashMap<>();
    private final Vfs owner;
    private final OpContext parent;
    private final List<OpContext> children = new ArrayList<>();
    private final Map<URI, Path> paths = new HashMap<>();
    private boolean isClosed = false;

    OpContext(final Vfs owner, final OpContext parent) {
        this.owner = owner;
        this.parent = parent;
    }

    @Override
    public void close() {
        if(!isClosed) {
            isClosed = true;
            if(LOGGER.isTraceEnabled())
                LOGGER.trace("Closing OpContext at level {}", getLevel());
            pathData.values().stream()
                .flatMap(m -> m.values().stream())
                .forEach(c -> c.close());
            if(parent != null)
                parent.children.remove(this);
            pathData.clear();
        }
    }

    @SuppressWarnings("unchecked")
    synchronized <T extends QuietCloseable> T get(final Path path, final String key, final Supplier<T> supplier) {
        checkClosed();
        {
            // need to find the context for the path looking up or down.
            final Map<String, QuietCloseable> cur = fullContext()
                .map(c -> c.rawCheck(path))
                .filter(c -> c != null)
                .findFirst()
                .orElse(null);

            if(cur != null) {
                if(supplier != null) {
                    return (T)cur.computeIfAbsent(key, p -> supplier.get());
                }
                return (T)cur.get(key);
            }
        }

        // okay, make it here since above failed or we would have returned already
        if(supplier != null) {
            final Map<String, QuietCloseable> cur = pathData.computeIfAbsent(path, p -> new HashMap<>());
            return (T)cur.computeIfAbsent(key, p -> supplier.get());
        }

        return null;
    }

    public Path toPath(final URI uri) throws IOException {
        // see if we already have the path cached.
        Path ret = fullContext()
            .map(c -> c.rawGetPath(uri))
            .filter(u -> u != null)
            .findFirst()
            .orElse(null);

        if(ret == null) {
            final FileSystem fs = owner.fileSystem(uri);
            if(fs == null)
                throw new IOException("Unsupported scheme \"" + uri.getScheme() + "\" for URI " + uri);

            ret = fs.createPath(uri, this);
            if(ret != null) {
                paths.put(uri, ret);
                final URI altUri = ret.uri();
                if(!uri.equals(altUri))
                    paths.put(altUri, ret);
            }
        }
        return ret;
    }

    public OpContext sub() {
        final var ret = new OpContext(owner, this);
        children.add(ret);
        return ret;
    }

    private Map<String, QuietCloseable> rawCheck(final Path path) {
        final var ret = pathData.get(path);
        if(ret != null) {
            if(LOGGER.isDebugEnabled())
                LOGGER.debug("Found path: {} at level {}", uncheck(() -> path.uri()), getLevel());
        }
        return ret;
    }

    private int getLevel() {
        int count = 0;
        var p = parent;
        while(p != null) {
            count++;
            p = p.parent;
        }
        return count;
    }

    private Path rawGetPath(final URI uri) {
        return paths.get(uri);
    }

    private List<OpContext> makeChildList(final List<OpContext> list) {
        list.addAll(children);
        children.forEach(c -> c.makeChildList(list));
        return list;
    }

    private Stream<OpContext> fullContext() {
        return Stream.concat(
            Stream.concat(
                Stream.of(this),
                Functional.enumerationAsStream(
                    new Enumeration<OpContext>() {
                        OpContext cur = parent;

                        @Override
                        public boolean hasMoreElements() {
                            return cur != null;
                        }

                        @Override
                        public OpContext nextElement() {
                            final var ret = cur;
                            cur = cur.parent;
                            return ret;
                        }
                    })),
            makeChildList(new ArrayList<>()).stream());
    }

    private void checkClosed() {
        if(isClosed)
            throw new IllegalStateException("Cannot perform an operation on a path if the ops context is closed");
    }
}

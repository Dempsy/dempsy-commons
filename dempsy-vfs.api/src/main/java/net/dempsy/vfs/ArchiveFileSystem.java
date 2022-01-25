package net.dempsy.vfs;

import static net.dempsy.util.Functional.chain;
import static net.dempsy.util.Functional.uncheck;
import static net.dempsy.util.UriUtils.SEP;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;

import net.dempsy.util.UriUtils;

public abstract class ArchiveFileSystem extends RecursiveFileSystem {

    public class ArchiveEntryPath extends Path {
        private final URI archiveUri;
        private final URI entryUri;
        private final boolean isDirectory;
        private final String pathInsideArchive;
        private final boolean isRoot;
        private final List<ArchiveEntryPath> children = new ArrayList<>();
        private final long lastModifiedTime;
        private final String scheme;
        private final long length;

        public ArchiveEntryPath(final URI uri, final URI entryUri, final String pathInsideArchive, final boolean isDirectory, final long lastModifiedTime,
            final long length) {
            this.archiveUri = uri;
            this.entryUri = entryUri;
            this.isDirectory = isDirectory;
            this.pathInsideArchive = pathInsideArchive;
            this.isRoot = UriUtils.isRoot(pathInsideArchive);
            this.lastModifiedTime = lastModifiedTime;
            this.scheme = entryUri.getScheme();
            this.length = length;
        }

        public ArchiveEntryPath(final URI uri, final URI entryUri, final ArchiveEntry ae) {
            this(uri, entryUri, ae.getName(), ae.isDirectory(), ae.getLastModifiedDate().getTime(), ae.getSize());
        }

        @Override
        public OutputStream write() throws IOException {
            throw new UnsupportedOperationException("tar file entries are not writable.");
        }

        @Override
        public URI uri() {
            return entryUri;
        }

        @Override
        public InputStream read() throws IOException {

            final ArchiveInputStream is = createArchiveInputStream(scheme, vfs.toPath(archiveUri).read());
            try {
                for(ArchiveEntry cur = is.getNextEntry(); cur != null; cur = is.getNextEntry()) {
                    final String curPathInside = cur.getName();
                    if(pathInsideArchive.equals(curPathInside) || (isRoot && UriUtils.isRoot(curPathInside)))
                        return is;
                }
                throw new IOException("Couldn't find the stream for " + uri());
            } catch(final IOException ioe) {
                if(is != null)
                    is.close();
                throw ioe;
            } catch(final RuntimeException re) {
                if(is != null)
                    is.close();
                throw re;
            }
        }

        @Override
        public boolean isDirectory() {
            return isDirectory;
        }

        @Override
        public Path[] list() throws IOException {
            return isDirectory ? children.toArray(Path[]::new) : null;
        }

        @Override
        public boolean exists() throws IOException {
            return true;
        }

        @Override
        public void delete() throws IOException {
            throw new UnsupportedOperationException("tar file entries are not deletable.");
        }

        @Override
        public long lastModifiedTime() throws IOException {
            return lastModifiedTime;
        }

        @Override
        public long length() throws IOException {
            return length;
        }
    }

    public static class ArchiveUriData {
        public final URI archiveUri;
        public final String pathInsideArchive;

        public ArchiveUriData(final URI archiveUri, final String pathInsideArchive) {
            this.archiveUri = archiveUri;
            this.pathInsideArchive = pathInsideArchive;
        }
    }

    protected abstract ArchiveInputStream createArchiveInputStream(String scheme, InputStream streamToInnerUriFileSystem) throws IOException;

    protected abstract URI makeUriForArchiveEntry(final String scheme, final URI uri, final String pathInsideTarFile) throws IOException;

    @Override
    protected Path doCreatePath(final URI uriX) throws IOException {

        final SplitUri split = uncheck(() -> splitUri(uriX.toString(), null));

        final LinkedHashMap<String, ArchiveEntryPath> cache = new LinkedHashMap<>();
        final var root = buildTree(uriX.getScheme(), split.baseUri, cache);

        final String searchPath = removeTrailingSlash(split.remainder);
        final var ret = UriUtils.isRoot(searchPath) ? root : cache.get(searchPath);

        if(ret == null)
            return new Path() {

                @Override
                public OutputStream write() throws IOException {
                    throw new FileNotFoundException(uriX + " doesn't exist.");
                }

                @Override
                public URI uri() {
                    return uriX;
                }

                @Override
                public InputStream read() throws IOException {
                    throw new FileNotFoundException(uriX + " doesn't exist.");
                }

                @Override
                public Path[] list() throws IOException {
                    throw new FileNotFoundException(uriX + " doesn't exist.");
                }

                @Override
                public boolean exists() throws IOException {
                    return false;
                }

                @Override
                public void delete() throws IOException {
                    throw new FileNotFoundException(uriX + " doesn't exist.");
                }

                @Override
                public long lastModifiedTime() throws IOException {
                    throw new FileNotFoundException(uriX + " doesn't exist.");
                }

                @Override
                public long length() throws IOException {
                    throw new FileNotFoundException(uriX + " doesn't exist.");
                }
            };
        return ret;
    }

    private ArchiveEntryPath makePathForArchiveEntry(final String thsScheme, final URI uriOfArchive, final ArchiveEntry ae) throws IOException {
        final URI entryUri = makeUriForArchiveEntry(thsScheme, uriOfArchive, ae.getName());
        return chain(new ArchiveEntryPath(uriOfArchive, entryUri, ae), p -> p.setVfs(vfs));
    }

    private static String removeTrailingSlash(final String str) {
        return str.endsWith("/") ? str.substring(0, str.length() - 1) : str;
    }

    private ArchiveEntryPath buildTree(final String thsScheme, final URI archiveUri, final LinkedHashMap<String, ArchiveEntryPath> cache) throws IOException {
        try(final ArchiveInputStream is = createArchiveInputStream(thsScheme, vfs.toPath(archiveUri).read());) {
            for(ArchiveEntry cur = is.getNextEntry(); cur != null; cur = is.getNextEntry()) {

                final String curPath = removeTrailingSlash(cur.getName());

                if(cache.containsKey(curPath))
                    throw new IllegalStateException(
                        "There appears to be two files with the same name (\"" + curPath + "\") inside of the archive at " + archiveUri);

                final ArchiveEntryPath curAePath = makePathForArchiveEntry(thsScheme, archiveUri, cur);
                cache.put(curPath, curAePath);
            }
        }

        final Set<String> pathsHooked = new HashSet<>();
        // now we need to structure the tree
        ArchiveEntryPath tree = null;
        final Set<Map.Entry<String, ArchiveEntryPath>> entries = new LinkedHashSet<>(cache.entrySet());
        for(final var entry: entries) {
            ArchiveEntryPath prev = entry.getValue();

            for(boolean done = false; !done;) {
                final String tPp = UriUtils.getParent(prev.pathInsideArchive);
                final String parentPath = tPp == null ? "" : UriUtils.getParent(prev.pathInsideArchive);
                final ArchiveEntryPath parent;
                final boolean isRoot = UriUtils.isRoot(parentPath);
                if(!cache.containsKey(parentPath)) {
                    parent = makeArchiveEntryPath(thsScheme, archiveUri, parentPath);
                    if(isRoot) {
                        if(tree != null)
                            throw new IllegalStateException("Multiple roots to the tree? " + archiveUri);
                        tree = parent;
                        pathsHooked.add(parent.pathInsideArchive);
                    }
                    cache.put(parentPath, parent);
                } else {
                    parent = cache.get(parentPath);
                    if(pathsHooked.contains(parent.pathInsideArchive))
                        done = true;
                }

                parent.children.add(prev);
                pathsHooked.add(prev.pathInsideArchive);

                if(isRoot)
                    done = true;
                else
                    prev = parent;
            }
        }
        if(tree == null)
            throw new IllegalStateException("No root for tree? " + archiveUri);
        return tree;
    }

    private ArchiveEntryPath makeArchiveEntryPath(final String scheme, final URI uriOfArchive, final String pathInsideArchive) throws IOException {
        return chain(
            new ArchiveEntryPath(uriOfArchive, makeUriForArchiveEntry(scheme, uriOfArchive, pathInsideArchive), pathInsideArchive, true,
                System.currentTimeMillis(), 0),
            p -> p.setVfs(vfs));
    }

    protected static URI resolve(final URI base, final String child, final String enc) {
        final String path = base.toString();
        final String parentPath = path.endsWith(SEP) ? path.substring(0, path.length() - 1) : path;
        final String newpath = parentPath + enc + child;
        return uncheck(() -> new URI(newpath));
    }

}

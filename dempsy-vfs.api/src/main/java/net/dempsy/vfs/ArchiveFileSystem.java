package net.dempsy.vfs;

import static net.dempsy.util.Functional.chain;
import static net.dempsy.util.Functional.uncheck;

import java.io.File;
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
import java.util.Optional;
import java.util.Set;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.UriUtils;
import net.dempsy.vfs.internal.DempsyArchiveEntry;
import net.dempsy.vfs.internal.DempsyArchiveInputStream;

public abstract class ArchiveFileSystem extends RecursiveFileSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveFileSystem.class);

    public class ArchiveEntryPath extends Path {
        private URI archiveUri;
        private URI entryUri;
        private boolean isDirectory;
        private String pathInsideArchive;
        private boolean isRoot;
        private final List<ArchiveEntryPath> children = new ArrayList<>();
        private long lastModifiedTime;
        private String scheme;
        private long length;

        public ArchiveEntryPath(final URI uri, final URI entryUri, final String pathInsideArchive, final boolean isDirectory, final long lastModifiedTime,
            final long length) {
            this.archiveUri = uri;
            this.entryUri = entryUri;
            this.isDirectory = isDirectory;
            this.pathInsideArchive = isDirectory ? removeTrailingSlash(pathInsideArchive) : pathInsideArchive;
            this.isRoot = UriUtils.isRoot(pathInsideArchive);
            this.lastModifiedTime = lastModifiedTime;
            this.scheme = entryUri.getScheme();
            this.length = length;
        }

        public ArchiveEntryPath(final URI uri, final URI entryUri, final DempsyArchiveEntry ae) {
            this(uri, entryUri, ae.getName(), ae.isDirectory(), Optional.ofNullable(ae.getLastModifiedDate()).map(d -> d.getTime()).orElse(0L), ae.getSize());
        }

        private void updateDetails(final ArchiveEntryPath aep) {
            archiveUri = aep.archiveUri;
            entryUri = aep.entryUri;
            isDirectory = aep.isDirectory;
            pathInsideArchive = aep.pathInsideArchive;
            isRoot = aep.isRoot;
            lastModifiedTime = aep.lastModifiedTime;
            scheme = aep.scheme;
            length = aep.length;
        }

        @Override
        public OutputStream write() throws IOException {
            throw new UnsupportedOperationException("archive file entries are not writable.");
        }

        @Override
        public URI uri() {
            return entryUri;
        }

        @Override
        public InputStream read() throws IOException {

            final DempsyArchiveInputStream is = createArchiveInputStream(scheme, archiveUri, false, ctx);
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
        public Path[] list(final OpContext ctx) throws IOException {
            return isDirectory ? children.toArray(Path[]::new) : null;
        }

        @Override
        public boolean exists() throws IOException {
            return true;
        }

        @Override
        public boolean delete() throws IOException {
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

        @Override
        public File toFile() throws IOException {
            return null;
        }
    }

    protected abstract DempsyArchiveInputStream createArchiveInputStream(String scheme, URI archiveUri, boolean listingOnly, OpContext ctx)
        throws IOException;

    protected abstract URI makeUriForArchiveEntry(final String scheme, final URI uri, final String DECODEDpathInsideTarFile) throws IOException;

    public void tryPasswords(final String... password) {
        // by default this does nothing. If the archive supports password protection it needs to be managed there.
    }

    @Override
    protected Path doCreatePath(final URI uri, final OpContext ctx) throws IOException {

        final SplitUri split = uncheck(() -> {
            return splitUri(uri, null);
        });
        final String searchPath = removeTrailingSlash(UriUtils.decodePath(split.remainder));
        // the child (ie the split.remainder) should be decoded for makeUriForArchiveEntry
        final URI topUri = makeUriForArchiveEntry(uri.getScheme(), split.baseUri, searchPath);
        final boolean isRoot = UriUtils.isRoot(searchPath);

        return setVfs(new Path() {

            private ArchiveEntryPath root = null;
            private boolean set = false;

            private synchronized void setup() throws IOException {
                if(!set) {
                    final LinkedHashMap<String, ArchiveEntryPath> cache = new LinkedHashMap<>();
                    final var sroot = buildTree(uri.getScheme(), split.baseUri, cache, ctx, this);

                    root = isRoot ? sroot : cache.get(searchPath);
                    set = true;
                }
            }

            @Override
            public OutputStream write() throws IOException {
                setup();
                if(root == null)
                    throw new FileNotFoundException(uri + " doesn't exist.");
                return root.write();
            }

            @Override
            public URI uri() {
                uncheck(() -> setup());
                if(root == null)
                    return topUri;
                return root.uri();
            }

            @Override
            public InputStream read() throws IOException {
                setup();
                if(root == null)
                    throw new FileNotFoundException(uri + " doesn't exist.");
                return root.read();
            }

            @Override
            public Path[] list(final OpContext ctx) throws IOException {
                setup();
                if(root == null)
                    throw new FileNotFoundException(uri + " doesn't exist.");
                return root.list(ctx);
            }

            @Override
            public boolean exists() throws IOException {
                setup();
                if(root == null)
                    return false;
                return root.exists();
            }

            @Override
            public boolean delete() throws IOException {
                setup();
                if(root == null)
                    throw new FileNotFoundException(uri + " doesn't exist.");
                return root.delete();
            }

            @Override
            public long lastModifiedTime() throws IOException {
                setup();
                if(root == null)
                    throw new FileNotFoundException(uri + " doesn't exist.");
                return root.lastModifiedTime();
            }

            @Override
            public long length() throws IOException {
                setup();
                if(root == null)
                    throw new FileNotFoundException(uri + " doesn't exist.");
                return root.length();
            }
        }, ctx);
    }

    protected static String removeTrailingSlash(final String str) {
        return str.endsWith("/") ? str.substring(0, str.length() - 1) : str;
    }

    private ArchiveEntryPath makePathForArchiveEntry(final String thsScheme, final URI uriOfArchive, final DempsyArchiveEntry ae, final OpContext ctx)
        throws IOException {
        final URI entryUri = makeUriForArchiveEntry(thsScheme, uriOfArchive, ae.getName());
        return chain(new ArchiveEntryPath(uriOfArchive, entryUri, ae), p -> p.setVfs(vfs, ctx));
    }

    // For archives that have multiple roots, For example, it's possible to manually craft
    // an archive with the following entries:
    //
    // /ArchiveFile1.ext
    // ArchiveFile2.ext
    // ./ArchiveFile3.ext
    //
    private ArchiveEntryPath reformulateRoot(final ArchiveEntryPath r1, final ArchiveEntryPath r2, final Set<String> pathsHooked, final OpContext ctx)
        throws IOException {
        LOGGER.warn("Multiple roots to the tree? {}", r1.archiveUri);
        ArchiveEntryPath ret;
        ArchiveEntryPath other;
        if(r1.pathInsideArchive.length() == 0) {
            ret = r1;
            other = r2;
        } else if(r2.pathInsideArchive.length() == 0) {
            ret = r2;
            other = r1;
        } else {
            ret = makeArchiveEntryPath(r1.scheme, r1.archiveUri, "", ctx);
            pathsHooked.add(ret.pathInsideArchive);
            ret.children.add(r1);
            other = r2;
        }

        pathsHooked.add(r1.pathInsideArchive);
        pathsHooked.add(r2.pathInsideArchive);
        ret.children.add(other);
        return ret;
    }

    private ArchiveEntryPath buildTree(final String thsScheme, final URI archiveUri, final LinkedHashMap<String, ArchiveEntryPath> cache, final OpContext ctx,
        final Path path)
        throws IOException {
        try(final DempsyArchiveInputStream is = createArchiveInputStream(thsScheme, archiveUri, true, ctx);) {
            for(DempsyArchiveEntry cur = is.getNextEntry(); cur != null; cur = is.getNextEntry()) {

                final String curPath = removeTrailingSlash(cur.getName());

                if(cache.containsKey(curPath)) {
                    LOGGER.warn("SKIPPING ENTRY! There are two files with the same name (\"{}\") inside of the archive at {}", curPath, archiveUri);
                } else {
                    final ArchiveEntryPath curAePath = makePathForArchiveEntry(thsScheme, archiveUri, cur, ctx);
                    cache.put(curPath, curAePath);
                }
            }
        }

        final Set<String> pathsHooked = new HashSet<>();
        // now we need to structure the tree
        ArchiveEntryPath tree = null;
        final Set<Map.Entry<String, ArchiveEntryPath>> entries = new LinkedHashSet<>(cache.entrySet());
        for(final var entry: entries) {
            ArchiveEntryPath prev = entry.getValue();

            // it's possible the archive contains the root as an entry
            if(prev.isRoot) {
                if(tree != null) {
                    tree = reformulateRoot(tree, prev, pathsHooked, ctx);
                } else {
                    tree = prev;
                    pathsHooked.add(prev.pathInsideArchive);
                }

                // in that case we don't want to traverse up the hierarchy
                // since we're at the top.
                continue;
            }

            if(pathsHooked.contains(prev.pathInsideArchive)) {
                // This is an indication that the path was added while traversing parents but is also
                // included in the list of ArchiveEntryPaths.
                final ArchiveEntryPath cur = cache.get(prev.pathInsideArchive);
                if(cur == null)
                    throw new IllegalStateException();
                cur.updateDetails(prev);
            } else {
                for(boolean done = false; !done;) {
                    final String parentPath = Optional.ofNullable(UriUtils.getParent(prev.pathInsideArchive)).orElse("");
                    final ArchiveEntryPath parent;
                    final boolean isRoot = UriUtils.isRoot(parentPath);
                    if(!cache.containsKey(parentPath)) {
                        parent = makeArchiveEntryPath(thsScheme, archiveUri, parentPath, ctx);
                        if(isRoot) {
                            if(tree != null) {
                                tree = reformulateRoot(tree, prev, pathsHooked, ctx);
                            } else {
                                tree = parent;
                                pathsHooked.add(parent.pathInsideArchive);
                            }
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
        }
        if(tree == null)
            throw new IOException("No root for tree? Possibly a bad or miscoded file." + archiveUri);
        return tree;
    }

    private ArchiveEntryPath makeArchiveEntryPath(final String scheme, final URI uriOfArchive, final String pathInsideArchive, final OpContext ctx)
        throws IOException {
        return setVfs(
            new ArchiveEntryPath(uriOfArchive, makeUriForArchiveEntry(scheme, uriOfArchive, pathInsideArchive), pathInsideArchive, true,
                System.currentTimeMillis(), 0),
            ctx);
    }
}

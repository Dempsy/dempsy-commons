package net.dempsy.vfs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.provider.UriParser;

import net.dempsy.util.UriUtils;

public abstract class ArchiveFileSystem extends FileSystem {

    public ArchiveEntryPath makeArchiveEntryPath(final URI uriOfArchive, final String pathInsideArchive) throws IOException {

        final ArchiveEntry ae = new ArchiveEntry() {

            @Override
            public boolean isDirectory() {
                return true;
            }

            @Override
            public long getSize() {
                return SIZE_UNKNOWN;
            }

            @Override
            public String getName() {
                return pathInsideArchive;
            }

            @Override
            public Date getLastModifiedDate() {
                throw new UnsupportedOperationException();
            }
        };

        return new ArchiveEntryPath(uriOfArchive, makeUriForArchiveEntry(uriOfArchive, ae), ae);
    }

    public class ArchiveEntryPath extends Path {
        private final URI uri;
        private final URI entryUri;
        private ArchiveEntry ae;
        private final List<ArchiveEntryPath> children = new ArrayList<>();

        public ArchiveEntryPath(final URI uri, final URI entryUri, final ArchiveEntry ae) {
            this.uri = uri;
            this.entryUri = entryUri;
            this.ae = ae;
        }

        @Override
        public OutputStream write() throws IOException {
            throw new UnsupportedOperationException("tar file entries are not writable.");
        }

        @Override
        public URI uri() {
            return entryUri;
        }

        void setArchiveEntry(final ArchiveEntry ae) {
            this.ae = ae;
        }

        @Override
        public InputStream read() throws IOException {

            final ArchiveInputStream is = createArchiveInputStream(uri);
            try {
                for(ArchiveEntry cur = is.getNextEntry(); cur != null; cur = is.getNextEntry()) {
                    if(ae.equals(cur))
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
            return ae.isDirectory();
        }

        @Override
        public Path[] list() throws IOException {
            return ae.isDirectory() ? children.toArray(Path[]::new) : null;
        }

        @Override
        public boolean exists() throws IOException {
            return true;
        }

        @Override
        public void delete() throws IOException {
            throw new UnsupportedOperationException("tar file entries are not deletable.");
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

    protected abstract ArchiveUriData split(URI uri) throws IOException;

    protected abstract ArchiveInputStream createArchiveInputStream(URI uriToArchive) throws IOException;

    protected abstract URI makeUriForArchiveEntry(URI archiveFileUri, ArchiveEntry ae) throws IOException;

    // this uri must have an "inner" uri. For example, if this is a tar
    // archive then the uri will look like:
    //
    // tar:file:///path/to/file.tar
    //
    // So the "inner" uri is file:///path/to/file.tar.
    protected static URI innerUri(final URI uri) throws IOException {
        final String innerUriString = uri.getSchemeSpecificPart();
        final URI innerUri;
        // This should be a valid URI with a scheme.
        try {
            innerUri = UriUtils.sanitize(innerUriString);
        } catch(final URISyntaxException use) {
            throw new IOException(
                "Archive uri's require a valid \"inner\" URI. The inner URI for \"" + uri + "\" is \"" + innerUriString + "\" which is not a valid URI.");
        }
        return innerUri;
    }

    protected static String normalisePath(final String path) throws FileSystemException {
        final StringBuilder sb = new StringBuilder(path);
        UriParser.normalisePath(sb);
        return sb.toString();
    }

    @Override
    protected Path doCreatePath(final URI uriX) throws IOException {

        return new Path() {

            private ArchiveEntryPath tree = null;
            private ArchiveEntryPath entry = null;
            private final URI uriToArchive;
            private final String pathToEntry;

            {
                final var ad = split(uriX);
                uriToArchive = ad.archiveUri;
                pathToEntry = ad.pathInsideArchive;
            }

            @Override
            public OutputStream write() throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public URI uri() {
                return uriX;
            }

            @Override
            public InputStream read() throws IOException {
                return entry.read();
            }

            @Override
            public Path[] list() throws IOException {

                if(tree == null) {
                    final Map<String, ArchiveEntryPath> cache = new HashMap<>();
                    tree = buildTree(uriToArchive, cache);
                    entry = cache.get(pathToEntry);
                    if(entry == null)
                        throw new IllegalStateException(
                            "Cannot find file \"" + pathToEntry + "\" inside archive \"" + uriToArchive + "\" referenced by uri \"" + uriX + "\"");
                }
                return entry.list();
            }

            @Override
            public boolean exists() throws IOException {
                // This is a uri to an archive which exists if the inner uri exists.
                // for example: tar:file:///path/to/file.tar exists if file:///path/to/file.tar exists
                final Path inner = vfs.toPath(innerUri(uriX));
                return inner.exists();
            }

            @Override
            public void delete() throws IOException {
                // This is a uri to an archive which can be deleted by deleting the inner uri
                // for example: tar:file:///path/to/file.tar is delete by deleting file:///path/to/file.tar
                final Path inner = vfs.toPath(innerUri(uriX));
                inner.delete();
            }
        };
    }

    protected ArchiveEntryPath makePathForArchiveEntry(final URI uriOfArchive, final ArchiveEntry ae) throws IOException {
        final URI entryUri = makeUriForArchiveEntry(uriOfArchive, ae);
        return new ArchiveEntryPath(uriOfArchive, entryUri, ae);
    }

    private ArchiveEntryPath buildTree(final URI archiveUri, final Map<String, ArchiveEntryPath> cache) throws IOException {
        final ArchiveEntryPath tree = makeArchiveEntryPath(archiveUri, "/");
        try(final ArchiveInputStream is = createArchiveInputStream(archiveUri);) {
            for(ArchiveEntry cur = is.getNextEntry(); cur != null; cur = is.getNextEntry()) {

                final String curPath = normalisePath(cur.getName());

                if(cache.containsKey(curPath))
                    throw new IllegalStateException(
                        "There appears to be two files with the same name (\"" + curPath + "\") inside of the archive at " + archiveUri);

                final ArchiveEntryPath curAePath = makePathForArchiveEntry(archiveUri, cur);
                cache.put(curPath, curAePath);

                ArchiveEntryPath prev = curAePath;
                String prevPath = curPath;
                for(boolean done = false; !done;) {
                    final String parentPath = normalisePath(Optional.ofNullable(UriUtils.getParent(prevPath)).orElse("/"));
                    final ArchiveEntryPath parent;
                    if(!cache.containsKey(parentPath)) {
                        if(UriUtils.isRoot(parentPath)) {
                            parent = tree;
                            done = true;
                        } else {
                            parent = makeArchiveEntryPath(archiveUri, parentPath);
                        }
                        cache.put(parentPath, parent);
                    } else {
                        parent = cache.get(parentPath);
                        // if the parent is in the tree then the parents of the parent are also
                        done = true;
                    }

                    parent.children.add(prev);
                    prevPath = normalisePath(prev.ae.getName());
                    prev = parent;
                }
            }
        }
        return tree;
    }
}

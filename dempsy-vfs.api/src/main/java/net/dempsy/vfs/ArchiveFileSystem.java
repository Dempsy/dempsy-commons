package net.dempsy.vfs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ArchiveFileSystem extends FileSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveFileSystem.class);

    private final Function<URI, ArchiveInputStream> iStreamCreator;

    public ArchiveFileSystem(final Function<URI, ArchiveInputStream> isCreator) {
        this.iStreamCreator = isCreator;
    }

    protected abstract Path makePath(final ArchiveEntry ae);

    @Override
    protected Path doCreatePath(final URI uri) throws IOException {
        return new Path() {

            private List<ArchiveEntry> entries = null;

            @Override
            public OutputStream write() throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public URI uri() {
                return uri;
            }

            @Override
            public InputStream read() throws IOException {
                return iStreamCreator.apply(uri);
            }

            @Override
            public Path[] list() throws IOException {
                if(entries == null) {
                    entries = new ArrayList<>();
                    try(ArchiveInputStream is = iStreamCreator.apply(uri);) {
                        for(ArchiveEntry cur = is.getNextEntry(); cur != null; cur = is.getNextEntry())
                            entries.add(cur);
                    }
                }
                return entries.stream()
                    .map(e -> makePath(e))
                    .toArray(Path[]::new);
            }

            @Override
            public boolean exists() throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public void delete() throws IOException {
                throw new UnsupportedOperationException();
            }
        };
    }

}

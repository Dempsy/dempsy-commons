package net.dempsy.vfs;

import static net.dempsy.util.Functional.uncheck;

import java.io.IOException;
import java.net.URI;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import net.dempsy.util.UriUtils;

public class TarFileSystem extends ArchiveFileSystem {

    private final static String[] SCHEMES = {"tar"};

    private final static String ENC = "!";

    @Override
    public ArchiveInputStream createArchiveInputStream(final URI uriToTarFile) throws IOException {
        final URI iUri = innerUri(uriToTarFile);
        return new TarArchiveInputStream(getVfs().toPath(iUri).read());
    }

    @Override
    protected URI makeUriForArchiveEntry(final URI uri, final ArchiveEntry ae) throws IOException {
        final URI innerUri = innerUri(uri);
        return uncheck(() -> new URI("tar:" + UriUtils.resolve(innerUri, normalisePath(ae.getName()), ENC)));
    }

    @Override
    public String[] supportedSchemes() {
        return SCHEMES;
    }

    @Override
    public void close() throws IOException {}

    @Override
    protected ArchiveUriData split(final URI uri) throws IOException {
        final URI iUri = innerUri(uri);
        final String iUriPath = iUri.getPath();
        if(iUriPath.contains(ENC)) {
            final int index = iUri.toString().indexOf(ENC);

            return new ArchiveUriData(
                uncheck(() -> new URI(uri.getScheme() + ":" + iUri.toString().substring(0, index))),
                iUri.toString().substring(index + ENC.length()));
        } else
            return new ArchiveUriData(uri, "/");
    }

}

package net.dempsy.vfs;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This abstract class is the parent for the various means of converting from a scheme
 * to an instance of a {@link Path}, which is the means by which the file system
 * is accessed.
 */
public abstract class FileSystem {
    private final static Logger LOGGER = LoggerFactory.getLogger(FileSystem.class);
    protected Vfs vfs = null;

    private final String encToIgnore = getBogusEnc();

    /**
     * This is called automatically by the {@link Vfs} instance as part of registering
     * the file system.
     */
    void setVfs(final Vfs vfs) {
        this.vfs = vfs;
    }

    /**
     * The main public interface to convert a URI of a file or directory to a {@link Path}.
     */
    public final Path createPath(final URI uri, final OpContext oc) throws IOException {
        final Path path = doCreatePath(uri, oc);
        if(path != null)
            path.setVfs(vfs, oc);
        return path;
    }

    protected abstract Path doCreatePath(URI uri, OpContext oc) throws IOException;

    protected Vfs getVfs() {
        return vfs;
    }

    public File toFile(final URI uri) throws UnsupportedOperationException {
        try {
            return Paths.get(uri).toFile();
        } catch(final RuntimeException rte) {
            if(LOGGER.isTraceEnabled())
                LOGGER.trace("Couldn't convert uri \"{}\" to a file:", uri, rte);
            else
                LOGGER.debug("Couldn't convert uri \"{}\" to a file. Got error: {}", uri, rte.getLocalizedMessage());
        }
        return null;
    }

    public abstract String[] supportedSchemes();

    public static class SplitUri {
        public final URI baseUri;
        public final String enc;
        public final String remainder;

        public SplitUri(final URI baseUri, final String enc, final String remainder) {
            this.baseUri = baseUri;
            this.enc = enc;
            this.remainder = remainder;
        }
    }

    /**
     * Currently this is very hacky. The enc passed can be one of 3 things. It can
     * be an enc (for example, '!' separating an archive URI from it's entry for a tar file).
     * It can be null which means we're at the top of the recursion. It can be a flag
     * meant to be ignored. This can be tested by calling {@link FileSystem#ignoreEnc(String)}
     */
    protected SplitUri splitUri(final URI uri, final String outerEnc) throws URISyntaxException {
        if(outerEnc == null || ignoreEnc(outerEnc))
            return new SplitUri(uri, outerEnc, "");
        final int encIndex = uri.toString().indexOf(outerEnc);
        if(encIndex < 0)
            return new SplitUri(uri, outerEnc, "");
        return new SplitUri(new URI(uri.toString().substring(0, encIndex)), outerEnc, uri.toString().substring(encIndex + 1));
    }

    protected boolean ignoreEnc(final String enc) {
        return enc == encToIgnore;
    }

    protected String ignoreEnc() {
        return encToIgnore;
    }

    /**
     * Helper for allowing implementors to set the vfs on newly create Paths
     */
    protected <T extends Path> T setVfs(final T p, final OpContext ctx) {
        p.setVfs(vfs, ctx);
        return p;
    }

    private String getBogusEnc() {
        return "!!!!BOGUS!!!!";
    }

}

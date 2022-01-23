package net.dempsy.vfs;

import static net.dempsy.util.Functional.uncheck;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import net.dempsy.util.UriUtils;

public abstract class EncArchiveFileSystem extends ArchiveFileSystem {
    public final String enc;
    public final String scheme;

    protected EncArchiveFileSystem(final String scheme, final String enc) {
        this.enc = enc;
        this.scheme = scheme;
    }

    @Override
    protected URI makeUriForArchiveEntry(final URI uri, final String pathInsideTarFile) throws IOException {
        return uncheck(() -> new URI(scheme + ":" + resolve(uri, pathInsideTarFile /* normalizePath(pathInsideTarFile) */, enc)));
    }

    @Override
    public SplitUri splitUri(final String uri, final String outerEnc) throws URISyntaxException {
        // first we take the scheme off.
        final URI hackedUri = new URI(uri);
        // curScheme will be assumed to be supported by this FileSystem
        final String otherThanScheme = hackedUri.getSchemeSpecificPart();
        final URI otherThanSchemeUri = UriUtils.sanitize(otherThanScheme);

        final FileSystem innerFileSystem = vfs.fileSystemFromScheme(otherThanSchemeUri.getScheme());
        final SplitUri innerSplitUri = innerFileSystem.splitUri(otherThanSchemeUri.toString(), enc);

        if(outerEnc == null) // then there is no outer and this is the top of the recursion
            return innerSplitUri;

        // we're not the outermost. Therefore we're an archive within an archive. We need to adjust
        // our content so that the base url contains the remainder - the newremainder

        // using the outer enc I need to truncate the innerInner's remainder
        final int encIndex = innerSplitUri.remainder.indexOf(outerEnc);
        if(encIndex < 0)
            return new SplitUri(new URI(scheme + ":" + innerSplitUri.baseUri.toString() + enc + innerSplitUri.remainder), "");

        final String newUriStr = scheme + ":" + innerSplitUri.baseUri.toString() + enc + innerSplitUri.remainder.substring(0, encIndex);
        final String newRemainder = innerSplitUri.remainder.substring(encIndex + 1);

        return new SplitUri(new URI(newUriStr), newRemainder);
    }

}

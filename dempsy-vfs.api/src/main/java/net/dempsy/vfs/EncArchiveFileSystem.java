package net.dempsy.vfs;

import static net.dempsy.util.Functional.uncheck;
import static net.dempsy.util.UriUtils.SEP;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import net.dempsy.util.UriUtils;

public abstract class EncArchiveFileSystem extends ArchiveFileSystem {
    public final String enc;

    protected EncArchiveFileSystem(final String enc) {
        this.enc = enc;
    }

    @Override
    protected URI makeUriForArchiveEntry(final String scheme, final URI uri, final String DECODEDpathInsideTarFile) throws IOException {
        return uncheck(() -> new URI(scheme + ":" + resolve(uri, DECODEDpathInsideTarFile, enc)));
    }

    @Override
    public SplitUri splitUri(final URI uri, final String outerEnc) throws URISyntaxException {
        // first we take the scheme off.
        final URI hackedUri = uri;
        final String scheme = hackedUri.getScheme();
        // curScheme will be assumed to be supported by this FileSystem
        final String otherThanScheme = hackedUri.getSchemeSpecificPart();
        final URI otherThanSchemeUri = UriUtils.sanitize(otherThanScheme);

        final FileSystem innerFileSystem = vfs.fileSystemFromScheme(otherThanSchemeUri.getScheme());
        final SplitUri innerSplitUri = innerFileSystem.splitUri(otherThanSchemeUri, enc);

        if(outerEnc == null) // then there is no outer and this is the top of the recursion
            return innerSplitUri;

        // we're not the outermost. Therefore we're an archive within an archive. We need to adjust
        // our content so that the base url contains the remainder - the newremainder
        if(ignoreEnc(outerEnc))
            return new SplitUri(new URI(scheme + ":" + innerSplitUri.baseUri.toString() + innerSplitUri.enc + innerSplitUri.remainder), outerEnc, "");

        // using the outer enc I need to truncate the innerInner's remainder
        final int encIndex = innerSplitUri.remainder.indexOf(outerEnc);
        if(encIndex < 0)
            return new SplitUri(new URI(scheme + ":" + innerSplitUri.baseUri.toString() + innerSplitUri.enc + innerSplitUri.remainder), outerEnc, "");

        final String newUriStr = scheme + ":" + innerSplitUri.baseUri.toString() + innerSplitUri.enc + innerSplitUri.remainder.substring(0, encIndex);
        final String newRemainder = innerSplitUri.remainder.substring(encIndex + outerEnc.length());

        return new SplitUri(new URI(newUriStr), outerEnc, newRemainder);
    }

    private static URI resolve(final URI base, final String DECODEDChild, final String enc) {
        final String path = base.toString();
        final String parentPath = path.endsWith(SEP) ? path.substring(0, path.length() - 1) : path;

        final String newpath = parentPath + enc + UriUtils.encodePath(DECODEDChild);
        return uncheck(() -> new URI(newpath));
    }
}

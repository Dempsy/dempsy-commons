package net.dempsy.vfs2;

import java.net.URI;
import java.net.URISyntaxException;

import net.dempsy.util.UriUtils;

public abstract class RecursiveFileSystem extends FileSystem {

    @Override
    public SplitUri splitUri(final URI uri, final String outerEnc) throws URISyntaxException {
        // first we take the scheme off.
        final URI hackedUri = uri;
        final String scheme = hackedUri.getScheme();
        // curScheme will be assumed to be supported by this FileSystem
        final String otherThanScheme = hackedUri.getSchemeSpecificPart();
        final URI otherThanSchemeUri = UriUtils.sanitize(otherThanScheme);

        final FileSystem innerFileSystem = vfs.fileSystemFromScheme(otherThanSchemeUri.getScheme());

        if(outerEnc == null) // then there is no outer and this is the top of the recursion
            return innerFileSystem.splitUri(otherThanSchemeUri, ignoreEnc());

        final SplitUri innerSplitUri = innerFileSystem.splitUri(otherThanSchemeUri, outerEnc);

        return new SplitUri(new URI(scheme + ":" + innerSplitUri.baseUri.toString()), innerSplitUri.enc, innerSplitUri.remainder);
    }
}

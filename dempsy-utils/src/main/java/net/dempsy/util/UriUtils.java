package net.dempsy.util;

import static net.dempsy.util.Functional.uncheck;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UriUtils {

    public static final String SEP = "/";
    public static final char SEP_CHAR = '/';

    /**
     * Parse the query string into a multi-map. Edge cases:
     *
     * <ul>
     * <li>1. The same key appears twice. This will result in a multimap that has
     * multiple entries for the same key.</li>
     * <li>2. The query string has an entry with no equals. This will result in an
     * entry in the multimap for the value as the key but with a null in the list.
     * For example: {@code ...?tcp&loop=true}</li>
     * <li>3. The query string has an entry that has an equal sign but no value. This
     * will result in an entry in the multimap where the value is assumed to be an
     * empty string. For example: {@code ...?loop&something=somethingelse}</li>
     * <li>4. The query string has an entry that starts with an equals sign. This
     * will result in a value added to a key where the key is an empty string.
     * For example: {@code ...?=true}</li>
     * </ul>
     */
    public static Map<String, List<String>> query(final URI toUse) {
        final Map<String, List<String>> queryPairs = new LinkedHashMap<>();

        final String query = toUse.getQuery();
        if(query != null) {
            final String[] pairs = query.split("&");
            for(final String pair: pairs) {
                if(pair.length() != 0) {
                    final int idx = pair.indexOf("=");
                    if(idx < 0) // there's no equal so it's all key
                        add(queryPairs, uncheck(() -> URLDecoder.decode(pair, "UTF-8")), null);
                    else if(idx == 0) // equals is first?
                        add(queryPairs, "", pair.substring(1));
                    else
                        add(queryPairs, uncheck(() -> URLDecoder.decode(pair.substring(0, idx), "UTF-8")),
                            uncheck(() -> URLDecoder.decode(pair.substring(idx + 1), "UTF-8")));
                }
            }
        }
        return queryPairs;
    }

    public static URI removeQuery(final URI uri) {
        return uncheck(() -> new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), null, uri.getFragment()));
    }

    public static String urlEncode(final String val) {
        return uncheck(() -> URLEncoder.encode(val, StandardCharsets.UTF_8.name()));
    }

    /**
     * Returns the name of the file or directory denoted by this URI.
     * This is just the last name in the pathname's name
     * sequence. If the pathname's name sequence is empty, then the empty
     * string is returned.
     *
     * This handles recursive URIs and returns the path of the inner most
     * URI.
     *
     * @return The name of the file or directory denoted by this abstract
     * pathname, or the empty string if this pathname's name sequence
     * is empty
     */
    public static String getName(final URI uri) {
        return getName(extractPath(uri.toString()));
    }

    /**
     * Returns the name of the file or directory denoted by this URI.
     * This is just the last name in the pathname's name
     * sequence. If the pathname's name sequence is empty, then the empty
     * string is returned.
     *
     * @return The name of the file or directory denoted by this abstract
     * pathname, or the empty string if this pathname's name sequence
     * is empty
     */
    public static String getName(final String pathPartOnly) {
        if(pathPartOnly.endsWith(SEP))
            return getName(pathPartOnly.substring(0, pathPartOnly.length() - 1));
        final int prefixLength = pathPartOnly.startsWith(SEP) ? 1 : 0;
        final int index = pathPartOnly.lastIndexOf(SEP_CHAR);
        if(index < prefixLength) return pathPartOnly.substring(prefixLength);
        return pathPartOnly.substring(index + 1);
    }

    /**
     * Extract the path from a potentially nexted, and potentially not encoded (with
     * control characters) string representation of a uri.
     */
    public static String extractPath(final String uri) {
        return extractPath(uri, null);
    }

    public static String extractPath(final String uri, final int[] beginningAndEnd) {
        // we need to extract the ultimate path, if there is one.
        // That means handling recursive urls and not loosing queries or frags
        // or auths
        final int len = uri.length();
        if(len == 0) {
            if(beginningAndEnd != null) {
                beginningAndEnd[0] = 0;
                beginningAndEnd[1] = 0;
            }
            return uri;
        }

        final int pathBegin;
        final int pathEnd;

        // the goal here is to set pathBegin and pathEnd
        {
            final int indexSlash = uri.indexOf('/');
            final int queryBegin = uri.indexOf('?');
            final int indexHash = uri.indexOf('#');
            final int dontConsiderPast;
            // set dontConsiderPast
            {
                if(queryBegin < 0 && indexHash < 0)
                    dontConsiderPast = len;
                else if(queryBegin < 0 && indexHash >= 0)
                    dontConsiderPast = indexHash;
                else if(queryBegin >= 0 && indexHash < 0)
                    dontConsiderPast = queryBegin;
                else
                    dontConsiderPast = Math.min(queryBegin, indexHash);
            }

            if(indexSlash < 0) { // there's no slash
                final int indexCol = uri.substring(0, dontConsiderPast).lastIndexOf(':');
                pathEnd = dontConsiderPast;
                if(indexCol < 0)
                    pathBegin = 0;
                else
                    pathBegin = indexCol + 1;
            } else {
                // the last ':' before the slash
                final int lastColBeforeFirstSlash = uri.substring(0, indexSlash).lastIndexOf(':');

                // if the first slash is a double slash, and it immediately follows the colon, then there's an authority and we need to scan past it to the
                // path. This works even if lastColBeforeFirstSlash is -1
                final boolean isThereAnAuthority = (indexSlash == lastColBeforeFirstSlash + 1)
                    ? (indexSlash >= (dontConsiderPast - 1) ? false : ('/' == uri.charAt(indexSlash + 1)))
                    : false;

                if(isThereAnAuthority) {
                    if(indexSlash + 2 >= dontConsiderPast)
                        pathBegin = pathEnd = dontConsiderPast;
                    else {
                        final int authBegin = indexSlash + 2;
                        final int indexNextSlash = uri.indexOf('/', authBegin);
                        if(indexNextSlash < 0)
                            pathBegin = pathEnd = dontConsiderPast;
                        else {
                            pathBegin = indexNextSlash;
                            pathEnd = dontConsiderPast;
                        }
                    }
                } else {
                    pathBegin = lastColBeforeFirstSlash + 1;
                    pathEnd = dontConsiderPast;
                }
            }
        }

        if(beginningAndEnd != null) {
            beginningAndEnd[0] = pathBegin;
            beginningAndEnd[1] = pathEnd;
        }
        return uri.substring(pathBegin, pathEnd);
    }

    /**
     * Returns the pathname string of this path's parent, or
     * <code>null</code> if this pathname does not name a parent directory.
     *
     * <p>
     * The <em>parent</em> of a path consists of the
     * pathname's prefix, if any, and each name in the pathname's name
     * sequence except for the last. If the name sequence is empty then
     * the pathname does not name a parent directory.
     *
     * @return The pathname string of the parent directory named by this
     * abstract pathname, or <code>null</code> if this pathname
     * does not name a parent
     */
    public static String getParent(final String pathPartOnly) {
        // is this a URI?
        final String normalizedPathPartOnly = removeTrailingSlash(pathPartOnly);
        final int prefixLength = normalizedPathPartOnly.startsWith("/") ? 1 : 0;
        final int index = normalizedPathPartOnly.lastIndexOf(SEP_CHAR);
        if(index < prefixLength) {
            if((prefixLength > 0) && (normalizedPathPartOnly.length() > prefixLength))
                return normalizedPathPartOnly.substring(0, prefixLength);
            return null;
        }
        return normalizedPathPartOnly.substring(0, index);
    }

    /**
     * Recursive URI resistant resolver. child should not already be encoded
     */
    public static URI resolve(final URI base, String child) {
        final String path;
        // we extract the path from the uri decoded without using URI.toString
        final int[] be = new int[2];
        final String otherThanScheme = base.getRawSchemeSpecificPart();
        path = extractPath("x:" + otherThanScheme, be);
        // shift everything back by 2; the size of "x:"
        be[0] -= 2;
        be[1] -= 2;
        child = encodePath(child);
        final String childStripped = child.startsWith(SEP) ? child.substring(1) : child;
        final String newpath = path.endsWith(SEP) ? (path + childStripped) : (path + SEP + childStripped);

        final String newUriStr = base.getScheme() + ':' + otherThanScheme.substring(0, be[0]) + newpath +
            (be[1] >= otherThanScheme.length() ? "" : otherThanScheme.substring(be[1]));
        return uncheck(() -> new URI(newUriStr));
        // try {
        // return new URI(base.getScheme(), base.getAuthority(), newpath, base.getQuery(), base.getFragment());
        // } catch(final URISyntaxException e) {
        // throw new RuntimeException(e);
        // }
    }

    public static URI getParent(final URI uri) {
        final String newpath = getParent(uri.getPath());
        try {
            return new URI(uri.getScheme(), uri.getAuthority(), newpath, uri.getQuery(), uri.getFragment());
        } catch(final URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static URI prependScheme(final String scheme, final URI uri) throws URISyntaxException {
        return new URI(scheme + ':' + uri.toASCIIString());
    }

    private static Set<String> allRoots = Set.of(".", "./", "/", "");

    /**
     * If the return value of {@link UriUtils#getParent(String)} is passed to this, it
     * will return true if the path represents the root of the file system tree.
     * In this case it will be true if the path is '/' or null.
     */
    public static boolean isRoot(final String path) {
        if(path == null)
            return true;
        return allRoots.contains(path);
    }

    /**
     * If the uri has no scheme, it assumes it's a file.
     */
    public static URI sanitize(final String uri) throws URISyntaxException {
        final int[] be = new int[2];
        final String exPath = extractPath(uri, be);

        final URI potentialReturn;
        String potentialReturnUriStr = uri;
        if(be[0] != be[1]) {
            final String newPath = encodePath(exPath);
            final StringBuilder sb = new StringBuilder(uri.substring(0, be[0]))
                .append(newPath)
                .append(uri.substring(be[1]));
            potentialReturnUriStr = sb.toString();
        }
        potentialReturn = new URI(potentialReturnUriStr);

        if(potentialReturn.getScheme() == null) { // here, we assume it's a file.
            return new File(uri).toURI();
        }

        return potentialReturn;
    }

    /**
     * Used mostly for debugging.
     */
    public static void dumpUri(final URI uri) {
        System.out.println("scheme:" + uri.getScheme());
        System.out.println("scheme specific part:" + uri.getRawSchemeSpecificPart());
        System.out.println("authority:" + uri.getRawAuthority());
        System.out.println("fragment:" + uri.getRawFragment());
        System.out.println("host:" + uri.getHost());
        System.out.println("path:" + uri.getRawPath());
        System.out.println("port" + uri.getPort());
        System.out.println("query:" + uri.getRawQuery());
    }

    /**
     * This will take a non-uri-encoded path part of a uri and encode it
     */
    public static String encodePath(final String path) {

        if(path == null)
            return null;
        if(path.length() == 0)
            return path;

        // we're going to make this look like an absolute file path.
        boolean addedSep = false;
        String tmp = path;

        if(SEP_CHAR != path.charAt(0)) {
            addedSep = true;
            tmp = SEP_CHAR + path;
        }
        final int numTralingSlashes = numTralingSlashes(path);

        final String ftmp = tmp;
        final URI tmpu = uncheck(() -> new URI("file", null, ftmp, null));
        tmp = tmpu.getRawSchemeSpecificPart();

        if(addedSep && tmp.startsWith(SEP))
            tmp = tmp.substring(1);

        final int newNumTralingSlashes = numTralingSlashes(tmp);
        if(newNumTralingSlashes != numTralingSlashes) {
            final var sb = new StringBuilder(tmp);

            for(int i = newNumTralingSlashes; i < numTralingSlashes; i++)
                sb.append(SEP_CHAR);
            tmp = sb.toString();
        }
        return tmp;
    }

    /**
     * This will take an encoded path part of a uri and decode it.
     *
     * <em>NOTE: it collapses repeated forward slashes into a single slash</em>
     */
    public static String decodePath(final String path) {

        if(path == null)
            return null;
        if(path.length() == 0)
            return path;

        String tmp = path;
        boolean addedSep = false;
        if(path.charAt(0) != SEP_CHAR) {
            addedSep = true;
            tmp = SEP_CHAR + path;
        }

        final boolean trailingSlash = path.charAt(path.length() - 1) == SEP_CHAR;

        final String ftmp = tmp;
        final URI furi = uncheck(() -> new URI("file:" + ftmp));

        String r = Paths.get(furi).toFile().getPath();
        if(File.separatorChar != SEP_CHAR)
            r = r.replace(File.separatorChar, SEP_CHAR);

        if(addedSep && r.charAt(0) == SEP_CHAR)
            r = r.substring(1);

        if(trailingSlash && r.charAt(r.length() - 1) != SEP_CHAR)
            r = r + SEP_CHAR;

        return r;
    }

    private static void add(final Map<String, List<String>> map, final String key, final String value) {
        map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
    }

    private static int numTralingSlashes(final String str) {
        int numTralingSlashes = 0;
        for(int idx = str.length() - 1; idx > 0; idx--) {
            if(SEP_CHAR != str.charAt(idx))
                break;
            numTralingSlashes++;
        }
        return numTralingSlashes;
    }

    private static String removeTrailingSlash(final String str) {
        return str.endsWith("/") ? str.substring(0, str.length() - 1) : str;
    }
}

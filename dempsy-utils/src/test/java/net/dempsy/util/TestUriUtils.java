/*
 * Copyright 2022 Jim Carroll
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dempsy.util;

import static net.dempsy.util.Functional.uncheck;
import static net.dempsy.util.UriUtils.decodePath;
import static net.dempsy.util.UriUtils.encodePath;
import static net.dempsy.util.UriUtils.extractPath;
import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class TestUriUtils {

    private static URI u(final String uri) {
        return uncheck(() -> new URI(uri));
    }

    @Test
    public void testExtractBadPath() throws Exception {
        assertEquals("", extractPath("tar:gz:file:hello://"));
        assertEquals("/path/to/Ichiban no Takaramono `Yui final ver.`",
            extractPath("tar:gz:file:///path/to/Ichiban no Takaramono `Yui final ver.`"));
        assertEquals("path/to/Ichiban no Takaramono `Yui final ver.`",
            extractPath("tar:gz:file:path/to/Ichiban no Takaramono `Yui final ver.`"));
        assertEquals("/path/to/Ichiban no Takaramono `Yui final ver.`",
            extractPath("tar:gz:file://auth[bank]:9/path/to/Ichiban no Takaramono `Yui final ver.`"));
    }

    @Test
    public void testExtractPath() throws Exception {
        testExtractPath("");
        testExtractPath("medapath");
        testExtractPath("/medapath");
        testExtractPath("//medapath");
        testExtractPath("///medapath");
        testExtractPath("////medapath");
        testExtractPath("a/medapath");
        testExtractPath("a//medapath");
        testExtractPath("a///medapath");
        testExtractPath("a////medapath");
        testExtractPath("a/////medapath");
        testExtractPath("tar:gz:file:hello:/");
        testExtractPath("tar:gz:file:hello://a");
        testExtractPath("tar:gz:file:hello:what:path//path2");
        testExtractPath("tar:gz:file://where@host:9/am/i!youarehere.txt!more?query=[hell]#frag");
        testExtractPath("tar:gz:file:wherehost:9ami!youarehere.txt!more?query=[hell]#frag");
        testExtractPath("tar:gz:file:x?");
        testExtractPath("tar:gz:file:/x");
        testExtractPath("tar:gz:file://x");
        testExtractPath("tar:gz:file:///x");
        testExtractPath("tar:gz:file:////x");
        testExtractPath("tar:gz:file:x?query=[hell]");
        testExtractPath("tar:gz:file:/x?query=[hell]");
        testExtractPath("tar:gz:file://x?query=[hell]");
        testExtractPath("tar:gz:file:///x?query=[hell]");
        testExtractPath("tar:gz:file:////x?query=[hell]");
        testExtractPath("tar:gz:file:x#frag");
        testExtractPath("tar:gz:file:/x#frag");
        testExtractPath("tar:gz:file://x#frag");
        testExtractPath("tar:gz:file:///x#frag");
        testExtractPath("tar:gz:file:////x#frag");
        testExtractPath("tar:gz:file:x?query=[hell]#frag");
        testExtractPath("tar:gz:file:/x?query=[hell]#frag");
        testExtractPath("tar:gz:file://x?query=[hell]#frag");
        testExtractPath("tar:gz:file:///x?query=[hell]#frag");
        testExtractPath("tar:gz:file:////x?query=[hell]#frag");
        testExtractPath("tar:gz:file:noslashhere:more?the:end=joe&fritz?who=tmp#?kog=fang");
    }

    @Test
    public void testEncodePath() throws Exception {
        assertEquals("other/dir%20With%20Brackets%5Bsquare%5D/", encodePath("other/dir With Brackets[square]/"));
        assertEquals("other/dir With Brackets[square]/", decodePath(encodePath("other/dir With Brackets[square]/")));

        assertEquals("", encodePath(""));
        assertEquals("", decodePath(encodePath("")));
        assertEquals("/", encodePath("/"));
        assertEquals("/", decodePath(encodePath("/")));
        assertEquals("a://x", encodePath("a://x"));
        // decode path collapses slashes
        assertEquals("a:/x", decodePath(encodePath("a://x")));
        assertEquals("a://", encodePath("a://"));
        assertEquals("a:/", decodePath(encodePath("a://")));
        // decode path collapses slashes
        assertEquals("a:/", encodePath("a:/"));
        assertEquals("a:///", encodePath("a:///"));
        // decode path collapses slashes
        assertEquals("a:/", decodePath(encodePath("a:///")));
        assertEquals("a%20a:/%20junk//://", encodePath("a a:/ junk//://"));
        // decode path collapses slashes
        assertEquals("a a:/ junk/:/", decodePath(encodePath("a a:/ junk//://")));
        assertEquals("/aNormal/absolute/path/to/some/file.txt", encodePath("/aNormal/absolute/path/to/some/file.txt"));
        assertEquals("/aNormal/absolute/path/to/some/file.txt", decodePath(encodePath("/aNormal/absolute/path/to/some/file.txt")));
        assertEquals("aNormal/relative/path/to/some/file.txt", encodePath("aNormal/relative/path/to/some/file.txt"));
        assertEquals("aNormal/relative/path/to/some/file.txt", decodePath(encodePath("aNormal/relative/path/to/some/file.txt")));
    }

    private void testExtractPath(final String uri) throws Exception {

        String uriRelStr = uri;
        String path = null;
        while(true) {
            final URI u = new URI(uriRelStr);

            path = u.getPath();
            if(path != null) {
                UriUtils.dumpUri(u);
                break;
            }
            uriRelStr = u.getRawSchemeSpecificPart();
        }

        // System.out.println("Extracted Path: \"" + extractPath(uri) + "\"");
        assertEquals(path, extractPath(uri));
    }

    @Test
    public void canParseNormalQueryString() throws Exception {
        final Map<String, List<String>> results = UriUtils.query(u("http://junk.whoareyou.com:5598432/to/path?hello=world"));
        assertEquals(Map.of("hello", List.of("world")), results);
    }

    @Test
    public void canParseNoSchemeQueryString() throws Exception {
        // lone
        var results = UriUtils.query(u("junk.whoareyou.com/to/path?hello=world"));
        assertEquals(Map.of("hello", List.of("world")), results);
        // multi
        results = UriUtils.query(u("junk.whoareyou.com/to/path?hello=world&joe=schmoe"));
        assertEquals(Map.of("hello", List.of("world"), "joe", List.of("schmoe")), results);
    }

    @Test
    public void canParseInvalidQueryString() throws Exception {
        // lone
        var results = UriUtils.query(u("junk.whoareyou.com/to/path?hello"));
        assertEquals(Map.of("hello", Arrays.asList((String)null)), results);
        // first
        results = UriUtils.query(u("junk.whoareyou.com/to/path?hello&joe=schmoe"));
        assertEquals(Map.of("hello", Arrays.asList((String)null), "joe", List.of("schmoe")), results);
        // middle
        results = UriUtils.query(u("junk.whoareyou.com/to/path?joey=schmoey&hello&joe=schmoe"));
        assertEquals(Map.of("joey", List.of("schmoey"), "hello", Arrays.asList((String)null), "joe", List.of("schmoe")), results);
        // last
        results = UriUtils.query(u("junk.whoareyou.com/to/path?joe=schmoe&hello"));
        assertEquals(Map.of("hello", Arrays.asList((String)null), "joe", List.of("schmoe")), results);
    }

    @Test
    public void canParseInvalidQueryString2() throws Exception {
        var results = UriUtils.query(u("junk.whoareyou.com/to/path?=world"));
        assertEquals(Map.of("", List.of("world")), results);
        results = UriUtils.query(u("junk.whoareyou.com/to/path?=world&joe=schmoe"));
        assertEquals(Map.of("", List.of("world"), "joe", List.of("schmoe")), results);
        results = UriUtils.query(u("junk.whoareyou.com/to/path?joey=schmoey&=world&joe=schmoe"));
        assertEquals(Map.of("joey", List.of("schmoey"), "", List.of("world"), "joe", List.of("schmoe")), results);
        results = UriUtils.query(u("junk.whoareyou.com/to/path?joey=schmoey&=world"));
        assertEquals(Map.of("joey", List.of("schmoey"), "", List.of("world")), results);
    }

    @Test
    public void canParseInvalidQueryString3() throws Exception {
        var results = UriUtils.query(u("junk.whoareyou.com/to/path?hello="));
        assertEquals(Map.of("hello", List.of("")), results);
        results = UriUtils.query(u("junk.whoareyou.com/to/path?hello=&joe=schmoe"));
        assertEquals(Map.of("hello", List.of(""), "joe", List.of("schmoe")), results);
        results = UriUtils.query(u("junk.whoareyou.com/to/path?joey=schmoey&hello=&joe=schmoe"));
        assertEquals(Map.of("joey", List.of("schmoey"), "hello", List.of(""), "joe", List.of("schmoe")), results);
        results = UriUtils.query(u("junk.whoareyou.com/to/path?joey=schmoey&hello="));
        assertEquals(Map.of("joey", List.of("schmoey"), "hello", List.of("")), results);
    }
}

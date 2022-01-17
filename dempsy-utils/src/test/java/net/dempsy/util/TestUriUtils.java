package net.dempsy.util;

import static net.dempsy.util.Functional.uncheck;
import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import net.dempsy.util.UriUtils;

public class TestUriUtils {

    private static URI u(final String uri) {
        return uncheck(() -> new URI(uri));
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

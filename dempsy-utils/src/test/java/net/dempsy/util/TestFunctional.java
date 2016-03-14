package net.dempsy.util;

import static net.dempsy.util.Functional.mapChecked;
import static net.dempsy.util.Functional.recheck;
import static net.dempsy.util.Functional.uncheck;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

public class TestFunctional {

    private static void instantiate(final String className) throws ClassNotFoundException {
        Class.forName(className);
    }

    @Test(expected = ClassNotFoundException.class)
    public void testForEachRechecked() throws Throwable {
        final List<String> classnames = Arrays.asList("java.lang.String", "junk.no.class.Exists");

        try {
            Functional.<ClassNotFoundException> recheck(() -> classnames.stream().forEach(cn -> uncheck(() -> instantiate(cn))));
        } catch (final ClassNotFoundException cnfe) {
            throw cnfe;
        }
    }

    @Test(expected = ClassNotFoundException.class)
    public void testForEachRecheckedAlt() throws Throwable {
        final List<String> classnames = Arrays.asList("java.lang.String", "junk.no.class.Exists");

        try {
            recheck(() -> classnames.stream().forEach(cn -> uncheck(() -> instantiate(cn))), ClassNotFoundException.class);
        } catch (final ClassNotFoundException cnfe) {
            throw cnfe;
        }
    }

    @Test(expected = ClassNotFoundException.class)
    public void testForEachRecheckedParallelStream() throws Throwable {
        final List<String> classnames = Arrays.asList("java.lang.String", "junk.no.class.Exists");

        try {
            Functional.<ClassNotFoundException> recheck(() -> classnames.parallelStream().forEach(cn -> uncheck(() -> instantiate(cn))));
        } catch (final ClassNotFoundException cnfe) {
            throw cnfe;
        }
    }

    @Test(expected = ClassNotFoundException.class)
    public void testMapRechecked() throws Throwable {
        final List<String> classnames = Arrays.asList("java.lang.String", "junk.no.class.Exists");
        @SuppressWarnings("unused")
        List<Class<?>> classes = null;
        try {
            classes = Functional
                    .<List<Class<?>>, ClassNotFoundException> recheck(
                            () -> classnames.stream().map(cn -> uncheck(() -> Class.forName(cn))).collect(Collectors.toList()));
        } catch (final ClassNotFoundException cnfe) {
            throw cnfe;
        }
    }

    @Test(expected = ClassNotFoundException.class)
    public void testMapRecheckedAlt() throws Throwable {
        final List<String> classnames = Arrays.asList("java.lang.String", "junk.no.class.Exists");
        @SuppressWarnings({ "unused", "rawtypes" })
        List classes = null;
        try {
            classes = recheck(() -> classnames.stream().map(cn -> uncheck(() -> Class.forName(cn))).collect(Collectors.toList()),
                    ClassNotFoundException.class);
        } catch (final ClassNotFoundException cnfe) {
            throw cnfe;
        }
    }

    @Test(expected = ClassNotFoundException.class)
    public void testMapRecheckedParallelStream() throws Throwable {
        final List<String> classnames = Arrays.asList("java.lang.String", "junk.no.class.Exists");
        @SuppressWarnings("unused")
        List<Class<?>> classes = null;
        try {
            classes = Functional
                    .<List<Class<?>>, ClassNotFoundException> recheck(
                            () -> classnames.parallelStream().map(cn -> uncheck(() -> Class.forName(cn))).collect(Collectors.toList()));
        } catch (final ClassNotFoundException cnfe) {
            throw cnfe;
        }
    }

    public static class MyException extends Exception {
        private static final long serialVersionUID = 1L;

        public MyException(final Exception e) {
            super(e);
        }
    }

    @Test(expected = MyException.class)
    public void testMapChecked() throws Throwable {
        mapChecked(() -> {
            final List<String> classnames = Arrays.asList("java.lang.String", "junk.no.class.Exists");
            @SuppressWarnings({ "unused", "rawtypes" })
            List classes = null;
            classes = recheck(() -> classnames.stream().map(cn -> uncheck(() -> Class.forName(cn))).collect(Collectors.toList()),
                    ClassNotFoundException.class);
        } , (final ClassNotFoundException cnfe) -> new MyException(cnfe));
    }

    @Test(expected = MyException.class)
    public void testMapCheckedReturns() throws Throwable {
        @SuppressWarnings({ "rawtypes", "unused" })
        final List classes = mapChecked(() -> {
            final List<String> classnames = Arrays.asList("java.lang.String", "junk.no.class.Exists");
            return recheck(() -> classnames.stream().map(cn -> uncheck(() -> Class.forName(cn))).collect(Collectors.toList()),
                    ClassNotFoundException.class);
        } , (final ClassNotFoundException cnfe) -> new MyException(cnfe));
    }
}

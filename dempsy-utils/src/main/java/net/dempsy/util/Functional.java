package net.dempsy.util;

import java.util.function.Consumer;

public class Functional {
    @FunctionalInterface
    public static interface SupplierThrows<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public static interface ConsumerThrows<T, E extends Exception> {
        void accept(T obj) throws E;
    }

    public static <T> T unchecked(final SupplierThrows<T> f) {
        try {
            return f.get();
        } catch (final Exception fse) {
            throw new RuntimeException(fse);
        }
    }

    public static <T> T chain(final T t, final Consumer<T> f) {
        f.accept(t);
        return t;
    }

    public static <T, E extends Exception> T chainThrows(final T t, final ConsumerThrows<T, E> f) throws E {
        f.accept(t);
        return t;
    }
}

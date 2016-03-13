package net.dempsy.util;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A series of methods utilities to help with Java 8 functional operations.
 */
public class Functional {
    /**
     * A functional interface that analogous to a {@link Supplier} that throws a checked {@link Exception}
     * 
     * @param <T>
     *            is the type that is supplied by the function.
     * @param <E>
     *            is the exception type that's thrown.
     */
    @FunctionalInterface
    public static interface SupplierThrows<T, E extends Exception> {
        T get() throws E;
    }

    /**
     * A functional interface that analogous to a {@link Consumer} that throws a checked {@link Exception}
     * 
     * @param <T>
     *            is the type that is consumed by the function.
     * @param <E>
     *            is the exception type that's thrown.
     */
    @FunctionalInterface
    public static interface ConsumerThrows<T, E extends Exception> {
        void accept(T obj) throws E;
    }

    /**
     * A functional interface that analogous to a {@link Runnable} that throws a checked {@link Exception}
     * 
     * @param <E>
     *            is the exception type that's thrown.
     */
    @FunctionalInterface
    public static interface RunnableThrows<E extends Exception> {
        void run() throws E;
    }

    /**
     * <p>
     * This method allows for the chaining of calls that don't normally chain. For example, typical setter methods. A simple example would be:
     * </p>
     * 
     * <pre>
     * {@code
     *     properties = chain(new Properties(), p -> p.setProperty("name1", "value1"), p -> p.setProperty("name2", "value2"));
     * }
     * </pre>
     */
    @SafeVarargs
    public static <T> T chain(final T t, final Consumer<T>... fs) {
        for (final Consumer<T> f : fs)
            f.accept(t);
        return t;
    }

    /**
     * <p>
     * This method allows for the chaining of calls that don't normally chain but that also may throw. A simple example would be:
     * </p>
     * 
     * <pre>
     * {@code
     *     properties = chain(new Properties(), p -> p.setProperty("name1", "value1"), p -> p.setProperty("name2", "value2"));
     * }
     * </pre>
     * 
     */
    @SafeVarargs
    public static <T, E extends Exception> T chainThrows(final T t, final ConsumerThrows<T, E>... fs) throws E {
        for (final ConsumerThrows<T, E> f : fs)
            f.accept(t);
        return t;
    }

    /**
     * Wrapper class that encapsulates a checked exception in an unchecked exception
     */
    public static class UncheckingExcpetion extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final Exception checked;

        private UncheckingExcpetion(final Exception checked) {
            super(checked);
            this.checked = checked;
        }
    }

    /**
     * <p>
     * This method allows the use of lambda's that throw exceptions in standard java 8 {@link Stream} operations by converting those exceptions to an unchecked exception. For example:
     * </p>
     * 
     * <p>
     * Suppose I wanted to process a {@link Stream} of classnames:
     * </p>
     * 
     * <pre>
     * {@code
     *     classes = classnames.stream().map(Class::forName).collect(Collectors.toList());
     * }
     * </pre>
     * <p>
     * This wont work since Class.forName throws a checked {@link ClassNotFoundException}. Therefore we can wrap it.
     * </p>
     * 
     * <pre>
     * {@code
     *     classes = classnames.stream().map(cn -> uncheck(() -> Class.forName(cn))).collect(Collectors.toList());
     * }
     * </pre>
     * 
     * @param <T>
     *            is the type that is supplied by the function, f.
     * @param <E>
     *            is the exception type that's thrown by f.
     * @param f
     *            is a {@link SupplierThrows} that throws an exception
     * @return the value that the supplier throws,
     * @throws UncheckingExcpetion
     *             which is a specific {@link RuntimeException} that wraps the underlying exception.
     */
    public static <T, E extends Exception> T uncheck(final SupplierThrows<T, E> f) {
        try {
            return f.get();
        } catch (final Exception fse) {
            throw new UncheckingExcpetion(fse);
        }
    }

    /**
     * <p>
     * This method allows the use of lambda's that throw exceptions in standard java 8 {@link Stream} operations by converting those exceptions to an unchecked exception. For example:
     * </p>
     * 
     * <p>
     * Suppose I wanted to process a {@link Stream} of classnames:
     * </p>
     * 
     * <pre>
     * {@code
     *     strings.stream().forEach(objectOutputStream::writeObject);
     * }
     * </pre>
     * <p>
     * This wont work since {@link ObjectOutputStream#writeObject(Object)} throws a checked {@link IOException}. Therefore we can wrap it.
     * </p>
     * 
     * <pre>
     * {@code
     *     strings.stream().forEach(s -> uncheck(() -> objectOutputStream.writeObject(s));
     * }
     * </pre>
     * 
     * @param <E>
     *            is the exception type that's thrown by f.
     * @param f
     *            is a {@link RunnableThrows} that throws an exception
     * @throws UncheckingExcpetion
     *             which is a specific {@link RuntimeException} that wraps the underlying exception.
     */
    public static <E extends Exception> void uncheck(final RunnableThrows<E> f) {
        try {
            f.run();
        } catch (final Exception fse) {
            throw new UncheckingExcpetion(fse);
        }
    }

    /**
     * <p>
     * This assumes there's a {@link #uncheck(SupplierThrows)} call nested within the provided supplier. An example of the usage would be:
     * </p>
     * 
     * <p>
     * Suppose I wanted to process a {@link Stream} of classnames:
     * </p>
     * 
     * <pre>
     * {@code
     *     classes = classnames.stream().map(Class::forName).collect(Collectors.toList());
     * }
     * </pre>
     * <p>
     * This wont work since Class.forName throws a checked {@link ClassNotFoundException}. Therefore we can wrap it.
     * </p>
     * 
     * <pre>
     * {@code
     *     classes = classnames.stream().map(cn -> uncheck(() -> Class.forName(cn))).collect(Collectors.toList());
     * }
     * </pre>
     * 
     * But this buries the checked exception. If we want the checked exception to be thrown from the stream operation we can {@link #recheck(Supplier)} it. Unfortunately we need to be explicit about the
     * exception type thrown since there's no way for the compiler to infer it. This also means we can't use a static import.
     * 
     * <pre>
     * {@code
     * try {
     *     classes = Functional.&lt;ClassNotFoundException&gt; recheck(() -> classnames.stream().map(cn -> uncheck(() -> Class.forName(cn))));
     * } catch (final ClassNotFoundException cnf) {
     *     ...
     * }
     * }
     * </pre>
     * 
     * @param <T>
     *            is the type that is supplied by the function, f.
     * @param <E>
     *            is the exception type that's wrapped inside of 'f' having been passed to {@link #uncheck(SupplierThrows).
     */
    @SuppressWarnings("unchecked")
    public static <T, E extends Exception> T recheck(final Supplier<T> f) throws E {
        try {
            return f.get();
        } catch (final UncheckingExcpetion e) {
            throw (E) e.checked;
        }
    }

    /**
     * <p>
     * This assumes there's a {@link #uncheck(SupplierThrows)} call nested within the provided supplier. An example of the usage would be:
     * </p>
     * 
     * <p>
     * Suppose I wanted to process a {@link Stream} of classnames:
     * </p>
     * 
     * <pre>
     * {@code
     *     strings.stream().forEach(objectOutputStream::writeObject);
     * }
     * </pre>
     * <p>
     * This wont work since {@link ObjectOutputStream#writeObject(Object)} throws a checked {@link IOException}. Therefore we can wrap it.
     * </p>
     * 
     * <pre>
     * {@code
     *     strings.stream().forEach(s -> uncheck(() -> objectOutputStream.writeObject(s));
     * }
     * </pre>
     * 
     * But this buries the checked exception. If we want the checked exception to be thrown from the stream operation we can {@link #recheck(Runnable)} it. Unfortunately we need to be explicit about the
     * exception type thrown since there's no way for the compiler to infer it. This also means we can't use a static import.
     * 
     * <pre>
     * {@code
     *   try {
     *       Functional.&lt;ClassNotFoundException&gt; recheck(() -> strings.stream().forEach(s -> uncheck(() -> objectOutputStream.writeObject(s))));
     *   } catch (final ClassNotFoundException cnf) {
     *       ....
     *   }
     * }
     * </pre>
     * 
     */
    @SuppressWarnings("unchecked")
    public static <E extends Exception> void recheck(final Runnable f) throws E {
        try {
            f.run();
        } catch (final UncheckingExcpetion e) {
            throw (E) e.checked;
        }
    }

    /**
     * Alternate form of {@link #recheck(Supplier)} that doesn't require the explicit generic and allows for the use of a static import.
     */
    @SuppressWarnings("unchecked")
    public static <T, E extends Exception> T recheck(final Supplier<T> f, final Class<E> exceptionClass) throws E {
        try {
            return f.get();
        } catch (final UncheckingExcpetion e) {
            throw (E) e.checked;
        }
    }

    /**
     * Alternate form of {@link #recheck(Runnable)} that doesn't require the explicit generic and allows for the use of a static import.
     */
    @SuppressWarnings("unchecked")
    public static <E extends Exception> void recheck(final Runnable f, final Class<E> exceptionClass) throws E {
        try {
            f.run();
        } catch (final UncheckingExcpetion e) {
            throw (E) e.checked;
        }
    }
}

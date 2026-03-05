package net.dempsy.utils.test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * A {@link CloseableRule} can be used to manage resources for unit tests that
 * are {@link AutoCloseable}s.
 */
public class CloseableRule implements AutoCloseable {
    private final AutoCloseable[] autoCloseables;
    private final Supplier<AutoCloseable> supplier;

    private final String newLine = System.getProperty("line.separator");

    /**
     * Create a rule that automatically closes the {@link AutoCloseable}s
     * passed in.
     *
     * @param autoCloseables - {@link AutoCloseable}s to close when at the
     *            conclusion of the test scope.
     */
    public CloseableRule(final AutoCloseable... autoCloseables) {
        this.autoCloseables = autoCloseables;
        this.supplier = null;
    }

    /**
     * Create a rule that creates an {@link AutoCloseable} through the given
     * {@link Supplier} during the setup phase and closes the
     * {@link AutoCloseable} during the tear-down phase.
     *
     * @param supplier - the source of the given {@link AutoCloseable}
     */
    public CloseableRule(final Supplier<AutoCloseable> supplier) {
        this.autoCloseables = new AutoCloseable[1];
        this.supplier = supplier;
    }

    @SuppressWarnings("unchecked")
    public <T extends AutoCloseable> T get(final Class<T> clazz) {
        return (T)Arrays.stream(autoCloseables)
            .filter(o -> clazz.isAssignableFrom(o.getClass()))
            .findFirst()
            .orElse(null);
    }

    public void before() {
        if(supplier != null)
            autoCloseables[0] = supplier.get();
    }

    /**
     * Close all of the {@link AutoCloseable}'s that were passed in the constructor.
     */
    @Override
    public void close() {
        final List<Exception> exceptions = new ArrayList<>();
        Arrays.stream(autoCloseables)
            .filter(ac -> ac != null)
            .forEach(ac -> {
                try {
                    ac.close();
                } catch(final Exception e) {
                    exceptions.add(e);
                }
            });
        if(exceptions.size() > 0) {
            throw new Error("Exceptions thrown while closing:" + newLine + formatExceptionListAsMessage(exceptions), exceptions.get(0));
        }
    }

    private String formatExceptionListAsMessage(final List<Exception> exceptions) {
        final StringBuilder sb = new StringBuilder();
        exceptions.stream()
            .forEach(e -> {
                final StringWriter stringWriter = new StringWriter();
                try (final PrintWriter w = new PrintWriter(stringWriter)) {
                    sb.append("Exception thrown during close: ");
                    w.println();
                    e.printStackTrace(w);
                }
                sb.append(stringWriter.toString());
            });
        return sb.toString();
    }
}

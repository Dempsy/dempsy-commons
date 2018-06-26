package net.dempsy.utils.test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.rules.ExternalResource;

/**
 * A {@link CloseableRule} can be used to manage resources for unit tests that are {@link AutoCloseable}s.
 * 
 * <pre>
 * {@code
 * &#64;ClassRule
 * public static final ClosableRule closables = new ClosableRule(new ClassPathXmlApplicationContext(...));
 * 
</pre>
 */
public class CloseableRule extends ExternalResource {
   private final AutoCloseable[] autoCloseables;

   private final String newLine = System.getProperty("line.separator");

   public CloseableRule(final AutoCloseable... autoCloseables) {
      this.autoCloseables = autoCloseables;
   }

   @SuppressWarnings("unchecked")
   public <T extends AutoCloseable> T get(final Class<T> clazz) {
      return (T)Arrays.stream(autoCloseables)
            .filter(o -> clazz.isAssignableFrom(o.getClass()))
            .findFirst()
            .orElse(null);
   }

   /**
    * On the {@code after} test phase, close all of the {@link AutoCloseable}'s that were passed in the constructor.
    */
   @Override
   protected void after() {
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

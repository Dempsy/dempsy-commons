package net.dempsy.utils.test;

/**
 * <p>
 * Multi threaded tests are notoriously difficult to write. You should almost NEVER (though there is one exception to this) simply <em>sleep</em> for a certain amount of time and then expect a condition to be
 * met. First of all it makes your tests slow, and it also makes them somewhat platform dependent. Ideally you should wait on the condition and be notified when the condition is met.
 * </p>
 * <p>
 * Given that you're writing a test, the next best thing to this ideal is to <em>poll</em> for the condition. While this should be avoided in production code (where possible), it's not that important in unit
 * tests. And it's usually a lot easier to do than setting up a condition variable or a CountDownLatch and triggering it appropriately.
 * </p>
 * <p>
 * Therefore the class has several utility methods for helping to write multithreaded tests by allowing easy polling for a particular condition for a fixed amount of time and returns the final condition value.
 * For example:
 * </p>
 * <pre>
 * {@code
 * import static net.dempsy.utils.test.ConditionPoll.poll;
 * import static org.junit.Assert.assertTrue;
 * ...
 * public int numOfTimesSomethingHappens = 0;
 * ....
 * new Thread(() -> { doSomethingThatIncrementsNumOfTimesSomethingHappensAndExit() }).start();
 *     
 * ...
 * assertTrue(poll(() -> numOfTimesSomethingHappens == numTimesExpected));
 * }
 * </pre>
 */
public class ConditionPoll {
    /**
     * The default polling timeout.
     */
    public static final long baseTimeoutMillis = 20000;

    /**
     * This is the interface that serves as the root for anonymous classes passed to the poll call.
     */
    @FunctionalInterface
    public static interface Condition<T> {
        /**
         * Return whether or not the condition we are polling for has been met yet.
         */
        public boolean conditionMet(T o) throws Throwable;
    }

    @FunctionalInterface
    public static interface SimpleCondition extends Condition<Object> {
        /**
         * The developer should return whether or not the condition we're polling for has been met.
         */
        public abstract boolean conditionMet();

        @Override
        public default boolean conditionMet(final Object o) {
            return conditionMet();
        }
    }

    /**
     * <p>
     * Poll for a given condition for timeoutMillis milliseconds. If the condition hasn't been met by then return false. Otherwise, return true as soon as the condition is met.
     * </p>
     * 
     * <p>
     * Anything passed to as the userObject will be passed on to the {@link Condition} and is for the implementor to use as they see fit.
     * </p>
     */
    public static <T> boolean poll(final long timeoutMillis, final T userObject, final Condition<T> condition) throws Throwable {
        boolean conditionMet = condition.conditionMet(userObject);
        for (final long endTime = System.currentTimeMillis() + timeoutMillis; endTime > System.currentTimeMillis() && !conditionMet;) {
            Thread.sleep(10);
            conditionMet = condition.conditionMet(userObject);
        }
        return conditionMet;
    }

    /**
     * <p>
     * Poll for a given condition for {@link ConditionPoll#baseTimeoutMillis} milliseconds. If the condition hasn't been met by then return false. Otherwise, return true as soon as the condition is met.
     * </p>
     * 
     * <p>
     * Anything passed to as the userObject will be passed on to the {@link Condition} and is for the implementor to use as they see fit.
     * </p>
     */
    public static <T> boolean poll(final T userObject, final Condition<T> condition) throws Throwable {
        return poll(baseTimeoutMillis, userObject, condition);
    }

    /**
     * <p>
     * Poll for a given condition for {@link ConditionPoll#baseTimeoutMillis} milliseconds. If the condition hasn't been met by then return false. Otherwise, return true as soon as the condition is met.
     * </p>
     */
    @SuppressWarnings("unchecked")
    public static boolean poll(@SuppressWarnings("rawtypes") final Condition condition) throws Throwable {
        return poll(baseTimeoutMillis, null, condition);
    }

}

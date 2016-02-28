package net.dempsy.utils.test;

/**
 * This class has several utility methods for helping to write tests.
 */
public class ConditionPoll {
    /**
     * The default polling timeout.
     */
    public static final long baseTimeoutMillis = 20000;

    /**
     * This is the interface that serves as the root for anonymous classes passed to the poll call.
     */
    public static interface Condition<T> {
        /**
         * Return whether or not the condition we are polling for has been met yet.
         */
        public boolean conditionMet(T o) throws Throwable;
    }

    public static abstract class SimpleCondition implements Condition<Object> {
        /**
         * The developer should return whether or not the condition we're polling for has been met.
         */
        public abstract boolean conditionMet();

        @Override
        public boolean conditionMet(final Object o) {
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

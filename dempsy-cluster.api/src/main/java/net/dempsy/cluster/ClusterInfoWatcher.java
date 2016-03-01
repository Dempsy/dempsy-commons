package net.dempsy.cluster;

/**
 * Implementations of this interface are used for notifications when changes happen.
 */
@FunctionalInterface
public interface ClusterInfoWatcher {
    public void process();
}

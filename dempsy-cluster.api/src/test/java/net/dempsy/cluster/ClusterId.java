/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dempsy.cluster;

/**
 * <p>
 * This class represents the Id of a message processor cluster within a Dempsy application. Cluster Id's are essentially a two level name: namespace name, cluster name.
 * </p>
 * 
 * <p>
 * A cluster Id should be unique.
 * </p>
 * 
 * <p>
 * ClusterIds are immutable.
 * </p>
 * 
 * <p>
 * See the User Guide for an explanation of what a 'message processor cluster' is.
 * </p>
 */
public class ClusterId {
    public final String namespace;
    public final String clusterName;

    /**
     * Create a cluster Id from the constituent parts.
     * 
     * @param applicationName
     *            is the application name that the cluster identified with this Id is part of.
     * 
     * @param clusterName
     *            is the cluster name within the given application that the cluster identified with this Id is part of.
     */
    public ClusterId(final String applicationName, final String clusterName) {
        this.namespace = applicationName;
        this.clusterName = clusterName;
    }

    /**
     * Convenience constructor for copying an existing ClusterId.
     * 
     * @param other
     *            is the cluster id to make a copy of.
     */
    public ClusterId(final ClusterId other) {
        this.namespace = other.namespace;
        this.clusterName = other.clusterName;
    }

    /**
     * <p>
     * Provide the ClusterId as a path. The form of the string returned is:
     * </p>
     * <br>
     * 
     * @return: "/" + this.getApplicationName() + "/" + this.getMpClusterName()
     */
    public String asPath() {
        return "/" + this.namespace + "/" + this.clusterName;
    }

    @Override
    public String toString() {
        return this.namespace + ":" + this.clusterName;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((namespace == null) ? 0 : namespace.hashCode());
        result = prime * result + ((clusterName == null) ? 0 : clusterName.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final ClusterId other = (ClusterId) obj;
        if (namespace == null) {
            if (other.namespace != null)
                return false;
        } else if (!namespace.equals(other.namespace))
            return false;
        if (clusterName == null) {
            if (other.clusterName != null)
                return false;
        } else if (!clusterName.equals(other.clusterName))
            return false;
        return true;
    }
}

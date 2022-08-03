/*
 * Copyright 2022 Jim Carroll
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dempsy.util;

/**
 * This class will execute the Runnable provided to the constructor
 * once every so many calls to {@code run()}. It's meant to be used
 * in a high performance loop so it uses a mask rather than a mod
 * to determine when to actually do the call.
 */
public class OccasionalRunnable {

    private final long mask;
    private long callCount = 0;

    public OccasionalRunnable(final long frequency) {
        if(Long.bitCount(frequency) != 1)
            throw new IllegalArgumentException("When using an " + OccasionalRunnable.class.getSimpleName() + " the frequency must be a power of 2");
        this.mask = frequency - 1;
    }

    public void maybeRun(final Runnable work) {
        callCount++;
        if((callCount & mask) == 0)
            work.run();
    }

    public static Runnable staticOccasionalRunnable(final long frequency, final Runnable runnable) {
        final OccasionalRunnable partial = new OccasionalRunnable(frequency);
        return () -> partial.maybeRun(runnable);
    }

}

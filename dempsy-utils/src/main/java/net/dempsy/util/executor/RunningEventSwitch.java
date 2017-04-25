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

package net.dempsy.util.executor;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <p>This is a helper class for coordinating a "restartable" job. A "restartable" 
 * job is one that can be started over before it's finished but there should only 
 * be on running at a time. To use it you should instantiate a {@link RunningEventSwitch}.</p>
 * 
 * <p>When you execute a job in another thread:</p>
 * <ul>
 * <li>the job should indicate it's started by calling {@link #workerRunning()}.</li>
 * <li>When the worker completes the job it should call {@link #workerFinished()}.</li>
 * <li>The worker should be checking {@link #wasPreempted()} and if it ever returns 'true' 
 * the worker should exit.</li>
 * <li>The initiating (parent) thread should wait for the worker to start by calling 
 * {@link #waitForWorkerToStart()}.</li>
 * <li>If it subsequently needs to restart the job it should call {@link #preemptWorkerAndWait()},
 * and then it can restart the job using a new Thread.</li>
 * </ul>
 * 
 * <p>The RunningEventSwitch can also monitor an externally supplied "isRunning" flag
 * so that the job will be 'preempted' whenever that's also set to false</p>
 */
public class RunningEventSwitch {
    final AtomicBoolean externalIsRunning;
    final AtomicBoolean isRunning = new AtomicBoolean(false);
    final AtomicBoolean stopRunning = new AtomicBoolean(false);
    // this flag is used to hold up the calling thread's exit of this method
    // until the worker is underway.
    final AtomicBoolean runningGate = new AtomicBoolean(false);

    public RunningEventSwitch(final AtomicBoolean externalIsRunning) {
        this.externalIsRunning = externalIsRunning;
    }

    public RunningEventSwitch() {
        this(new AtomicBoolean(true));
    }

    /**
    * This is called from the worker thread to notify the fact that
    * it's been started.
    */
    public void workerRunning() {
        // this is synchronized because it's used as a condition variable
        // along with the condition.
        stopRunning.set(false);
        isRunning.set(true);

        synchronized (runningGate) {
            runningGate.set(true);
            runningGate.notify();
        }
    }

    /**
    * The worker thread can use this method to check if it's been explicitly preempted.
    * @return
    */
    public boolean wasPreempted() {
        return stopRunning.get();
    }

    /**
    * The worker thread should indicate that it's done in a finally clause on it's way
    * out.
    */
    public void workerFinished() {
        // This kicks the preemptWorkerAndWait out.
        synchronized (isRunning) {
            isRunning.set(false);
            isRunning.notify();
        }
    }

    /**
    * The main thread uses this method when it needs to preempt the worker and
    * wait for the worker to finish before continuing.
    */
    public void preemptWorkerAndWait() {
        // We need to see if we're already executing
        stopRunning.set(true); // kick out any running instantiation thread
        // wait for it to exit, it it's even running - also consider the overall
        // Mp isRunning flag.
        synchronized (isRunning) {
            while (isRunning.get() && externalIsRunning.get()) {
                try {
                    isRunning.wait();
                } catch (final InterruptedException e) {}
            }
        }
    }

    /**
    * This allows the main thread to wait until the worker is started in order
    * to continue. This method only works once. It resets the flag so a second
    * call will block until another thread calls to workerInitiateRun().
    */
    public void waitForWorkerToStart() {
        // make sure the thread is running before we head out from the synchronized block
        synchronized (runningGate) {
            while (runningGate.get() == false && externalIsRunning.get()) {
                try {
                    runningGate.wait();
                } catch (final InterruptedException ie) {}
            }

            runningGate.set(false); // reset this flag
        }
    }
}

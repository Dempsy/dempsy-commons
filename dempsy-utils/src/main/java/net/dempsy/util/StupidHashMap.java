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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

public class StupidHashMap<K, V> implements Map<K, V> {
    private static final int DEFAULT_INITAL_TABLE_SIZE = 2048;
    private static final int SPIN_TRIES = 100;
    private final AtomicInteger size = new AtomicInteger(0);

    private final int mask;
    private final Node<K, V>[] table;

    @SuppressWarnings("unchecked")
    public StupidHashMap(final int initialCapacity) {
        if(Integer.bitCount(initialCapacity) != 1)
            throw new IllegalArgumentException("The initial capacity must be a power of 2.");

        table = new Node[initialCapacity];
        for(int i = 0; i < initialCapacity; i++)
            table[i] = new Node<K, V>();
        mask = initialCapacity - 1;
    }

    public StupidHashMap() {
        this(DEFAULT_INITAL_TABLE_SIZE);
    }

    @Override
    public int size() {
        return size.get();
    }

    @Override
    public V putIfAbsent(final K k, final V v) {
        return computeIfAbsent(k, () -> v);
    }

    public V computeIfAbsent(final K k, final Supplier<V> v) {
        final int h = hash(k);
        final Node<K, V> b = table[h & mask];
        while(true) {
            FinalWrapper<Node<K, V>> tmpnode = b.next;
            if(tmpnode == null) { // this bin has no entries yet
                waitFor(b); // grab the lock
                if(b.next == null) { // double check - still has no entries.
                    b.next = new FinalWrapper<Node<K, V>>(new Node<K, V>(h, k, v.get()));
                    tmpnode = b.next;
                    b.mine.lazySet(1);
                    size.getAndIncrement();
                    return null;
                }

                // in the meantime, it was set. So borrow the results and move on.
                tmpnode = b.next;
                b.mine.lazySet(1); // release the lock
            }

            // tmpnode now has the current node value
            FinalWrapper<Node<K, V>> prev = null;
            while(tmpnode != null) {
                final Node<K, V> tmpnodevalue = tmpnode.value;
                if(tmpnodevalue.key != null) { // it's possible for this to be null if tmpnode is pointing to
                                               // a bin indicator. This is only possible if we had a "remove"
                                               // collision. This is how remove collisions are managed. The
                                               // removed node is pointed to the bin.
                    if(tmpnodevalue.hash == h && tmpnodevalue.key.equals(k)) { // we found an existing entry
                        final Node<K, V> cur = tmpnodevalue;
                        final V ret = cur.value;
                        return ret;
                    }
                }

                // move forward
                prev = tmpnode;
                tmpnode = tmpnodevalue.next;
            }

            // if we got here, then this is a new value and prev.next = null
            // prev can't be null
            waitFor(prev.value);
            // double check and make sure it's still null
            if(prev.value.next != null) {
                prev.value.mine.lazySet(1);
                continue; // start over and try again
            }
            prev.value.next = new FinalWrapper<Node<K, V>>(new Node<K, V>(h, k, v.get()));
            prev.value.mine.lazySet(1);
            size.getAndIncrement();
            return null;
        }
    }

    @Override
    public V put(final K k, final V v) {
        // TODO: Maybe

        // final int h = hash(k);
        // final Node<K, V> b = table[h & mask];
        // while (true) {
        // // satisfy the memory model/final semantics
        // FinalWrapper<Node<K, V>> tmpnode = b.next;
        // if (tmpnode == null) { // this bin has no entries yet.
        // waitFor(b); // grab the lock
        // if (b.next == null) { // double check we're still okay
        // b.next = new FinalWrapper<Node<K, V>>(new Node<K, V>(h, k, v));
        // tmpnode = b.next;
        // b.mine.lazySet(true);
        // size.getAndIncrement();
        // return null;
        // }
        //
        // tmpnode = b.next;
        // b.mine.lazySet(true); // release the lock - initiating the "happens-before" semantics
        // }
        //
        // // tmpnode now has the current node value
        // FinalWrapper<Node<K, V>> prev = null;
        // while (tmpnode != null) {
        // if (tmpnode.value.key != null) { // it's possible for this to be null if tmpnode is pointing to
        // // a bin indicator. This is only possible if we had a "remove"
        // // collision. This is how remove collisions are managed. The
        // // removed node is pointed to the bin.
        // if (tmpnode.value.hash == h && tmpnode.value.key.equals(k)) { // we found an existing entry
        // waitFor(tmpnode.value);
        // final Node<K, V> cur = tmpnode.value;
        // final V ret = cur.value;
        // cur.value = v; // affect the change.
        // tmpnode.value.mine.lazySet(true); // change should be visible
        // return ret;
        // }
        // }
        // // move forward
        // prev = tmpnode;
        // tmpnode = tmpnode.value.next;
        // }
        //
        // // if we got here, then this is a new value
        // // prev can't be null
        // waitFor(prev.value);
        // // double check
        // if (prev.value.next == null)
        // continue; // start over and try again
        // prev.value.next = new FinalWrapper<Node<K, V>>(new Node<K, V>(h, k, v));
        // prev.value.mine.lazySet(true);
        // size.getAndIncrement();
        // return null;
        // }
        throw new UnsupportedOperationException();
    }

    @Override
    public V remove(final Object k) {
        final int h = hash(k);
        final Node<K, V> b = table[h & mask];
        boolean done;
        do {
            done = true;

            // satisfy the memory model/final semantics
            FinalWrapper<Node<K, V>> tmpnode = b.next;
            if(tmpnode == null) { // the entire bin is empty
                return null;
            }

            // tmpnode now has the current node value
            FinalWrapper<Node<K, V>> prev = null;
            while(tmpnode != null) {
                final Node<K, V> tmpnodevalue = tmpnode.value;
                if(tmpnodevalue.key != null && tmpnodevalue.hash == h && tmpnodevalue.key.equals(k)) { // we found an existing entry
                    // if prev is null then we're at the first entry in the bin so we need to lock the Bin
                    final Node<K, V> prevNode = (prev == null) ? b : prev.value;
                    waitFor(tmpnodevalue); // lock THIS node so no one
                                           // can remove the one after it
                                           // or loose something by adding
                                           // after it
                    waitFor(prevNode); // lock the previous node (or Bin) since we're going to mod it.

                    // double check.
                    if(prevNode.next != tmpnode) { // are we still in the list?
                        // if not, then start over
                        prevNode.mine.lazySet(1); // unlock
                        tmpnodevalue.mine.lazySet(1); // unlock
                        done = false;
                        break;
                    }

                    // otherwise we can jumper
                    prevNode.next = tmpnodevalue.next;
                    tmpnodevalue.next = new FinalWrapper<Node<K, V>>(b); // redirect to the bin itself

                    size.getAndDecrement();

                    // release the hounds
                    prevNode.mine.lazySet(1); // unlock
                    tmpnodevalue.mine.lazySet(1); // unlock
                    return tmpnodevalue.value;
                }

                // move forward
                prev = tmpnode;
                tmpnode = tmpnodevalue.next;
            }

            // if we got here, then there's nothing to remove ... if we're not done then try again
            if(done)
                return null;
        } while(true);

    }

    @Override
    public V get(final Object k) {
        final int h = hash(k);
        final Node<K, V> b = table[h & mask];

        FinalWrapper<Node<K, V>> tmpnode = b.next;
        if(tmpnode == null)
            return null;

        // tmpnode now has the current node value
        while(tmpnode != null) {
            if(tmpnode.value.key != null && tmpnode.value.hash == h && tmpnode.value.key.equals(k)) // we found an existing entry
                return tmpnode.value.value;
            tmpnode = tmpnode.value.next;
        }
        return null;
    }

    @Override
    public Set<K> keySet() {
        final Set<K> ret = new HashSet<>();
        final Node<K, V>[] tab = table; // snapshot of the table.
        final int cap = tab.length;
        for(int i = 0; i < cap; i++) {
            final Node<K, V> b = table[i];

            FinalWrapper<Node<K, V>> tmpnode = b.next;
            while(tmpnode != null) {
                if(tmpnode.value.key != null)
                    ret.add(tmpnode.value.key);
                tmpnode = tmpnode.value.next;
            }
        }
        return ret;
    }

    static class ShmEntry<K, V> {
        public final K key;
        public final V value;

        ShmEntry(final K k, final V v) {
            this.key = k;
            this.value = v;
        }
    }

    List<ShmEntry<K, V>> offlineCollect() {
        final Node<K, V>[] tab = table;
        final List<ShmEntry<K, V>> ret = new ArrayList<>();
        for(int i = 0; i < tab.length; i++) {
            Node<K, V> cur = tab[i].next.value;
            while(cur != null) {
                if(cur.key != null)
                    ret.add(new ShmEntry<>(cur.key, cur.value));
                else
                    throw new IllegalStateException("Null Key in offline mode. This shouldn't happen.");

                cur = cur.next.value;
            }
        }
        return ret;
    }

    private static class FinalWrapper<V> {
        public final V value;

        public FinalWrapper(final V value) {
            this.value = value;
        }
    }

    private final static class Node<K, V> {
        public FinalWrapper<Node<K, V>> next = null;
        // This is actually a boolean but AtomicBoolean uses an int
        // value anyway so we're skipping the middle man
        public final AtomicInteger mine = new AtomicInteger(1);
        final int hash;
        final K key;
        V value;

        private Node() {
            hash = -1;
            key = null;
            value = null;
        }

        public Node(final int h, final K k, final V v) {
            this.key = k;
            this.value = v;
            this.hash = h;
        }
    }

    private static final int hash(final Object key) {
        int h;
        return (h = key.hashCode()) ^ (h >>> 16);
    }

    private static final void waitFor(final Node<?, ?> bin) {
        int counter = SPIN_TRIES;
        do {
            if(bin.mine.compareAndSet(1, 0))
                return;
            if(counter > 0)
                counter--;
            else
                LockSupport.parkNanos(1L);
        } while(true);
    }

    @Override
    public boolean isEmpty() {
        return size.get() == 0;
    }

    @Override
    public boolean containsKey(final Object key) {
        return get(key) != null;
    }

    @Override
    public boolean containsValue(final Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(final Map<? extends K, ? extends V> m) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<V> values() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        throw new UnsupportedOperationException();
    }
}

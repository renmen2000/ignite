/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.cache.transactions;

import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteEvents;
import org.apache.ignite.events.Event;
import org.apache.ignite.events.TransactionStateChangedEvent;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.lang.IgnitePredicate;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.apache.ignite.transactions.Transaction;
import org.apache.ignite.transactions.TransactionConcurrency;
import org.apache.ignite.transactions.TransactionIsolation;
import org.apache.ignite.transactions.TransactionState;

import static org.apache.ignite.events.EventType.EVTS_TX;
import static org.apache.ignite.events.EventType.EVT_TX_COMMITTED;
import static org.apache.ignite.events.EventType.EVT_TX_PREPARED;
import static org.apache.ignite.events.EventType.EVT_TX_RESUMED;
import static org.apache.ignite.events.EventType.EVT_TX_ROLLED_BACK;
import static org.apache.ignite.events.EventType.EVT_TX_STARTED;
import static org.apache.ignite.events.EventType.EVT_TX_SUSPENDED;

/**
 * Tests transaction state change event.
 */
public class TxStateChangeEventTest extends GridCommonAbstractTest {
    /** Label. */
    private final String lb = "testLabel";

    /** Timeout. */
    private final long timeout = 404;

    /** Creation. */
    private AtomicBoolean creation = new AtomicBoolean();

    /** Commit. */
    private AtomicBoolean commit = new AtomicBoolean();

    /** Rollback. */
    private AtomicBoolean rollback = new AtomicBoolean();

    /** Suspend. */
    private AtomicBoolean suspend = new AtomicBoolean();

    /** Resume. */
    private AtomicBoolean resume = new AtomicBoolean();

    /** Prepare. */
    private AtomicBoolean prepare = new AtomicBoolean();

    /**
     *
     */
    public void testLocal() throws Exception {
        test(true);
    }

    /**
     *
     */
    public void testRemote() throws Exception {
        test(false);
    }

    /**
     *
     */
    private void test(boolean loc) throws Exception {
        Ignite ignite = startGrids(5);

        final IgniteEvents evts = ignite.events();

        evts.enableLocal(EVTS_TX);

        if (loc)
            evts.localListen((IgnitePredicate<Event>)e -> {
                assert e instanceof TransactionStateChangedEvent;

                checkEvent((TransactionStateChangedEvent)e);

                return true;
            }, EVTS_TX);
        else
            evts.remoteListen(null,
                (IgnitePredicate<Event>)e -> {
                    assert e instanceof TransactionStateChangedEvent;

                    checkEvent((TransactionStateChangedEvent)e);

                    return false;
                },
                EVTS_TX);

        IgniteCache cache = ignite.getOrCreateCache(defaultCacheConfiguration().setBackups(2));

        // create & commit
        try (Transaction tx = ignite.transactions().withLabel(lb).txStart(
            TransactionConcurrency.PESSIMISTIC, TransactionIsolation.SERIALIZABLE, timeout, 3)) {
            cache.put(1, 1);

            tx.commit();
        }

        assertTrue(
            creation.get() &&
                commit.get() &&
                !rollback.get() &&
                !suspend.get() &&
                !resume.get() &&
                prepare.get());

        clear();

        // create & suspend & resume & commit
        try (Transaction tx = ignite.transactions().withLabel(lb).txStart(
            TransactionConcurrency.OPTIMISTIC, TransactionIsolation.SERIALIZABLE, timeout, 3)) {
            cache.put(2, 7);

            tx.suspend();

            U.sleep(100);

            tx.resume();

            tx.commit();
        }

        assertTrue(
            creation.get() &&
                commit.get() &&
                !rollback.get() &&
                suspend.get() &&
                resume.get()&&
                prepare.get());

        clear();

        // create & rollback (pessimistic)
        try (Transaction tx = ignite.transactions().withLabel(lb).txStart(
            TransactionConcurrency.PESSIMISTIC, TransactionIsolation.SERIALIZABLE, timeout, 3)) {
            cache.put(4, 5);
        }

        assertTrue(
            creation.get() &&
                !commit.get() &&
                rollback.get() &&
                !suspend.get() &&
                !resume.get()&&
                !prepare.get());
    }

    /**
     *
     */
    private void clear() {
        creation.set(false);
        commit.set(false);
        rollback.set(false);
        suspend.set(false);
        resume.set(false);
        prepare.set(false);
    }

    /**
     * @param evt Event.
     */
    private void checkEvent(TransactionStateChangedEvent evt) {
        Transaction tx = evt.tx();

        assertTrue(tx.timeout() > 0); // Remote tx has relative timeout

        switch (evt.type()) {
            case EVT_TX_STARTED: {
                assertEquals(lb, tx.label());
                assertFalse(creation.get());
                assertEquals(tx.state(), TransactionState.ACTIVE);

                creation.set(true);

                break;
            }

            case EVT_TX_COMMITTED: {
                assertEquals(tx.state(), TransactionState.COMMITTED);

                commit.set(true);

                break;
            }

            case EVT_TX_ROLLED_BACK: {
                assertEquals(tx.state(), TransactionState.ROLLED_BACK);

                rollback.set(true);

                break;
            }

            case EVT_TX_SUSPENDED: {
                assertEquals(lb, tx.label());
                assertFalse(commit.get());
                assertEquals(tx.state(), TransactionState.SUSPENDED);

                suspend.set(true);

                break;
            }

            case EVT_TX_RESUMED: {
                assertEquals(lb, tx.label());
                assertFalse(commit.get());
                assertEquals(tx.state(), TransactionState.ACTIVE);

                resume.set(true);

                break;
            }

            case EVT_TX_PREPARED: {
                assertEquals(tx.state(), TransactionState.PREPARED);

                prepare.set(true);

                break;
            }
        }
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        super.afterTest();

        stopAllGrids();
    }
}

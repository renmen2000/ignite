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

package org.apache.ignite.examples.streaming.wordcount;

import org.apache.ignite.*;
import org.apache.ignite.cache.affinity.*;
import org.apache.ignite.cache.query.*;
import org.apache.ignite.examples.*;

import java.util.*;

/**
 * Periodically query popular numbers from the streaming cache.
 * To start the example, you should:
 * <ul>
 *     <li>Start a few nodes using {@link ExampleNodeStartup} or by starting remote nodes as specified below.</li>
 *     <li>Start streaming using {@link StreamWords}.</li>
 *     <li>Start querying popular words using {@link QueryWords}.</li>
 * </ul>
 * <p>
 * You should start remote nodes by running {@link ExampleNodeStartup} in another JVM.
 */
public class QueryWords {
    /**
     * Schedules words query execution.
     *
     * @param args Command line arguments (none required).
     * @throws Exception If failed.
     */
    public static void main(String[] args) throws Exception {
        // Mark this cluster member as client.
        Ignition.setClientMode(true);

        try (Ignite ignite = Ignition.start("examples/config/example-ignite.xml")) {
            if (!ExamplesUtils.hasServerNodes(ignite))
                return;

            // The cache is configured with sliding window holding 1 second of the streaming data.
            IgniteCache<AffinityUuid, String> stmCache = ignite.getOrCreateCache(CacheConfig.wordCache());

            // Select top 10 words.
            SqlFieldsQuery top10Qry = new SqlFieldsQuery(
                "select _val, count(_val) as cnt from String group by _val order by cnt desc limit 10",
                true /*collocated*/
            );

            // Select average, min, and max counts among all the words.
            SqlFieldsQuery statsQry = new SqlFieldsQuery(
                "select avg(cnt), min(cnt), max(cnt) from (select count(_val) as cnt from String group by _val)");

            // Query top 10 popular numbers every 5 seconds.
            while (true) {
                // Execute queries.
                List<List<?>> top10 = stmCache.query(top10Qry).getAll();
                List<List<?>> stats = stmCache.query(statsQry).getAll();

                // Print average count.
                List<?> row = stats.get(0);

                if (row.get(0) != null)
                    System.out.printf("Query results [avg=%.2f, min=%d, max=%d]%n", row.get(0), row.get(1), row.get(2));

                // Print top 10 words.
                ExamplesUtils.printQueryResults(top10);

                Thread.sleep(5000);
            }
        }
    }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hbase;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.replication.ReplicationException;
import org.apache.hbase.hbck1.HBaseFsck;

/**
 * Checks and repairs for hbase replication.
 */
public class ReplicationFsck implements Closeable {
  private final Configuration configuration;

  ReplicationFsck(Configuration conf) {
    this.configuration = conf;
  }

  @Override
  public void close() {
    // Nothing to do.
  }

  int fsck(List<String> tables, boolean fix) throws IOException {
    try (HBaseFsck hbaseFsck = new HBaseFsck(this.configuration)) {
      hbaseFsck.connect();
      hbaseFsck.setFixReplication(fix);
      hbaseFsck.checkAndFixReplication();
      if (tables != null && !tables.isEmpty()) {
        hbaseFsck.setCleanReplicationBarrier(fix);
        for (String table : tables) {
          hbaseFsck.setCleanReplicationBarrierTable(table);
          hbaseFsck.cleanReplicationBarrier();
        }
      }
    } catch (ClassNotFoundException | ReplicationException e) {
      throw new IOException(e);
    }
    return 0;
  }
}

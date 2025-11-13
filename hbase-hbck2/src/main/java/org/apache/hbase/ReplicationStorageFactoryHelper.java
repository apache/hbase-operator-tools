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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.replication.ReplicationPeerStorage;
import org.apache.hadoop.hbase.replication.ReplicationQueueStorage;
import org.apache.hadoop.hbase.replication.ReplicationStorageFactory;
import org.apache.hadoop.hbase.zookeeper.ZKWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for supporting different versions of HBase for creating
 * {@link ReplicationQueueStorage} and {@link ReplicationPeerStorage}.
 */
public final class ReplicationStorageFactoryHelper {

  private static final Logger LOG = LoggerFactory.getLogger(ReplicationStorageFactoryHelper.class);

  private ReplicationStorageFactoryHelper() {
  }

  public static ReplicationPeerStorage getReplicationPeerStorage(Configuration conf, ZKWatcher zkw,
    FileSystem fs) {
    // Case HBase >= 2.6.0: Invoke the method that requires three parameters
    try {
      Method method = ReplicationStorageFactory.class.getMethod("getReplicationPeerStorage",
        FileSystem.class, ZKWatcher.class, Configuration.class);
      return (ReplicationPeerStorage) method.invoke(null, fs, zkw, conf);
    } catch (NoSuchMethodException e) {
      LOG.debug("No getReplicationPeerStorage method with FileSystem as a parameter, "
        + "should be HBase 2.6-", e);
    } catch (IllegalAccessException | InvocationTargetException e) {
      // getReplicationPeerStorage method does not throw any exceptions, so should not arrive here
      throw new RuntimeException(e);
    }
    // Case HBase < 2.6.0: Fall back to the method that requires only two parameters
    try {
      Method method = ReplicationStorageFactory.class.getMethod("getReplicationPeerStorage",
        ZKWatcher.class, Configuration.class);
      return (ReplicationPeerStorage) method.invoke(null, zkw, conf);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  public static ReplicationQueueStorage getReplicationQueueStorage(Configuration conf,
    ZKWatcher zkw, Connection conn) {
    try {
      Method method = ReplicationStorageFactory.class.getMethod("getReplicationQueueStorage",
        Connection.class, Configuration.class);
      return (ReplicationQueueStorage) method.invoke(null, conn, conf);
    } catch (NoSuchMethodException e) {
      LOG.debug("No getReplicationQueueStorage method with Connection as a parameter, "
        + "should be HBase 2.x", e);
    } catch (IllegalAccessException | InvocationTargetException e) {
      // getReplicationQueueStorage method does not throw any exceptions, so should not arrive here
      throw new RuntimeException(e);
    }
    try {
      Method method = ReplicationStorageFactory.class.getMethod("getReplicationQueueStorage",
        ZKWatcher.class, Configuration.class);
      return (ReplicationQueueStorage) method.invoke(null, zkw, conf);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      // getReplicationQueueStorage method does not throw any exceptions, so should not arrive here
      throw new RuntimeException(e);
    }
  }
}

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
package org.apache.hbase.hbck1;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.replication.ReplicationException;
import org.apache.hadoop.hbase.replication.ReplicationPeerStorage;
import org.apache.hadoop.hbase.replication.ReplicationQueueInfo;
import org.apache.hadoop.hbase.replication.ReplicationQueueStorage;
import org.apache.hadoop.hbase.zookeeper.ZKWatcher;
import org.apache.hbase.ReplicationStorageFactoryHelper;
import org.apache.yetus.audience.InterfaceAudience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hbase.thirdparty.com.google.common.base.Throwables;

/**
 * Check and fix undeleted replication queues for removed peerId. Copied over wholesale from hbase.
 * Unaltered except for package and imports.
 */
@InterfaceAudience.Private
public class ReplicationChecker {

  private static final Logger LOG = LoggerFactory.getLogger(ReplicationChecker.class);

  private final HBaseFsck.ErrorReporter errorReporter;

  private final UnDeletedQueueChecker unDeletedQueueChecker;

  // replicator with its undeleted queueIds for removed peers in hfile-refs queue
  private Set<String> undeletedHFileRefsPeerIds = Collections.emptySet();

  private final ReplicationPeerStorage peerStorage;
  private final ReplicationQueueStorage queueStorage;

  private UnDeletedQueueChecker initUnDeletedQueueChecker() {
    try {
      ReplicationQueueStorage.class.getMethod("listAllPeerIds");
      return new UnDeletedQueueChecker3();
    } catch (NoSuchMethodException e) {
      LOG.debug("No listAllPeerIds method, should be hbase 2", e);
      return new UnDeletedQueueChecker2();
    }
  }

  public ReplicationChecker(Configuration conf, ZKWatcher zkw, FileSystem fs, Connection conn,
    HBaseFsck.ErrorReporter errorReporter) {
    this.peerStorage = ReplicationStorageFactoryHelper.getReplicationPeerStorage(conf, zkw, fs);
    this.queueStorage = ReplicationStorageFactoryHelper.getReplicationQueueStorage(conf, zkw, conn);
    this.errorReporter = errorReporter;
    this.unDeletedQueueChecker = initUnDeletedQueueChecker();
  }

  public boolean hasUnDeletedQueues() {
    return errorReporter.getErrorList()
      .contains(HBaseFsck.ErrorReporter.ERROR_CODE.UNDELETED_REPLICATION_QUEUE);
  }

  private interface UnDeletedQueueChecker {

    void check() throws ReplicationException;

    void fix() throws ReplicationException;
  }

  private final class UnDeletedQueueChecker2 implements UnDeletedQueueChecker {

    private final Method getListOfReplicators;

    private final Method getAllQueues;

    private final Method removeQueue;

    private final Method removeReplicatorIfQueueIsEmpty;

    // replicator with its queueIds for removed peers
    private Map<ServerName, List<String>> undeletedQueueIds = Collections.emptyMap();

    UnDeletedQueueChecker2() {
      try {
        getListOfReplicators = ReplicationQueueStorage.class.getMethod("getListOfReplicators");
        getAllQueues = ReplicationQueueStorage.class.getMethod("getAllQueues", ServerName.class);
        removeQueue =
          ReplicationQueueStorage.class.getMethod("removeQueue", ServerName.class, String.class);
        removeReplicatorIfQueueIsEmpty = ReplicationQueueStorage.class
          .getMethod("removeReplicatorIfQueueIsEmpty", ServerName.class);
      } catch (NoSuchMethodException e) {
        throw new RuntimeException("method unavailable", e);
      }
    }

    private Map<ServerName, List<String>> getUnDeletedQueues() throws ReplicationException {
      Map<ServerName, List<String>> undeletedQueues = new HashMap<>();
      Set<String> peerIds = new HashSet<>(peerStorage.listPeerIds());
      try {
        for (ServerName replicator : (List<ServerName>) getListOfReplicators.invoke(queueStorage)) {
          for (String queueId : (List<String>) getAllQueues.invoke(queueStorage, replicator)) {
            ReplicationQueueInfo queueInfo = new ReplicationQueueInfo(queueId);
            if (!peerIds.contains(queueInfo.getPeerId())) {
              undeletedQueues.computeIfAbsent(replicator, key -> new ArrayList<>()).add(queueId);
              LOG.debug(
                "Undeleted replication queue for removed peer found: "
                  + "[removedPeerId={}, replicator={}, queueId={}]",
                queueInfo.getPeerId(), replicator, queueId);
            }
          }
        }
      } catch (InvocationTargetException e) {
        Throwables.throwIfInstanceOf(e.getCause(), ReplicationException.class);
        Throwables.throwIfUnchecked(e.getCause());
        throw new RuntimeException(e.getCause());
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
      return undeletedQueues;
    }

    @Override
    public void check() throws ReplicationException {
      undeletedQueueIds = getUnDeletedQueues();
      undeletedQueueIds.forEach((replicator, queueIds) -> {
        queueIds.forEach(queueId -> {
          ReplicationQueueInfo queueInfo = new ReplicationQueueInfo(queueId);
          String msg = "Undeleted replication queue for removed peer found: "
            + String.format("[removedPeerId=%s, replicator=%s, queueId=%s]", queueInfo.getPeerId(),
              replicator, queueId);
          errorReporter.reportError(HBaseFsck.ErrorReporter.ERROR_CODE.UNDELETED_REPLICATION_QUEUE,
            msg);
        });
      });
    }

    @Override
    public void fix() throws ReplicationException {
      try {
        for (Map.Entry<ServerName, List<String>> replicatorAndQueueIds : undeletedQueueIds
          .entrySet()) {
          ServerName replicator = replicatorAndQueueIds.getKey();
          for (String queueId : replicatorAndQueueIds.getValue()) {
            removeQueue.invoke(queueStorage, replicator, queueId);
          }
          removeReplicatorIfQueueIsEmpty.invoke(queueStorage, replicator);
        }
      } catch (InvocationTargetException e) {
        Throwables.throwIfInstanceOf(e.getCause(), ReplicationException.class);
        Throwables.throwIfUnchecked(e.getCause());
        throw new RuntimeException(e.getCause());
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private final class UnDeletedQueueChecker3 implements UnDeletedQueueChecker {

    private final Method listAllPeerIds;

    private final Method removeAllQueues;

    private List<String> unDeletedPeerIds = Collections.emptyList();

    UnDeletedQueueChecker3() {
      try {
        listAllPeerIds = ReplicationQueueStorage.class.getMethod("listAllPeerIds");
        removeAllQueues = ReplicationQueueStorage.class.getMethod("removeAllQueues", String.class);
      } catch (NoSuchMethodException e) {
        throw new RuntimeException("method unavailable", e);
      }
    }

    private List<String> getUnDeletedPeerIds() throws ReplicationException {
      List<String> unDeletedPeerIds = new ArrayList<>();
      try {
        Set<String> peerIds = new HashSet<>(peerStorage.listPeerIds());
        for (String peerId : (List<String>) listAllPeerIds.invoke(queueStorage)) {
          if (!peerIds.contains(peerId)) {
            unDeletedPeerIds.add(peerId);
          }
        }
      } catch (InvocationTargetException e) {
        Throwables.throwIfInstanceOf(e.getCause(), ReplicationException.class);
        Throwables.throwIfUnchecked(e.getCause());
        throw new RuntimeException(e.getCause());
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
      return unDeletedPeerIds;
    }

    @Override
    public void check() throws ReplicationException {
      unDeletedPeerIds = getUnDeletedPeerIds();
      unDeletedPeerIds.forEach(peerId -> {
        String msg = "Undeleted replication queue for removed peer found: "
          + String.format("[removedPeerId=%s]", peerId);
        errorReporter.reportError(HBaseFsck.ErrorReporter.ERROR_CODE.UNDELETED_REPLICATION_QUEUE,
          msg);
      });
    }

    @Override
    public void fix() throws ReplicationException {
      try {
        for (String peerId : unDeletedPeerIds) {
          removeAllQueues.invoke(queueStorage, peerId);
        }
      } catch (InvocationTargetException e) {
        Throwables.throwIfInstanceOf(e.getCause(), ReplicationException.class);
        Throwables.throwIfUnchecked(e.getCause());
        throw new RuntimeException(e.getCause());
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }

  }

  private Set<String> getUndeletedHFileRefsPeers() throws ReplicationException {

    Set<String> undeletedHFileRefsPeerIds =
      new HashSet<>(queueStorage.getAllPeersFromHFileRefsQueue());
    Set<String> peerIds = new HashSet<>(peerStorage.listPeerIds());
    undeletedHFileRefsPeerIds.removeAll(peerIds);
    if (LOG.isDebugEnabled()) {
      for (String peerId : undeletedHFileRefsPeerIds) {
        LOG.debug("Undeleted replication hfile-refs queue for removed peer {} found", peerId);
      }
    }
    return undeletedHFileRefsPeerIds;
  }

  private boolean hasData() throws ReplicationException {
    Method hasDataMethod;
    try {
      hasDataMethod = ReplicationQueueStorage.class.getMethod("hasData");
    } catch (NoSuchMethodException e) {
      LOG.debug("No hasData method, should be hbase 2", e);
      return true;
    }
    try {
      return (boolean) hasDataMethod.invoke(queueStorage);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      Throwables.throwIfInstanceOf(e.getCause(), ReplicationException.class);
      Throwables.throwIfUnchecked(e.getCause());
      throw new RuntimeException(e.getCause());
    }
  }

  public void checkUnDeletedQueues() throws ReplicationException {
    if (!hasData()) {
      return;
    }
    unDeletedQueueChecker.check();
    undeletedHFileRefsPeerIds = getUndeletedHFileRefsPeers();
    undeletedHFileRefsPeerIds.stream()
      .map(peerId -> "Undeleted replication hfile-refs queue for removed peer " + peerId + " found")
      .forEach(msg -> errorReporter
        .reportError(HBaseFsck.ErrorReporter.ERROR_CODE.UNDELETED_REPLICATION_QUEUE, msg));
  }

  public void fixUnDeletedQueues() throws ReplicationException {
    unDeletedQueueChecker.fix();
    for (String peerId : undeletedHFileRefsPeerIds) {
      queueStorage.removePeerFromHFileRefs(peerId);
    }
  }
}

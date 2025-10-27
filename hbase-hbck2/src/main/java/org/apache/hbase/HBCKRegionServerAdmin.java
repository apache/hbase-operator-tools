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

import static org.apache.hadoop.hbase.HConstants.DEFAULT_HBASE_RPC_TIMEOUT;
import static org.apache.hadoop.hbase.HConstants.HBASE_RPC_TIMEOUT_KEY;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.ipc.HBaseRpcController;
import org.apache.hadoop.hbase.ipc.RpcClient;
import org.apache.hadoop.hbase.ipc.RpcControllerFactory;
import org.apache.hadoop.hbase.security.User;
import org.apache.hadoop.hbase.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hbase.thirdparty.com.google.protobuf.ServiceException;

import org.apache.hadoop.hbase.shaded.protobuf.ProtobufUtil;
import org.apache.hadoop.hbase.shaded.protobuf.RequestConverter;
import org.apache.hadoop.hbase.shaded.protobuf.generated.AdminProtos.AdminService;
import org.apache.hadoop.hbase.shaded.protobuf.generated.AdminProtos.AdminService.BlockingInterface;
import org.apache.hadoop.hbase.shaded.protobuf.generated.AdminProtos.GetRegionInfoRequest;
import org.apache.hadoop.hbase.shaded.protobuf.generated.AdminProtos.GetRegionInfoResponse;
import org.apache.hadoop.hbase.shaded.protobuf.generated.HBaseProtos.RegionSpecifier.RegionSpecifierType;

/**
 * A helper class for doing some admin operations directly on region server
 */
public class HBCKRegionServerAdmin {

  private static final Logger LOG = LoggerFactory.getLogger(HBCKRegionServerAdmin.class);

  private final RpcControllerFactory rpcControllerFactory;

  private final AdminService.BlockingInterface stub;

  private final ServerName server;

  // for HBase 2.x
  private Pair<RpcControllerFactory, AdminService.BlockingInterface> init2(Connection conn,
    ServerName server) throws IOException {
    try {
      Class<?> clusterConnClazz = Class.forName("org.apache.hadoop.hbase.client.ClusterConnection");
      Method getRpcControllerFactoryMethod =
        clusterConnClazz.getDeclaredMethod("getRpcControllerFactory");
      getRpcControllerFactoryMethod.setAccessible(true);
      RpcControllerFactory rpcControllerFactory =
        (RpcControllerFactory) getRpcControllerFactoryMethod.invoke(conn);
      Method getAdminMethod = clusterConnClazz.getDeclaredMethod("getAdmin", ServerName.class);
      getAdminMethod.setAccessible(true);
      AdminService.BlockingInterface stub = (BlockingInterface) getAdminMethod.invoke(conn, server);
      return Pair.newPair(rpcControllerFactory, stub);
    } catch (ClassNotFoundException e) {
      LOG.debug("No ClusterConnection, should be HBase 3+", e);
      return null;
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new IOException(e);
    }
  }

  // for HBase 3+
  private Pair<RpcControllerFactory, AdminService.BlockingInterface> init3(Connection conn,
    ServerName server) throws IOException {
    try {
      Method toAsyncConnectionMethod = conn.getClass().getMethod("toAsyncConnection");
      toAsyncConnectionMethod.setAccessible(true);
      Object asyncConn = toAsyncConnectionMethod.invoke(conn);
      Field rpcControllerFactoryField =
        asyncConn.getClass().getDeclaredField("rpcControllerFactory");
      rpcControllerFactoryField.setAccessible(true);
      RpcControllerFactory rpcControllerFactory =
        (RpcControllerFactory) rpcControllerFactoryField.get(asyncConn);
      Field rpcClientField = asyncConn.getClass().getDeclaredField("rpcClient");
      rpcClientField.setAccessible(true);
      RpcClient rpcClient = (RpcClient) rpcClientField.get(asyncConn);
      Field userField = asyncConn.getClass().getDeclaredField("user");
      userField.setAccessible(true);
      User user = (User) userField.get(asyncConn);
      int rpcTimeoutMs =
        conn.getConfiguration().getInt(HBASE_RPC_TIMEOUT_KEY, DEFAULT_HBASE_RPC_TIMEOUT);
      AdminService.BlockingInterface stub = AdminService
        .newBlockingStub(rpcClient.createBlockingRpcChannel(server, user, rpcTimeoutMs));
      return Pair.newPair(rpcControllerFactory, stub);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException
      | NoSuchFieldException e) {
      throw new IOException(e);
    }
  }

  public HBCKRegionServerAdmin(Connection conn, ServerName server) throws IOException {
    this.server = server;
    Pair<RpcControllerFactory, AdminService.BlockingInterface> pair = init2(conn, server);
    if (pair == null) {
      pair = init3(conn, server);
    }
    rpcControllerFactory = pair.getFirst();
    stub = pair.getSecond();
  }

  public void closeRegion(byte[] regionName) throws IOException {
    HBaseRpcController hrc = rpcControllerFactory.newController();
    try {
      stub.closeRegion(hrc, ProtobufUtil.buildCloseRegionRequest(server, regionName));
    } catch (ServiceException e) {
      throw ProtobufUtil.getRemoteException(e);
    }
  }

  public RegionInfo getRegionInfo(byte[] regionName) throws IOException {
    HBaseRpcController hrc = rpcControllerFactory.newController();
    try {
      GetRegionInfoRequest request = RegionInfo.isEncodedRegionName(regionName)
        ? GetRegionInfoRequest.newBuilder()
          .setRegion(RequestConverter.buildRegionSpecifier(RegionSpecifierType.ENCODED_REGION_NAME,
            regionName))
          .build()
        : RequestConverter.buildGetRegionInfoRequest(regionName);
      GetRegionInfoResponse response = stub.getRegionInfo(hrc, request);
      return ProtobufUtil.toRegionInfo(response.getRegionInfo());
    } catch (ServiceException e) {
      throw ProtobufUtil.getRemoteException(e);
    }
  }
}

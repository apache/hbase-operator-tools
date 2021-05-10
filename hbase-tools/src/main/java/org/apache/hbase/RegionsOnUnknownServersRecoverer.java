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

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Set;

/**
 * Tool for identifying Unknown Servers from master logs and schedule SCPs for each of those using
 * HBCK2 'scheduleRecoveries' option. This is useful for clusters running hbase versions lower than
 * 2.2.7, 2.3.5 and 2.4.7. For any of these versions or higher, use HBCK2 'recoverUnknown' option.
 */
public class RegionsOnUnknownServersRecoverer extends Configured implements Tool {

  private static final Logger LOG =
    LoggerFactory.getLogger(RegionsOnUnknownServersRecoverer.class.getName());

  private static final String CATALOG_JANITOR = "CatalogJanitor: hole=";

  private static final String UNKNOWN_SERVER = "unknown_server=";

  private Configuration conf;

  private Set<String> unknownServers = new HashSet<>();

  public RegionsOnUnknownServersRecoverer(Configuration conf){
    this.conf = conf;
  }

  @Override
  public int run(String[] args) throws Exception {
    if(args.length!=1){
      LOG.error("Wrong number of arguments. "
        + "Arguments are: <PATH_TO_MASTER_LOGS>");
      return 1;
    }
    BufferedReader reader = null;
    try(Connection conn = ConnectionFactory.createConnection(conf)) {
      reader = new BufferedReader(new FileReader(new File(args[0])));
      String line = null;
      while((line = reader.readLine()) != null){
        if(line.contains(CATALOG_JANITOR)){
          String[] servers = line.split(UNKNOWN_SERVER);
          for(int i=1; i<servers.length; i++){
            String server = servers[i].split("/")[0];
            if(!unknownServers.contains(server)){
              LOG.info("Adding server {} to our list of servers that will have SCPs.", server);
              unknownServers.add(server);
            }
          }
        }
      }
      HBCK2 hbck2 = new HBCK2(conf);
      hbck2.scheduleRecoveries(conn.getHbck(), unknownServers.toArray(new String[]{}));
    } catch(Exception e){
      LOG.error("Recovering unknown servers failed:", e);
      return 2;
    } finally {
      reader.close();
    }
    return 0;
  }

  public static void main(String [] args) throws Exception {
    Configuration conf = HBaseConfiguration.create();
    int errCode = ToolRunner.run(new RegionsOnUnknownServersRecoverer(conf), args);
    if (errCode != 0) {
      System.exit(errCode);
    }
  }

}


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
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.yetus.audience.InterfaceAudience;

import java.io.IOException;

/**
 * hbck's local version of the CommonFSUtils from the hbase repo
 * A Utility class to facilitate hbck2's access to tables/namespace dir structures.
 */
@InterfaceAudience.Private
public final class HBCKFsUtils {
  /**
   * Returns the {@link org.apache.hadoop.fs.Path} object representing the table directory under
   * path rootdir
   *
   * COPIED from CommonFSUtils.getTableDir
   *
   * @param rootdir qualified path of HBase root directory
   * @param tableName name of table
   * @return {@link org.apache.hadoop.fs.Path} for table
   */
  public static Path getTableDir(Path rootdir, final TableName tableName) {
    return new Path(getNamespaceDir(rootdir, tableName.getNamespaceAsString()),
      tableName.getQualifierAsString());
  }

  /**
   * Returns the {@link org.apache.hadoop.fs.Path} object representing
   * the namespace directory under path rootdir
   *
   * COPIED from CommonFSUtils.getNamespaceDir
   *
   * @param rootdir qualified path of HBase root directory
   * @param namespace namespace name
   * @return {@link org.apache.hadoop.fs.Path} for table
   */
  public static Path getNamespaceDir(Path rootdir, final String namespace) {
    return new Path(rootdir, new Path(HConstants.BASE_NAMESPACE_DIR,
      new Path(namespace)));
  }

  /**
   *
   * COPIED from CommonFSUtils.getRootDir
   *
   * @param c configuration
   * @return {@link Path} to hbase root directory from
   *     configuration as a qualified Path.
   * @throws IOException e
   */
  public static Path getRootDir(final Configuration c) throws IOException {
    Path p = new Path(c.get(HConstants.HBASE_DIR));
    FileSystem fs = p.getFileSystem(c);
    return p.makeQualified(fs.getUri(), fs.getWorkingDirectory());
  }

}

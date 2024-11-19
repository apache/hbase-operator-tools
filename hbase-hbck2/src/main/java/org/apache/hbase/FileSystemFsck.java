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
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.util.CommonFSUtils;
import org.apache.hadoop.hbase.util.FSUtils;
import org.apache.hbase.hbck1.HBaseFsck;
import org.apache.hbase.hbck1.HFileCorruptionChecker;

/**
 * Checks and repairs for hbase filesystem.
 */
public class FileSystemFsck implements Closeable {
  private final Configuration configuration;
  private FileSystem fs;
  private Path rootDir;

  FileSystemFsck(Configuration conf) throws IOException {
    this.configuration = conf;
    this.rootDir = CommonFSUtils.getRootDir(this.configuration);
    this.fs = rootDir.getFileSystem(this.configuration);

  }

  @Override
  public void close() {
    // Nothing to do.
  }

  int fsck(List<String> tables, boolean fix) throws IOException {
    // Before we start make sure of the version file.
    if (fix && !HBaseFsck.versionFileExists(this.fs, this.rootDir)) {
      HBaseFsck.versionFileCreate(this.configuration, this.fs, this.rootDir);
    }
    // Iterate over list of tablenames or encoded region names passed.
    try (HBaseFsck hbaseFsck = new HBaseFsck(this.configuration)) {
      // Check hfiles.
      HFileCorruptionChecker hfcc = hbaseFsck.createHFileCorruptionChecker(fix);
      hbaseFsck.setHFileCorruptionChecker(hfcc);
      Collection<Path> tableDirs = tables.isEmpty()
        ? FSUtils.getTableDirs(this.fs, this.rootDir)
        : tables.stream().map(t -> CommonFSUtils.getTableDir(this.rootDir, TableName.valueOf(t)))
          .collect(Collectors.toList());
      hfcc.checkTables(tableDirs);
      hfcc.report(hbaseFsck.getErrors());
      // Now check links.
      hbaseFsck.setFixReferenceFiles(fix);
      hbaseFsck.setFixHFileLinks(fix);
      hbaseFsck.setCheckHdfs(true);
      /*
       * The below are too radical for hbck2. They are filesystem changes only. Need to connect them
       * to hbase:meta and master; master should repair holes and overlaps and adopt regions.
       * hbaseFsck.setFixHdfsOrphans(fix); hbaseFsck.setFixHdfsHoles(fix);
       * hbaseFsck.setFixHdfsOverlaps(fix); hbaseFsck.setFixTableOrphans(fix);
       */
      if (!tables.isEmpty()) {
        tables.stream().map(TableName::valueOf).forEach(hbaseFsck::includeTable);
      }
      if (!tableDirs.isEmpty()) {
        tableDirs.forEach(hbaseFsck::includeTableDir);
      }
      hbaseFsck.offlineHbck();
    } catch (ClassNotFoundException | InterruptedException e) {
      throw new IOException(e);
    }
    return 0;
  }
}

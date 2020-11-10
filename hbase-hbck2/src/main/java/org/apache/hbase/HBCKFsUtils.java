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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.yetus.audience.InterfaceAudience;


/**
 * hbck's local version of the CommonFSUtils from the hbase repo
 * A Utility class to facilitate hbck2's access to tables/namespace dir structures.
 */
@InterfaceAudience.Private
public final class HBCKFsUtils {

  /**
   * Private constructor to keep this class from being instantiated.
   */
  private HBCKFsUtils() {
  }

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

  /**
   * Copy all files/subdirectories from source path to destination path.
   *
   * COPIED from FSUtils.copyFilesParallel
   *
   * @param srcFS FileSystem instance for the source path
   * @param src source path
   * @param dstFS FileSystem instance for the destination path
   * @param dst destination path
   * @param conf a valid hbase configuration object
   * @param threads number of threads to execute the copy
   * @return list of Path representing all items residing int the source path
   * @throws IOException e
   */
  public static List<Path> copyFilesParallel(FileSystem srcFS, Path src, FileSystem dstFS, Path dst,
    Configuration conf, int threads) throws IOException {
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    List<Future<Void>> futures = new ArrayList<>();
    List<Path> traversedPaths;
    try {
      traversedPaths = copyFiles(srcFS, src, dstFS, dst, conf, pool, futures);
      for (Future<Void> future : futures) {
        future.get();
      }
    } catch (ExecutionException | InterruptedException | IOException e) {
      throw new IOException("Copy snapshot reference files failed", e);
    } finally {
      pool.shutdownNow();
    }
    return traversedPaths;
  }

  private static List<Path> copyFiles(FileSystem srcFS, Path src, FileSystem dstFS, Path dst,
    Configuration conf, ExecutorService pool, List<Future<Void>> futures) throws IOException {
    List<Path> traversedPaths = new ArrayList<>();
    traversedPaths.add(dst);
    FileStatus currentFileStatus = srcFS.getFileStatus(src);
    if (currentFileStatus.isDirectory()) {
      if (!dstFS.mkdirs(dst)) {
        throw new IOException("Create directory failed: " + dst);
      }
      FileStatus[] subPaths = srcFS.listStatus(src);
      for (FileStatus subPath : subPaths) {
        traversedPaths.addAll(copyFiles(srcFS, subPath.getPath(), dstFS,
          new Path(dst, subPath.getPath().getName()), conf, pool, futures));
      }
    } else {
      Future<Void> future = pool.submit(() -> {
        FileUtil.copy(srcFS, src, dstFS, dst, false, false, conf);
        return null;
      });
      futures.add(future);
    }
    return traversedPaths;
  }
}

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

import edu.umd.cs.findbugs.annotations.CheckForNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.yetus.audience.InterfaceAudience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hbase.thirdparty.com.google.common.collect.Iterators;

/**
 * hbck's local version of the CommonFSUtils from the hbase repo
 * A Utility class to facilitate hbck2's access to tables/namespace dir structures.
 */
@InterfaceAudience.Private
public final class HBCKFsUtils {

  private static final Logger LOG = LoggerFactory.getLogger(HBCKFsUtils.class);

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
  /*
   *
   * COPIED from CommonFSUtils.listStatus
   *
   * Calls fs.listStatus() and treats FileNotFoundException as non-fatal
   * This accommodates differences between hadoop versions, where hadoop 1
   * does not throw a FileNotFoundException, and return an empty FileStatus[]
   * while Hadoop 2 will throw FileNotFoundException.
   *
   * Where possible, prefer FSUtils#listStatusWithStatusFilter(FileSystem,
   * Path, FileStatusFilter) instead.
   *
   * @param fs file system
   * @param dir directory
   * @param filter path filter
   * @return null if dir is empty or doesn't exist, otherwise FileStatus array
   */
  public static FileStatus [] listStatus(final FileSystem fs,
    final Path dir, final PathFilter filter) throws IOException {
    FileStatus [] status = null;
    try {
      status = filter == null ? fs.listStatus(dir) : fs.listStatus(dir, filter);
    } catch (FileNotFoundException fnfe) {
      // if directory doesn't exist, return null
      if (LOG.isTraceEnabled()) {
        LOG.trace(dir + " doesn't exist");
      }
    }
    if (status == null || status.length < 1) {
      return null;
    }
    return status;
  }

  /**
   *
   * COPIED from CommonFSUtils.delete
   *
   * Calls fs.delete() and returns the value returned by the fs.delete()
   *
   * @param fs must not be null
   * @param path must not be null
   * @param recursive delete tree rooted at path
   * @return the value returned by the fs.delete()
   * @throws IOException from underlying FileSystem
   */
  public static boolean delete(final FileSystem fs, final Path path, final boolean recursive)
    throws IOException {
    return fs.delete(path, recursive);
  }

  /**
   *
   * COPIED from CommonFSUtils.deleteDirectory
   *
   * Delete if exists.
   * @param fs filesystem object
   * @param dir directory to delete
   * @return True if deleted <code>dir</code>
   * @throws IOException on IO errors
   */
  public static boolean deleteDirectory(final FileSystem fs, final Path dir)
    throws IOException {
    return fs.exists(dir) && fs.delete(dir, true);
  }

  /**
   *
   * COPIED from FsUtils.getRegionDirs
   *
   * Given a particular table dir, return all the regiondirs inside it, excluding files such as
   * .tableinfo
   * @param fs A file system for the Path
   * @param tableDir Path to a specific table directory &lt;hbase.rootdir&gt;/&lt;tabledir&gt;
   * @return List of paths to valid region directories in table dir.
   * @throws IOException on IO errors
   */
  public static List<Path> getRegionDirs(final FileSystem fs, final Path tableDir)
    throws IOException {
    // assumes we are in a table dir.
    List<FileStatus> rds = listStatusWithStatusFilter(fs, tableDir, new RegionDirFilter(fs));
    if (rds == null) {
      return new ArrayList<>();
    }
    List<Path> regionDirs = new ArrayList<>(rds.size());
    for (FileStatus rdfs: rds) {
      Path rdPath = rdfs.getPath();
      regionDirs.add(rdPath);
    }
    return regionDirs;
  }

  /**
   *
   * COPIED from FsUtils.listStatusWithStatusFilter
   *
   * Calls fs.listStatus() and treats FileNotFoundException as non-fatal
   * This accommodates differences between hadoop versions, where hadoop 1
   * does not throw a FileNotFoundException, and return an empty FileStatus[]
   * while Hadoop 2 will throw FileNotFoundException.
   *
   * @param fs file system
   * @param dir directory
   * @param filter file status filter
   * @return null if dir is empty or doesn't exist, otherwise FileStatus list
   */
  public static List<FileStatus> listStatusWithStatusFilter(final FileSystem fs, final Path dir,
    final HBCKFileStatusFilter filter) throws IOException {
    FileStatus [] status = null;
    try {
      status = fs.listStatus(dir);
    } catch (FileNotFoundException fnfe) {
      // if directory doesn't exist, return null
      if (LOG.isTraceEnabled()) {
        LOG.trace(dir + " doesn't exist");
      }
    }

    if (status == null || status.length < 1)  {
      return null;
    }

    if (filter == null) {
      return Arrays.asList(status);
    } else {
      List<FileStatus> status2 = filterFileStatuses(status, filter);
      if (status2 == null || status2.isEmpty()) {
        return null;
      } else {
        return status2;
      }
    }
  }

  /**
   *
   * COPIED from FsUtils.filterFileStatuses
   *
   * Filters FileStatuses in an array and returns a list
   *
   * @param input   An array of FileStatuses
   * @param filter  A required filter to filter the array
   * @return        A list of FileStatuses
   */
  public static List<FileStatus> filterFileStatuses(FileStatus[] input,
                                                    HBCKFileStatusFilter filter) {
    if (input == null) {
      return null;
    }
    return filterFileStatuses(Iterators.forArray(input), filter);
  }

  /**
   *
   * COPIED from FsUtils.filterFileStatuses
   *
   * Filters FileStatuses in an iterator and returns a list
   *
   * @param input   An iterator of FileStatuses
   * @param filter  A required filter to filter the array
   * @return        A list of FileStatuses
   */
  public static List<FileStatus> filterFileStatuses(Iterator<FileStatus> input,
                                                    HBCKFileStatusFilter filter) {
    if (input == null) {
      return null;
    }
    ArrayList<FileStatus> results = new ArrayList<>();
    while (input.hasNext()) {
      FileStatus f = input.next();
      if (filter.accept(f)) {
        results.add(f);
      }
    }
    return results;
  }


  /**
   *
   * COPIED from FsUtils.FamilyDirFilter
   *
   * Filter for all dirs that are legal column family names.  This is generally used for colfam
   * dirs &lt;hbase.rootdir&gt;/&lt;tabledir&gt;/&lt;regiondir&gt;/&lt;colfamdir&gt;.
   */
  public static class FamilyDirFilter extends HBCKAbstractFileStatusFilter {
    final FileSystem fs;

    public FamilyDirFilter(FileSystem fs) {
      this.fs = fs;
    }

    @Override
    protected boolean accept(Path p, @CheckForNull Boolean isDir) {
      try {
        // throws IAE if invalid
        ColumnFamilyDescriptorBuilder.isLegalColumnFamilyName(Bytes.toBytes(p.getName()));
      } catch (IllegalArgumentException iae) {
        // path name is an invalid family name and thus is excluded.
        return false;
      }

      try {
        return isDirectory(fs, isDir, p);
      } catch (IOException ioe) {
        // Maybe the file was moved or the fs was disconnected.
        LOG.warn("Skipping file " + p +" due to IOException", ioe);
        return false;
      }
    }
  }

  /**
   *
   * COPIED from FsUtils.RegionDirFilter
   *
   * Filter for all dirs that don't start with '.'
   */
  public static class RegionDirFilter extends HBCKAbstractFileStatusFilter {
    // This pattern will accept 0.90+ style hex region dirs and older numeric region dir names.
    final public static Pattern regionDirPattern = Pattern.compile("^[0-9a-f]*$");
    final FileSystem fs;

    public RegionDirFilter(FileSystem fs) {
      this.fs = fs;
    }

    @Override
    protected boolean accept(Path p, @CheckForNull Boolean isDir) {
      if (!regionDirPattern.matcher(p.getName()).matches()) {
        return false;
      }

      try {
        return isDirectory(fs, isDir, p);
      } catch (IOException ioe) {
        // Maybe the file was moved or the fs was disconnected.
        LOG.warn("Skipping file " + p +" due to IOException", ioe);
        return false;
      }
    }
  }

}

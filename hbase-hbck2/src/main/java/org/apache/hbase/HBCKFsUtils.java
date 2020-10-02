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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.io.HFileLink;
import org.apache.hadoop.hbase.regionserver.StoreFileInfo;
import org.apache.hadoop.hbase.shaded.protobuf.ProtobufUtil;
import org.apache.hadoop.hbase.shaded.protobuf.generated.FSProtos;
import org.apache.hadoop.hbase.util.AbstractFileStatusFilter;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.FSUtils;
import org.apache.hadoop.hbase.util.FileStatusFilter;
import org.apache.hbase.thirdparty.com.google.common.collect.Iterators;
import org.apache.yetus.audience.InterfaceAudience;


/**
 * hbck's local version of the CommonFSUtils from the hbase repo
 * A Utility class to facilitate hbck2's access to tables/namespace dir structures.
 */
@InterfaceAudience.Private
public final class HBCKFsUtils {


  /** Full access permissions (starting point for a umask) */
  public static final String FULL_RWX_PERMISSIONS = "777";
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
   * COPIED from CommonFSUtils.getCurrentFileSystem
   *
   * @param conf must not be null
   * @return Returns the filesystem of the hbase rootdir.
   * @throws IOException from underlying FileSystem
   */
  public static FileSystem getCurrentFileSystem(Configuration conf) throws IOException {
    return getRootDir(conf).getFileSystem(conf);
  }


  /**
   *
   * COPIED from FSUtils.getTableDirs
   *
   */
  public static List<Path> getTableDirs(final FileSystem fs, final Path rootdir)
    throws IOException {
    List<Path> tableDirs = new ArrayList<>();

    for (FileStatus status : fs
      .globStatus(new Path(rootdir, new Path(HConstants.BASE_NAMESPACE_DIR, "*")))) {
      tableDirs.addAll(FSUtils.getLocalTableDirs(fs, status.getPath()));
    }
    return tableDirs;
  }

  public static TableName getTableName(Path tableDir){
    return TableName.valueOf(tableDir.getParent().getName(), tableDir.getName());
  }

  /**
   * COPIED from FSUtils.
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
  public static List<FileStatus> listStatusWithStatusFilter(final FileSystem fs,
    final Path dir, final FileStatusFilter filter) throws IOException {
    FileStatus [] status = null;
    try {
      status = fs.listStatus(dir);
    } catch (FileNotFoundException fnfe) {
      return null;
    }

    if (ArrayUtils.getLength(status) == 0)  {
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
   * COPIED from FSUtils.
   *
   * Sets version of file system
   *
   * @param fs filesystem object
   * @param rootdir hbase root directory
   * @param version version to set
   * @param wait time to wait for retry
   * @param retries number of times to retry before throwing an IOException
   * @throws IOException e
   */
  public static void setVersion(FileSystem fs, Path rootdir, String version,
    int wait, int retries) throws IOException {
    Path versionFile = new Path(rootdir, HConstants.VERSION_FILE_NAME);
    Path tempVersionFile = new Path(rootdir, HConstants.HBASE_TEMP_DIRECTORY + Path.SEPARATOR +
      HConstants.VERSION_FILE_NAME);
    while (true) {
      try {
        // Write the version to a temporary file
        FSDataOutputStream s = fs.create(tempVersionFile);
        try {
          s.write(toVersionByteArray(version));
          s.close();
          s = null;
          // Move the temp version file to its normal location. Returns false
          // if the rename failed. Throw an IOE in that case.
          if (!fs.rename(tempVersionFile, versionFile)) {
            throw new IOException("Unable to move temp version file to " + versionFile);
          }
        } finally {
          // Cleaning up the temporary if the rename failed would be trying
          // too hard. We'll unconditionally create it again the next time
          // through anyway, files are overwritten by default by create().

          // Attempt to close the stream on the way out if it is still open.
          try {
            if (s != null) s.close();
          } catch (IOException ignore) { }
        }
        return;
      } catch (IOException e) {
        if (retries > 0) {
          fs.delete(versionFile, false);
          try {
            if (wait > 0) {
              Thread.sleep(wait);
            }
          } catch (InterruptedException ie) {
            throw (InterruptedIOException)new InterruptedIOException().initCause(ie);
          }
          retries--;
        } else {
          throw e;
        }
      }
    }
  }

  /**
   * COPIED from FSUtils.
   *
   * Get the file permissions specified in the configuration, if they are
   * enabled.
   *
   * @param fs filesystem that the file will be created on.
   * @param conf configuration to read for determining if permissions are
   *          enabled and which to use
   * @param permssionConfKey property key in the configuration to use when
   *          finding the permission
   * @return the permission to use when creating a new file on the fs. If
   *         special permissions are not specified in the configuration, then
   *         the default permissions on the the fs will be returned.
   */
  public static FsPermission getFilePermissions(final FileSystem fs,
    final Configuration conf, final String permssionConfKey) {
    boolean enablePermissions = conf.getBoolean(
      HConstants.ENABLE_DATA_FILE_UMASK, false);

    if (enablePermissions) {
      try {
        FsPermission perm = new FsPermission(FULL_RWX_PERMISSIONS);
        // make sure that we have a mask, if not, go default.
        String mask = conf.get(permssionConfKey);
        if (mask == null) {
          return FsPermission.getFileDefault();
        }
        // appy the umask
        FsPermission umask = new FsPermission(mask);
        return perm.applyUMask(umask);
      } catch (IllegalArgumentException e) {
        return FsPermission.getFileDefault();
      }
    }
    return FsPermission.getFileDefault();
  }

  /**
   * COPIED from FSUtils.
   *
   *
   * @param fs
   * @param familyDir
   * @return
   * @throws IOException
   */
  public static List<Path> getReferenceFilePaths(final FileSystem fs, final Path familyDir) throws IOException {
    List<FileStatus> fds = listStatusWithStatusFilter(fs, familyDir, new FSUtils.ReferenceFileFilter(fs));
    if (fds == null) {
      return Collections.emptyList();
    }
    List<Path> referenceFiles = new ArrayList<>(fds.size());
    for (FileStatus fdfs: fds) {
      Path fdPath = fdfs.getPath();
      referenceFiles.add(fdPath);
    }
    return referenceFiles;
  }


  /**
   * COPIED from FSUtils.
   *
   * Given a particular region dir, return all the familydirs inside it
   *
   * @param fs A file system for the Path
   * @param regionDir Path to a specific region directory
   * @return List of paths to valid family directories in region dir.
   * @throws IOException
   */
  public static List<Path> getFamilyDirs(final FileSystem fs, final Path regionDir) throws IOException {
    // assumes we are in a region dir.
    FileStatus[] fds = fs.listStatus(regionDir, new FSUtils.FamilyDirFilter(fs));
    List<Path> familyDirs = new ArrayList<>(fds.length);
    for (FileStatus fdfs: fds) {
      Path fdPath = fdfs.getPath();
      familyDirs.add(fdPath);
    }
    return familyDirs;
  }

  /**
   * COPIED from FSUtils.
   *
   * Create the content to write into the ${HBASE_ROOTDIR}/hbase.version file.
   * @param version Version to persist
   * @return Serialized protobuf with <code>version</code> content and a bit of pb magic for a prefix.
   */
  static byte [] toVersionByteArray(final String version) {
    FSProtos.HBaseVersionFileContent.Builder builder =
      FSProtos.HBaseVersionFileContent.newBuilder();
    return ProtobufUtil.prependPBMagic(builder.setVersion(version).build().toByteArray());
  }

  /**
   * COPIED from FSUtils.
   *
   * Filter for all dirs that don't start with '.'
   */
  public static class RegionDirFilter extends AbstractFileStatusFilter {
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
        return false;
      }
    }
  }

  /**
   * COPIED from FSUtils.
   *
   * Filters FileStatuses in an array and returns a list
   *
   * @param input   An array of FileStatuses
   * @param filter  A required filter to filter the array
   * @return        A list of FileStatuses
   */
  public static List<FileStatus> filterFileStatuses(FileStatus[] input,
    FileStatusFilter filter) {
    if (input == null) return null;
    return filterFileStatuses(Iterators.forArray(input), filter);
  }

  /**
   * COPIED from FSUtils.
   *
   * Filters FileStatuses in an iterator and returns a list
   *
   * @param input   An iterator of FileStatuses
   * @param filter  A required filter to filter the array
   * @return        A list of FileStatuses
   */
  public static List<FileStatus> filterFileStatuses(Iterator<FileStatus> input,
    FileStatusFilter filter) {
    if (input == null) return null;
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
   * COPIED from FSUtils.
   *
   * A FileStatusFilter for filtering links.
   */
  public static class ReferenceFileFilter extends AbstractFileStatusFilter {

    private final FileSystem fs;

    public ReferenceFileFilter(FileSystem fs) {
      this.fs = fs;
    }

    @Override
    protected boolean accept(Path p, @CheckForNull Boolean isDir) {
      if (!StoreFileInfo.isReference(p)) {
        return false;
      }

      try {
        // only files can be references.
        return isFile(fs, isDir, p);
      } catch (IOException ioe) {
        // Maybe the file was moved or the fs was disconnected.
        return false;
      }
    }
  }

  /**
   * COPIED from FSUtils.
   *
   * Filter for HFileLinks (StoreFiles and HFiles not included).
   * the filter itself does not consider if a link is file or not.
   */
  public static class HFileLinkFilter implements PathFilter {

    @Override
    public boolean accept(Path p) {
      return HFileLink.isHFileLink(p);
    }
  }

  /**
   * COPIED from FSUtils.
   *
   * Filter for all dirs that are legal column family names.  This is generally used for colfam
   * dirs &lt;hbase.rootdir&gt;/&lt;tabledir&gt;/&lt;regiondir&gt;/&lt;colfamdir&gt;.
   */
  public static class FamilyDirFilter extends AbstractFileStatusFilter {
    final FileSystem fs;

    public FamilyDirFilter(FileSystem fs) {
      this.fs = fs;
    }

    @Override
    protected boolean accept(Path p, @CheckForNull Boolean isDir) {
      try {
        // throws IAE if invalid
        HColumnDescriptor.isLegalFamilyName(Bytes.toBytes(p.getName()));
      } catch (IllegalArgumentException iae) {
        // path name is an invalid family name and thus is excluded.
        return false;
      }

      try {
        return isDirectory(fs, isDir, p);
      } catch (IOException ioe) {
        // Maybe the file was moved or the fs was disconnected.
        return false;
      }
    }
  }
}



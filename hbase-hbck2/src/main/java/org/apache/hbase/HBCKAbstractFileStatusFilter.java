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
import java.io.IOException;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.yetus.audience.InterfaceAudience;
import org.apache.yetus.audience.InterfaceStability;

/**
 * COPIED from org.apache.hadoop.hbase.util.AbstractFileStatusFilter because the original class was
 * tagged with @InterfaceAudience.Private. Typical base class for file status filter. Works more
 * efficiently when filtering file statuses, otherwise implementation will need to lookup filestatus
 * for the path which will be expensive.
 */
@InterfaceAudience.Private
@InterfaceStability.Evolving
public abstract class HBCKAbstractFileStatusFilter implements PathFilter, HBCKFileStatusFilter {

  /**
   * Filters out a path. Can be given an optional directory hint to avoid filestatus lookup.
   * @param p     A filesystem path
   * @param isDir An optional boolean indicating whether the path is a directory or not
   * @return true if the path is accepted, false if the path is filtered out
   */
  protected abstract boolean accept(Path p, @CheckForNull Boolean isDir);

  @Override
  public boolean accept(FileStatus f) {
    return accept(f.getPath(), f.isDirectory());
  }

  @Override
  public boolean accept(Path p) {
    return accept(p, null);
  }

  protected boolean isFile(FileSystem fs, @CheckForNull Boolean isDir, Path p) throws IOException {
    return !isDirectory(fs, isDir, p);
  }

  protected boolean isDirectory(FileSystem fs, @CheckForNull Boolean isDir, Path p)
    throws IOException {
    return isDir != null ? isDir : fs.isDirectory(p);
  }
}

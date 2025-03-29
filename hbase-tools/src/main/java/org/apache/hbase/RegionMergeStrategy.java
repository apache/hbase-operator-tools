package org.apache.hbase;

import org.apache.hadoop.hbase.client.RegionInfo;

/**
 * Strategy interface for deciding if two regions can be merged.
 */
public interface RegionMergeStrategy {
    /**
     * Determines whether the two regions can be merged.
     *
     * @param region1 the first region
     * @param region2 the second region
     * @return true if mergeable; false otherwise.
     * @throws Exception if any error occurs during evaluation.
     */
    boolean canMerge(RegionInfo region1, RegionInfo region2) throws Exception;
}

package org.apache.hbase;

import org.apache.hadoop.hbase.client.RegionInfo;

/**
 * Strategy that prevents merging when a region is split.
 */
public class SkipSplitRegionStrategy implements RegionMergeStrategy {
    @Override
    public boolean canMerge(RegionInfo region1, RegionInfo region2) {
        // Always return false to prevent merging if any region is split.
        return false;
    }
}

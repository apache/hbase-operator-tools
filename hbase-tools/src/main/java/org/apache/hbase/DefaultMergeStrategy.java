package org.apache.hbase;

import java.util.Set;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.client.RegionInfo;

/**
 * Default strategy for merging regions.
 * Merges if neither region is split and if the combined region size is acceptable.
 */
public class DefaultMergeStrategy implements RegionMergeStrategy {
    private final RegionsMerger merger;
    private final Path tableDir;
    private final Set<RegionInfo> mergingRegions;

    public DefaultMergeStrategy(RegionsMerger merger, Path tableDir, Set<RegionInfo> mergingRegions) {
        this.merger = merger;
        this.tableDir = tableDir;
        this.mergingRegions = mergingRegions;
    }

    @Override
    public boolean canMerge(RegionInfo region1, RegionInfo region2) throws Exception {
        // Only merge if both regions are not split and the RegionsMerger's logic approves.
        return !region1.isSplit() && !region2.isSplit() && merger.canMerge(tableDir, region1, region2, mergingRegions);
    }
}

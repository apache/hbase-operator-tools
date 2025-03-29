package org.apache.hbase;

import org.apache.hadoop.hbase.client.TableState;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.apache.hbase.HBCK2.*;

public class HBCK2CommandUsage {

    public static void usageAddFsRegionsMissingInMeta(PrintWriter writer) {
        writer.println(" " + ADD_MISSING_REGIONS_IN_META_FOR_TABLES + " [OPTIONS]");
        writer.println("      [<NAMESPACE|NAMESPACE:TABLENAME>...|-i <INPUTFILES>...]");
        writer.println("   Options:");
        writer.println("    -i,--inputFiles  take one or more files of namespace or table names");
        writer.println("    -o,--outputFile  name/prefix of the file(s) to dump region names");
        writer.println("    -n,--numLines  number of lines to be written to each output file");
        writer.println("   To be used when regions missing from hbase:meta but directories");
        writer.println("   are present still in HDFS. Can happen if user has run _hbck1_");
        writer.println("   'OfflineMetaRepair' against an hbase-2.x cluster. Needs hbase:meta");
        writer.println("   to be online. For each table name passed as parameter, performs diff");
        writer.println("   between regions available in hbase:meta and region dirs on HDFS.");
        writer.println("   Then for dirs with no hbase:meta matches, it reads the 'regioninfo'");
        writer.println("   metadata file and re-creates given region in hbase:meta. Regions are");
        writer.println("   re-created in 'CLOSED' state in the hbase:meta table, but not in the");
        writer.println("   Masters' cache, and they are not assigned either. To get these");
        writer.println("   regions online, run the HBCK2 'assigns'command printed when this");
        writer.println("   command-run completes.");
        writer.println("   NOTE: If using hbase releases older than 2.3.0, a rolling restart of");
        writer.println("   HMasters is needed prior to executing the set of 'assigns' output.");
        writer.println("   An example adding missing regions for tables 'tbl_1' in the default");
        writer.println("   namespace, 'tbl_2' in namespace 'n1' and for all tables from");
        writer.println("   namespace 'n2':");
        writer.println(
                "     $ HBCK2 " + ADD_MISSING_REGIONS_IN_META_FOR_TABLES + " default:tbl_1 n1:tbl_2 n2");
        writer.println("   Returns HBCK2  an 'assigns' command with all re-inserted regions.");
        writer.println("   SEE ALSO: " + REPORT_MISSING_REGIONS_IN_META);
        writer.println("   SEE ALSO: " + FIX_META);
        writer.println("   If -i or --inputFiles is specified, pass one or more input file names.");
        writer.println("   Each file contains <NAMESPACE|NAMESPACE:TABLENAME>, one per line.");
        writer.println("   For example:");
        writer.println(
                "     $ HBCK2 " + ADD_MISSING_REGIONS_IN_META_FOR_TABLES + " -i fileName1 fileName2");
        writer.println("   If -o or --outputFile is specified, the output file(s) can be passed as");
        writer.println("    input to assigns command via -i or -inputFiles option.");
        writer.println("   If -n or --numLines is specified, and say it is  set to 100, this will");
        writer.println("   create files with prefix as value passed by -o or --outputFile option.");
        writer.println("   Each file will have 100 region names (max.), one per line.");
        writer.println("   For example:");
        writer.println(
                "     $ HBCK2 " + ADD_MISSING_REGIONS_IN_META_FOR_TABLES + " -o outputFilePrefix -n 100");
        writer.println("     -i fileName1 fileName2");
        writer.println("   But if -n is not specified, but -o is specified, it will dump all");
        writer.println("   region names in a single file, one per line.");
        writer.println("   NOTE: -n option is applicable only if -o option is specified.");
    }

    public static void usageAssigns(PrintWriter writer) {
        writer.println(" " + ASSIGNS + " [OPTIONS] [<ENCODED_REGIONNAME>...|-i <INPUT_FILE>...]");
        writer.println("   Options:");
        writer.println("    -o,--override  override ownership by another procedure");
        writer.println("    -i,--inputFiles  take one or more files of encoded region names");
        writer.println("    -b,--batchSize   number of regions to process in a batch");
        writer.println("   A 'raw' assign that can be used even during Master initialization (if");
        writer.println("   the -skip flag is specified). Skirts Coprocessors. Pass one or more");
        writer.println("   encoded region names. 1588230740 is the hard-coded name for the");
        writer.println("   hbase:meta region and de00010733901a05f5a2a3a382e27dd4 is an example of");
        writer.println("   what a user-space encoded region name looks like. For example:");
        writer.println("     $ HBCK2 " + ASSIGNS + " 1588230740 de00010733901a05f5a2a3a382e27dd4");
        writer.println("   Returns the pid(s) of the created AssignProcedure(s) or -1 if none.");
        writer.println("   If -i or --inputFiles is specified, pass one or more input file names.");
        writer.println("   Each file contains encoded region names, one per line. For example:");
        writer.println("     $ HBCK2 " + ASSIGNS + " -i fileName1 fileName2");
        writer.println("   If -b or --batchSize is specified, the command processes those many");
        writer.println("   regions at a time in a batch-ed manner; Consider using this option,");
        writer.println("   if the list of regions is huge, to avoid CallTimeoutException.");
        writer.println("   For example:");
        writer.println("     $ HBCK2 " + ASSIGNS + " -i fileName1 fileName2 -b 500");
        writer.println("   By default, batchSize is set to -1 i.e. no batching is done.");
    }

    public static void usageBypass(PrintWriter writer) {
        writer.println(" " + BYPASS + " [OPTIONS] [<PID>...|-i <INPUT_FILE>...]");
        writer.println("   Options:");
        writer.println("    -o,--override   override if procedure is running/stuck");
        writer.println("    -r,--recursive  bypass parent and its children. SLOW! EXPENSIVE!");
        writer.println("    -w,--lockWait   milliseconds to wait before giving up; default=1");
        writer.println("    -i,--inputFiles  take one or more input files of PID's");
        writer.println("    -b,--batchSize   number of procedures to process in a batch");
        writer.println("   Pass one (or more) procedure 'pid's to skip to procedure finish. Parent");
        writer.println("   of bypassed procedure will also be skipped to the finish. Entities will");
        writer.println("   be left in an inconsistent state and will require manual fixup. May");
        writer.println("   need Master restart to clear locks still held. Bypass fails if");
        writer.println("   procedure has children. Add 'recursive' if all you have is a parent pid");
        writer.println("   to finish parent and children. This is SLOW, and dangerous so use");
        writer.println("   selectively. Does not always work.");
        writer.println("   If -i or --inputFiles is specified, pass one or more input file names.");
        writer.println("   Each file contains PID's, one per line. For example:");
        writer.println("     $ HBCK2 " + BYPASS + " -i fileName1 fileName2");
        writer.println("   If -b or --batchSize is specified, the command processes those many");
        writer.println("   procedures at a time in a batch-ed manner; Consider using this option,");
        writer.println("   if the list of procedures is huge, to avoid CallTimeoutException.");
        writer.println("   For example:");
        writer.println("     $ HBCK2 " + BYPASS + " -i fileName1 fileName2 -b 500");
        writer.println("   By default, batchSize is set to -1 i.e. no batching is done.");
    }

    public static void usageFilesystem(PrintWriter writer) {
        writer.println(" " + FILESYSTEM + " [OPTIONS] [<TABLENAME>...|-i <INPUT_FILE>...]");
        writer.println("   Options:");
        writer.println("    -f, --fix    sideline corrupt hfiles, bad links, and references.");
        writer.println("    -i,--inputFiles  take one or more input files of table names");
        writer.println("   Report on corrupt hfiles, references, broken links, and integrity.");
        writer.println("   Pass '--fix' to sideline corrupt files and links. '--fix' does NOT");
        writer.println("   fix integrity issues; i.e. 'holes' or 'orphan' regions. Pass one or");
        writer.println("   more tablenames to narrow checkup. Default checks all tables and");
        writer.println("   restores 'hbase.version' if missing. Interacts with the filesystem");
        writer.println("   only! Modified regions need to be reopened to pick-up changes.");
        writer.println("   If -i or --inputFiles is specified, pass one or more input file names.");
        writer.println("   Each file contains table names, one per line. For example:");
        writer.println("     $ HBCK2 " + FILESYSTEM + " -i fileName1 fileName2");
    }

    public static void usageFixMeta(PrintWriter writer) {
        writer.println(" " + FIX_META);
        writer.println("   Do a server-side fix of bad or inconsistent state in hbase:meta.");
        writer.println("   Available in hbase 2.2.1/2.1.6 or newer versions. Master UI has");
        writer.println("   matching, new 'HBCK Report' tab that dumps reports generated by");
        writer.println("   most recent run of _catalogjanitor_ and a new 'HBCK Chore'. It");
        writer.println("   is critical that hbase:meta first be made healthy before making");
        writer.println("   any other repairs. Fixes 'holes', 'overlaps', etc., creating");
        writer.println("   (empty) region directories in HDFS to match regions added to");
        writer.println("   hbase:meta. Command is NOT the same as the old _hbck1_ command");
        writer.println("   named similarily. Works against the reports generated by the last");
        writer.println("   catalog_janitor and hbck chore runs. If nothing to fix, run is a");
        writer.println("   noop. Otherwise, if 'HBCK Report' UI reports problems, a run of");
        writer.println("   " + FIX_META + " will clear up hbase:meta issues. See 'HBase HBCK' UI");
        writer.println("   for how to generate new report.");
        writer.println("   SEE ALSO: " + REPORT_MISSING_REGIONS_IN_META);
    }

    public static void usageGenerateMissingTableInfo(PrintWriter writer) {
        writer.println(" " + GENERATE_TABLE_INFO + " [OPTIONS] [<TABLENAME>...]");
        writer.println("   Trying to fix an orphan table by generating a missing table descriptor");
        writer.println("   file. This command will have no effect if the table folder is missing");
        writer.println("   or if the .tableinfo is present (we don't override existing table");
        writer.println("   descriptors). This command will first check it the TableDescriptor is");
        writer.println("   cached in HBase Master in which case it will recover the .tableinfo");
        writer.println("   accordingly. If TableDescriptor is not cached in master then it will");
        writer.println("   create a default .tableinfo file with the following items:");
        writer.println("     - the table name");
        writer.println("     - the column family list determined based on the file system");
        writer.println("     - the default properties for both TableDescriptor and");
        writer.println("       ColumnFamilyDescriptors");
        writer.println("   If the .tableinfo file was generated using default parameters then");
        writer.println("   make sure you check the table / column family properties later (and");
        writer.println("   change them if needed).");
        writer.println("   This method does not change anything in HBase, only writes the new");
        writer.println("   .tableinfo file to the file system. Orphan tables can cause e.g.");
        writer.println("   ServerCrashProcedures to stuck, you might need to fix these still");
        writer.println("   after you generated the missing table info files. If no tables are ");
        writer.println("   specified, .tableinfo will be generated for all missing table ");
        writer.println("   descriptors.");
    }

    public static void usageReplication(PrintWriter writer) {
        writer.println(" " + REPLICATION + " [OPTIONS] [<TABLENAME>...|-i <INPUT_FILE>...]");
        writer.println("   Options:");
        writer.println("    -f, --fix    fix any replication issues found.");
        writer.println("    -i,--inputFiles  take one or more input files of table names");
        writer.println("   Looks for undeleted replication queues and deletes them if passed the");
        writer.println("   '--fix' option. Pass a table name to check for replication barrier and");
        writer.println("   purge if '--fix'.");
        writer.println("   If -i or --inputFiles is specified, pass one or more input file names.");
        writer.println("   Each file contains <TABLENAME>, one per line. For example:");
        writer.println("     $ HBCK2 " + REPLICATION + " -i fileName1 fileName2");
    }

    public static void usageExtraRegionsInMeta(PrintWriter writer) {
        writer.println(" " + EXTRA_REGIONS_IN_META + " [<NAMESPACE|NAMESPACE:TABLENAME>...|");
        writer.println("      -i <INPUT_FILE>...]");
        writer.println("   Options:");
        writer.println("    -f, --fix    fix meta by removing all extra regions found.");
        writer.println("    -i,--inputFiles  take one or more input files of namespace or");
        writer.println("   table names");
        writer.println("   Reports regions present on hbase:meta, but with no related ");
        writer.println("   directories on the file system. Needs hbase:meta to be online. ");
        writer.println("   For each table name passed as parameter, performs diff");
        writer.println("   between regions available in hbase:meta and region dirs on the given");
        writer.println("   file system. Extra regions would get deleted from Meta ");
        writer.println("   if passed the --fix option. ");
        writer.println("   NOTE: Before deciding on use the \"--fix\" option, it's worth check if");
        writer.println("   reported extra regions are overlapping with existing valid regions.");
        writer.println("   If so, then \"extraRegionsInMeta --fix\" is indeed the optimal solution. ");
        writer.println("   Otherwise, \"assigns\" command is the simpler solution, as it recreates ");
        writer.println("   regions dirs in the filesystem, if not existing.");
        writer.println("   An example triggering extra regions report for tables 'table_1'");
        writer.println("   and 'table_2', under default namespace:");
        writer.println("     $ HBCK2 " + EXTRA_REGIONS_IN_META + " default:table_1 default:table_2");
        writer.println("   An example triggering extra regions report for table 'table_1'");
        writer.println("   under default namespace, and for all tables from namespace 'ns1':");
        writer.println("     $ HBCK2 " + EXTRA_REGIONS_IN_META + " default:table_1 ns1");
        writer.println("   Returns list of extra regions for each table passed as parameter, or");
        writer.println("   for each table on namespaces specified as parameter.");
        writer.println("   If -i or --inputFiles is specified, pass one or more input file names.");
        writer.println("   Each file contains <NAMESPACE|NAMESPACE:TABLENAME>, one per line.");
        writer.println("   For example:");
        writer.println("     $ HBCK2 " + EXTRA_REGIONS_IN_META + " -i fileName1 fileName2");
    }

    public static void usageReportMissingRegionsInMeta(PrintWriter writer) {
        writer.println(" " + REPORT_MISSING_REGIONS_IN_META + " [<NAMESPACE|NAMESPACE:TABLENAME>...|");
        writer.println("      -i <INPUT_FILE>...]");
        writer.println("   Options:");
        writer.println("    -i,--inputFiles  take one or more files of namespace or table names");
        writer.println("   To be used when regions missing from hbase:meta but directories");
        writer.println("   are present still in HDFS. Can happen if user has run _hbck1_");
        writer.println("   'OfflineMetaRepair' against an hbase-2.x cluster. This is a CHECK only");
        writer.println("   method, designed for reporting purposes and doesn't perform any");
        writer.println("   fixes, providing a view of which regions (if any) would get re-added");
        writer.println("   to hbase:meta, grouped by respective table/namespace. To effectively");
        writer
                .println("   re-add regions in meta, run " + ADD_MISSING_REGIONS_IN_META_FOR_TABLES + ".");
        writer.println("   This command needs hbase:meta to be online. For each namespace/table");
        writer.println("   passed as parameter, it performs a diff between regions available in");
        writer.println("   hbase:meta against existing regions dirs on HDFS. Region dirs with no");
        writer.println("   matches are printed grouped under its related table name. Tables with");
        writer.println("   no missing regions will show a 'no missing regions' message. If no");
        writer.println("   namespace or table is specified, it will verify all existing regions.");
        writer.println("   It accepts a combination of multiple namespace and tables. Table names");
        writer.println("   should include the namespace portion, even for tables in the default");
        writer.println("   namespace, otherwise it will assume as a namespace value.");
        writer.println("   An example triggering missing regions report for tables 'table_1'");
        writer.println("   and 'table_2', under default namespace:");
        writer.println("     $ HBCK2 reportMissingRegionsInMeta default:table_1 default:table_2");
        writer.println("   An example triggering missing regions report for table 'table_1'");
        writer.println("   under default namespace, and for all tables from namespace 'ns1':");
        writer.println("     $ HBCK2 reportMissingRegionsInMeta default:table_1 ns1");
        writer.println("   Returns list of missing regions for each table passed as parameter, or");
        writer.println("   for each table on namespaces specified as parameter.");
        writer.println("   If -i or --inputFiles is specified, pass one or more input file names.");
        writer.println("   Each file contains <NAMESPACE|NAMESPACE:TABLENAME>, one per line.");
        writer.println("   For example:");
        writer.println("     $ HBCK2 " + REPORT_MISSING_REGIONS_IN_META + " -i fileName1 fileName2");
    }

    public static void usageSetRegionState(PrintWriter writer) {
        writer.println(" " + SET_REGION_STATE + " [<ENCODED_REGIONNAME> <STATE>|-i <INPUT_FILE>...]");
        writer.println("   Options:");
        writer.println("    -i,--inputFiles  take one or more input files of encoded region names ");
        writer.println("   and states.");
        writer.println("   To set the replica region's state, it needs the primary region's ");
        writer.println("   encoded regionname and replica id. The command will be ");
        writer.println("   " + SET_REGION_STATE + " <PRIMARY_ENCODED_REGIONNAME>,<replicaId> <STATE>");
        writer.println("   Possible region states:");
        writer.println("    OFFLINE, OPENING, OPEN, CLOSING, CLOSED, SPLITTING, SPLIT,");
        writer.println("    FAILED_OPEN, FAILED_CLOSE, MERGING, MERGED, SPLITTING_NEW,");
        writer.println("    MERGING_NEW, ABNORMALLY_CLOSED");
        writer.println("   WARNING: This is a very risky option intended for use as last resort.");
        writer.println("   Example scenarios include unassigns/assigns that can't move forward");
        writer.println("   because region is in an inconsistent state in 'hbase:meta'. For");
        writer.println("   example, the 'unassigns' command can only proceed if passed a region");
        writer.println("   in one of the following states: SPLITTING|SPLIT|MERGING|OPEN|CLOSING");
        writer.println("   Before manually setting a region state with this command, please");
        writer.println("   certify that this region is not being handled by a running procedure,");
        writer.println("   such as 'assign' or 'split'. You can get a view of running procedures");
        writer.println("   in the hbase shell using the 'list_procedures' command. An example");
        writer.println("   setting region 'de00010733901a05f5a2a3a382e27dd4' to CLOSING:");
        writer.println("     $ HBCK2 setRegionState de00010733901a05f5a2a3a382e27dd4 CLOSING");
        writer.println("   Returns \"0\" if region state changed and \"1\" otherwise.");
        writer.println("   If -i or --inputFiles is specified, pass one or more input file names.");
        writer.println("   Each file contains <ENCODED_REGIONNAME> <STATE>, one pair per line.");
        writer.println("   For example:");
        writer.println("     $ HBCK2 " + SET_REGION_STATE + " -i fileName1 fileName2");
    }

    public static void usageSetTableState(PrintWriter writer) {
        writer.println(" " + SET_TABLE_STATE + " [<TABLENAME> <STATE>|-i <INPUT_FILE>...]");
        writer.println("   Options:");
        writer.println("    -i,--inputFiles  take one or more files of table names and states");
        writer.println("   Possible table states: " + Arrays.stream(TableState.State.values())
                .map(Enum::toString).collect(Collectors.joining(", ")));
        writer.println("   To read current table state, in the hbase shell run:");
        writer.println("     hbase> get 'hbase:meta', '<TABLENAME>', 'table:state'");
        writer.println("   A value of \\x08\\x00 == ENABLED, \\x08\\x01 == DISABLED, etc.");
        writer.println("   Can also run a 'describe \"<TABLENAME>\"' at the shell prompt.");
        writer.println("   An example making table name 'user' ENABLED:");
        writer.println("     $ HBCK2 setTableState users ENABLED");
        writer.println("   Returns whatever the previous table state was.");
        writer.println("   If -i or --inputFiles is specified, pass one or more input file names.");
        writer.println("   Each file contains <TABLENAME> <STATE>, one pair per line.");
        writer.println("   For example:");
        writer.println("     $ HBCK2 " + SET_TABLE_STATE + " -i fileName1 fileName2");
    }

    public static void usageScheduleRecoveries(PrintWriter writer) {
        writer.println(" " + SCHEDULE_RECOVERIES + " [<SERVERNAME>...|-i <INPUT_FILE>...]");
        writer.println("   Options:");
        writer.println("    -i,--inputFiles  take one or more input files of server names");
        writer.println("   Schedule ServerCrashProcedure(SCP) for list of RegionServers. Format");
        writer.println("   server name as '<HOSTNAME>,<PORT>,<STARTCODE>' (See HBase UI/logs).");
        writer.println("   Example using RegionServer 'a.example.org,29100,1540348649479':");
        writer.println("     $ HBCK2 scheduleRecoveries a.example.org,29100,1540348649479");
        writer.println("   Returns the pid(s) of the created ServerCrashProcedure(s) or -1 if");
        writer.println("   no procedure created (see master logs for why not).");
        writer.println("   Command support added in hbase versions 2.0.3, 2.1.2, 2.2.0 or newer.");
        writer.println("   If -i or --inputFiles is specified, pass one or more input file names.");
        writer.println("   Each file contains <SERVERNAME>, one per line. For example:");
        writer.println("     $ HBCK2 " + SCHEDULE_RECOVERIES + " -i fileName1 fileName2");
    }

    public static void usageRecoverUnknown(PrintWriter writer) {
        writer.println(" " + RECOVER_UNKNOWN);
        writer.println("   Schedule ServerCrashProcedure(SCP) for RegionServers that are reported");
        writer.println("   as unknown.");
        writer.println("   Returns the pid(s) of the created ServerCrashProcedure(s) or -1 if");
        writer.println("   no procedure created (see master logs for why not).");
        writer.println("   Command support added in hbase versions 2.2.7, 2.3.5, 2.4.3,");
        writer.println("     2.5.0 or newer.");
    }

    public static void usageUnassigns(PrintWriter writer) {
        writer.println(" " + UNASSIGNS + " [OPTIONS] [<ENCODED_REGIONNAME>...|-i <INPUT_FILE>...]");
        writer.println("   Options:");
        writer.println("    -o,--override  override ownership by another procedure");
        writer.println("    -i,--inputFiles  take one or more input files of encoded region names");
        writer.println("    -b,--batchSize   number of regions to process in a batch");
        writer.println("   A 'raw' unassign that can be used even during Master initialization");
        writer.println("   (if the -skip flag is specified). Skirts Coprocessors. Pass one or");
        writer.println("   more encoded region names. 1588230740 is the hard-coded name for the");
        writer.println("   hbase:meta region and de00010733901a05f5a2a3a382e27dd4 is an example");
        writer.println("   of what a userspace encoded region name looks like. For example:");
        writer.println("     $ HBCK2 " + UNASSIGNS + " 1588230740 de00010733901a05f5a2a3a382e27dd4");
        writer.println("   Returns the pid(s) of the created UnassignProcedure(s) or -1 if none.");
        writer.println();
        writer.println("   SEE ALSO, org.apache.hbase.hbck1.OfflineMetaRepair, the offline");
        writer.println("   hbase:meta tool. See the HBCK2 README for how to use.");
        writer.println("   If -i or --inputFiles is specified, pass one or more input file names.");
        writer.println("   Each file contains encoded region names, one per line. For example:");
        writer.println("     $ HBCK2 " + UNASSIGNS + " -i fileName1 fileName2");
        writer.println("   If -b or --batchSize is specified, the tool processes those many");
        writer.println("   regions at a time in a batch-ed manner; Consider using this option,");
        writer.println("   if the list of regions is huge, to avoid CallTimeoutException.");
        writer.println("   For example:");
        writer.println("     $ HBCK2 " + UNASSIGNS + " -i fileName1 fileName2 -b 500");
        writer.println("   By default, batchSize is set to -1 i.e. no batching is done.");
    }

    public static void usageRegioninfoMismatch(PrintWriter writer) {
        writer.println(" " + REGIONINFO_MISMATCH);
        writer.println("   Options:");
        writer.println("   -f,--fix Update hbase:meta with the corrections");
        writer.println("   It is recommended to first run this utility without the fix");
        writer.println("   option to ensure that the utility is generating the correct");
        writer.println("   serialized RegionInfo data structures. Inspect the output to");
        writer.println("   confirm that the hbase:meta rowkey matches the new RegionInfo.");
        writer.println();
        writer.println("   This tool will read hbase:meta and report any regions whose rowkey");
        writer.println("   and cell value differ in their encoded region name. HBASE-23328 ");
        writer.println("   illustrates a problem for read-replica enabled tables in which ");
        writer.println("   the encoded region name (the MD5 hash) does not match between ");
        writer.println("   the rowkey and the value. This problem is generally harmless ");
        writer.println("   for normal operation, but can break other HBCK2 tools.");
        writer.println();
        writer.println("   Run this command to determine if any regions are affected by ");
        writer.println("   this bug and use the -f/--fix option to then correct any");
        writer.println("   affected regions.");
    }
}

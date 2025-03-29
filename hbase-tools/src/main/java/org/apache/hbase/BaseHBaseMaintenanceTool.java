package org.apache.hbase;

import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseHBaseMaintenanceTool extends Configured implements Tool {
    protected static final Logger LOG = LoggerFactory.getLogger(BaseHBaseMaintenanceTool.class);
    protected final Configuration conf;
    protected final FileSystem fs;

    protected BaseHBaseMaintenanceTool(Configuration conf) throws IOException {
        super(conf);
        this.conf = HBaseConfiguration.create(conf);
        this.fs = FileSystem.get(this.conf);
    }

    protected Connection createConnection() throws IOException {
        return ConnectionFactory.createConnection(conf);
    }

    public static int launchTool(String[] args, BaseHBaseMaintenanceTool tool) {
        try {
            return ToolRunner.run(tool, args);
        } catch (Exception e) {
            LOG.error("Tool failed:", e);
            return 1;
        }
    }
}
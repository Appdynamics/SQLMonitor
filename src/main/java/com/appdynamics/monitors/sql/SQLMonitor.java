/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.appdynamics.monitors.sql;

import com.appdynamics.extensions.PathResolver;
import com.appdynamics.monitors.sql.config.*;
import com.google.common.base.Strings;
import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import com.singularity.ee.agent.systemagent.api.MetricWriter;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.TaskOutput;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

public class SQLMonitor extends AManagedMonitor {
    public final String VERSION = "1.0";
    protected final Logger logger = Logger.getLogger(SQLMonitor.class.getName());
    private String metricPrefix;
    public static final String CONFIG_ARG = "config-file";
    public static final String LOG_PREFIX = "log-prefix";
    private static String logPrefix;
    //To load the config files
    private final static ConfigUtil<Configuration> configUtil = new ConfigUtil<Configuration>();

    /**
     * This is the entry point to the monitor called by the Machine Agent
     *
     * @param taskArguments
     * @param taskContext
     * @return
     * @throws TaskExecutionException
     */
    public TaskOutput execute(Map<String, String> taskArguments, TaskExecutionContext taskContext) throws TaskExecutionException {
        if (taskArguments != null) {
            setLogPrefix(taskArguments.get(LOG_PREFIX));
            logger.info(getLogPrefix() + "Starting the SQL Monitoring task.");
            if (logger.isDebugEnabled()) {
                logger.debug(getLogPrefix() + "Task Arguments Passed ::" + taskArguments);
            }
            String status = "Success";

            String configFilename = getConfigFilename(taskArguments.get(CONFIG_ARG));

            try {
                //read the config.
                Configuration config = configUtil.readConfig(configFilename, Configuration.class);

                // no point continuing if we don't have this
                if (config.getCommands().isEmpty()) {
                    return new TaskOutput("Failure");
                }
                processMetricPrefix(config.getMetricPrefix());

                status = executeCommands(config, status);
            } catch (FileNotFoundException fe) {
                logger.error("File not found", fe);
            } catch (IOException ioe) {
                logger.error("IO Exception", ioe);
            }

            return new TaskOutput(status);
        }
        throw new TaskExecutionException(getLogPrefix() + "SQL monitoring task completed with failures.");
    }

    private String executeCommands(Configuration config, String status) {
        Connection conn = null;
        // loop through the statements
        try {
            for (Server server : config.getServers()) {
                conn = connect(server);

                for (Command command : config.getCommands()) {
                    try {
                        int counter = 1;
                        logger.info("sql statement: " + counter++);
                        String statement = command.getCommand();
                        String displayPrefix = command.getDisplayPrefix();
                        if (statement != null) {
                            // parse into statement and roll up
                            List<String> list = parse(statement);
                            String sql = list.get(0);
                            logger.info("Running " + sql);
                            printMetric(executeQuery(conn, sql), displayPrefix, list);
                        } else {
                            logger.error("Didn't found statement: " + counter);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (conn != null)
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
            }
        } catch (SQLException sqle) {
            logger.error("SQLException: ", sqle);
            status = "Failure";
        } catch (ClassNotFoundException ce) {
            logger.error("Class not found: ", ce);
            status = "Failure";
        } finally {
            if (conn != null) try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return status;
    }

    private void processMetricPrefix(String metricPrefix) {

        if (!metricPrefix.endsWith("|")) {
            metricPrefix = metricPrefix + "|";
        }
        if (!metricPrefix.startsWith("Custom Metrics|")) {
            metricPrefix = "Custom Metrics|" + metricPrefix;
        }

        this.metricPrefix = metricPrefix;
    }

    private Data executeQuery(Connection conn, String query) {
        Data retval = new Data();
        Statement stmt = null;
        ResultSet rs = null;

        // SECURITY
        // check for query only allow selects
        if (!query.toUpperCase().startsWith("SELECT")) {
            logger.error(query + " is not a select statement");
            return retval;
        }

        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(query);
            // only get the first result
            rs.first();
            // only get the first column
            String value = rs.getString(1);
            // use the lable for the name of the metric
            ResultSetMetaData metaData = rs.getMetaData();
            String name = metaData.getColumnLabel(1);
            retval.setName(name);
            retval.setValue(value);
        } catch (SQLException sqle) {
            logger.error("SQLException: ", sqle);
        } finally {
            if (rs != null) try {
                rs.close();
            } catch (SQLException e) {
            }
            if (stmt != null) try {
                stmt.close();
            } catch (SQLException e) {
            }
        }

        return retval;
    }

    private Connection connect(Server server) throws SQLException, ClassNotFoundException {
        Connection conn = null;
        // build the URL
        String driver = server.getDriver();
        String url = server.getUrl();
        String user = server.getUser();
        String password = server.getPassword();

        // load the driver
        if (driver != null && url != null) {
            Class.forName(driver);
            conn = DriverManager.getConnection(url, user, password);
            logger.info("Got connection " + conn);
        }

        return conn;
    }

    private void printMetric(Data data, String displayPrefix, List<String> rollup) {
        String metricName = metricPrefix + displayPrefix.concat("|") + data.getName();

        // don't write empty data
        if (data.getValue() != null) {
            logger.info("Data " + data);
            // default roll ups
            String aggregationType = MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE;
            String timeRollup = MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE;
            String clusterRollup = MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE;

            // Apply roll ups
            if (rollup.size() > 1) {
                Iterator<String> itor = rollup.iterator();
                while (itor.hasNext()) {
                    String value = itor.next();
                    if (value.equals("METRIC_AGGREGATION_TYPE_AVERAGE")) {
                        aggregationType = MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE;
                    } else if (value.equals("METRIC_AGGREGATION_TYPE_SUM")) {
                        aggregationType = MetricWriter.METRIC_AGGREGATION_TYPE_SUM;
                    } else if (value.equals("METRIC_AGGREGATION_TYPE_OBSERVATION")) {
                        aggregationType = MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION;
                    } else if (value.equals("METRIC_TIME_ROLLUP_TYPE_AVERAGE")) {
                        timeRollup = MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE;
                    } else if (value.equals("METRIC_TIME_ROLLUP_TYPE_SUM")) {
                        timeRollup = MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM;
                    } else if (value.equals("METRIC_TIME_ROLLUP_TYPE_CURRENT")) {
                        timeRollup = MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT;
                    } else if (value.equals("METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL")) {
                        clusterRollup = MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL;
                    } else if (value.equals("METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE")) {
                        clusterRollup = MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE;
                    }
                }
            }
            MetricWriter writer = getMetricWriter(metricName, aggregationType, timeRollup, clusterRollup);
            writer.printMetric(data.getValue());
        }
    }

    // parse the string from the config to get the SQL and any roll up settings
    private List<String> parse(String statement) {
        ArrayList result = new ArrayList();

        // split on --
        String[] pair = statement.split("--");
        result.add(pair[0].trim());
        if (pair.length == 2) {
            String[] rollups = pair[1].trim().split("\\s");
            result.addAll(Arrays.asList(rollups));
        }

        return result;
    }


    /**
     * Returns a config file name,
     *
     * @param filename
     * @return String
     */
    private String getConfigFilename(String filename) {
        if (filename == null) {
            return "";
        }
        //for absolute paths
        if (new File(filename).exists()) {
            return filename;
        }
        //for relative paths
        File jarPath = PathResolver.resolveDirectory(AManagedMonitor.class);
        String configFileName = "";
        if (!Strings.isNullOrEmpty(filename)) {
            configFileName = jarPath + File.separator + filename;
        }
        return configFileName;
    }

    public String getLogPrefix() {
        return logPrefix;
    }

    public void setLogPrefix(String logPrefix) {
        this.logPrefix = (logPrefix != null) ? logPrefix : "";
    }

    public static void main(String[] args) throws Exception {
        SQLMonitor monitor = new SQLMonitor();
        System.out.println("SQLMonitor version " + monitor.VERSION);
        Map<String, String> taskArgs = new HashMap();
        taskArgs.put(CONFIG_ARG, "src/main/resources/conf/config.yml");
        TaskExecutionContext context = null;
        System.out.println(monitor.execute(taskArgs, context));
    }
}
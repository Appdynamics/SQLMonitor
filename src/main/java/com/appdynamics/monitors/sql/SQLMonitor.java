/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.appdynamics.monitors.sql;

import com.appdynamics.TaskInputArgs;
import com.appdynamics.extensions.PathResolver;
import com.appdynamics.extensions.crypto.CryptoUtil;
import com.appdynamics.extensions.yml.YmlReader;
import com.appdynamics.monitors.sql.config.Command;
import com.appdynamics.monitors.sql.config.Configuration;
import com.appdynamics.monitors.sql.config.Server;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import com.singularity.ee.agent.systemagent.api.MetricWriter;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.TaskOutput;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.io.File;
import java.io.OutputStreamWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class SQLMonitor extends AManagedMonitor {
    private static final Logger logger = Logger.getLogger(SQLMonitor.class);
    public String metricPrefix;
    private static final String CONFIG_ARG = "config-file";
    private static final String LOG_PREFIX = "log-prefix";
    private String logPrefix;

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
            logger.info("Using Monitor Version [" + getImplementationVersion() + "]");
            logger.info(getLogPrefix() + "Starting the SQL Monitoring task.");
            if (logger.isDebugEnabled()) {
                logger.debug(getLogPrefix() + "Task Arguments Passed ::" + taskArguments);
            }
            String status = "Success";

            String configFilename = getConfigFilename(taskArguments.get(CONFIG_ARG));

            try {
                //read the config.
                Configuration config = YmlReader.readFromFile(configFilename, Configuration.class);

                /*// no point continuing if we don't have this
                if (config.getCommands().isEmpty()) {
                    return new TaskOutput("Failure");
                }*/
                processMetricPrefix(config.getMetricPrefix());

                status = executeCommands(config, status);
            } catch (Exception ioe) {
                logger.error("Exception", ioe);
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

                for (Command command : server.getCommands()) {
                    try {
                        int counter = 1;
                        logger.info("sql statement: " + counter++);
                        String statement = command.getCommand();
                        String displayPrefix = server.getDisplayName() + "|" + command.getDisplayPrefix();
                        if (statement != null) {
                            statement = statement.trim();
                            // parse into statement and roll up
                            logger.info("Running " + statement);
                            printMetric(executeQuery(conn, statement), displayPrefix);
                        } else {
                            logger.error("Didn't found statement: " + counter);
                        }
                    } catch (Exception e) {
                        logger.error("Error executing query", e);
                    }
                }
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        logger.error("Unable to close the connection", e);
                    }
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
                logger.error("Unable to close the connection", e);
            }
        }
        return status;
    }

    private void processMetricPrefix(String metricPrefix) {

        if (!metricPrefix.endsWith("|")) {
            metricPrefix = metricPrefix + "|";
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
            stmt = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            rs = stmt.executeQuery(query);
            if (rs.next()) {
                // only get the first column
                String value = rs.getString(1);
                // use the lable for the name of the metric
                ResultSetMetaData metaData = rs.getMetaData();
                String name = metaData.getColumnLabel(1);
                retval.setName(name);
                retval.setValue(value);
            } else {
                logger.info("Got empty ResultSet for query [ " + query + " ]");
            }
        } catch (SQLException sqle) {
            logger.error("SQLException: ", sqle);
        } finally {
            if (rs != null) try {
                rs.close();
            } catch (SQLException e) {
                logger.error("Unable to close the ResultSet", e);
            }
            if (stmt != null) try {
                stmt.close();
            } catch (SQLException e) {
                logger.error("Unable to close the Statement", e);
            }
        }

        return retval;
    }

    private Connection connect(Server server) throws SQLException, ClassNotFoundException {
        Connection conn = null;
        // build the URL
        String driver = server.getDriver();
        String connectionString = server.getConnectionString();
        String user = server.getUser();
        String password = getPassword(server);

        // load the driver
        if (driver != null && connectionString != null) {
            Class.forName(driver);
            conn = DriverManager.getConnection(connectionString, user, password);

            if (conn.getMetaData().supportsTransactions()) {
                int isolationLevel = Server.IsolationLevel.getIsolationLevel(server.getIsolationLevel());

                if (logger.isDebugEnabled()) {
                    logger.debug("Isolation level provided is [" + server.getIsolationLevel() + "] and setting the isolation level as [" + isolationLevel + "]");
                }

                logger.info("Setting isolation level as [" + isolationLevel + "]");

                conn.setTransactionIsolation(isolationLevel);
            } else {
                logger.info("Transactions are not supported so ignoring the isolation level.");
            }
            logger.info("Got connection " + conn);
        }

        return conn;
    }

    private String getPassword(Server server) {
        String password = null;

        if (!Strings.isNullOrEmpty(server.getPassword())) {
            password = server.getPassword();

        } else {
            try {
                Map<String, String> args = Maps.newHashMap();
                args.put(TaskInputArgs.PASSWORD_ENCRYPTED, server.getEncryptedPassword());
                args.put(TaskInputArgs.ENCRYPTION_KEY, server.getEncryptionKey());
                password = CryptoUtil.getPassword(args);

            } catch (IllegalArgumentException e) {
                String msg = "Encryption Key not specified. Please set the value in config.yml.";
                logger.error(msg);
                throw new IllegalArgumentException(msg);
            }
        }

        return password;
    }

    public void printMetric(Data data, String displayPrefix) {
        String metricName = metricPrefix + displayPrefix.concat("|") + data.getName();

        // don't write empty data
        if (data.getValue() != null) {
            logger.debug(String.format("Printing metric [ %s ] with value [ %s ]", metricName, data.getValue()));
            // default roll ups
            String aggregationType = MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE;
            String timeRollup = MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE;
            String clusterRollup = MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL;
            MetricWriter writer = getMetricWriter(metricName, aggregationType, timeRollup, clusterRollup);
            writer.printMetric(data.getValue());
        }
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

    private String getLogPrefix() {
        return logPrefix;
    }

    private void setLogPrefix(String logPrefix) {
        this.logPrefix = (logPrefix != null) ? logPrefix : "";
    }

    private static String getImplementationVersion() {
        return SQLMonitor.class.getPackage().getImplementationTitle();
    }

    public static void main(String[] args) throws TaskExecutionException {


        ConsoleAppender ca = new ConsoleAppender();
        ca.setWriter(new OutputStreamWriter(System.out));
        ca.setLayout(new PatternLayout("%-5p [%t]: %m%n"));
        ca.setThreshold(Level.TRACE);

        logger.getRootLogger().addAppender(ca);

        SQLMonitor monitor = new SQLMonitor();

        Map<String, String> taskArgs = new HashMap<String, String>();
        taskArgs.put(CONFIG_ARG, "/Users/Muddam/AppDynamics/Code/extensions/SQLMonitor/src/main/resources/conf/config.yml");
        taskArgs.put(LOG_PREFIX, "[SQLMonitorAppDExt] ");


        monitor.execute(taskArgs, null);

    }

}

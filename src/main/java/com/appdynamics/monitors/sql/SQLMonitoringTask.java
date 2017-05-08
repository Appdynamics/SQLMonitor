package com.appdynamics.monitors.sql;

import com.appdynamics.TaskInputArgs;
import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.extensions.crypto.CryptoUtil;
import com.appdynamics.extensions.util.MetricWriteHelper;
import com.appdynamics.monitors.sql.config.Command;
import com.appdynamics.monitors.sql.config.Server;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.singularity.ee.agent.systemagent.api.MetricWriter;
import org.apache.log4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by akshay.srivastava on 27/04/17.
 */
public class SQLMonitoringTask implements Runnable {

    private static final Logger logger = Logger.getLogger(SQLMonitoringTask.class);
    public String metricPrefix;
    private Map sqlServers;
    private MonitorConfiguration configuration;


    public SQLMonitoringTask(MonitorConfiguration configuration, Map sqlServers) {
        this.configuration = configuration;
        this.sqlServers = sqlServers;
        this.metricPrefix = configuration.getMetricPrefix() + "|";
    }

    public void run() {

        Server server = constructRequestObjects();
        executeCommands(server);
    }

    private Server constructRequestObjects() {

        Server server = new Server();

        server.setConnectionString((String) sqlServers.get("connectionString"));
        server.setDisplayName((String) sqlServers.get("displayName"));
        server.setDriver((String) sqlServers.get("driver"));
        server.setEncryptedPassword((String) sqlServers.get("encryptedPassword"));
        String encryptionKey = (String) configuration.getConfigYml().get("encryptionKey");
        server.setEncryptionKey(encryptionKey);
        server.setIsolationLevel((String) sqlServers.get("isolationLevel"));
        server.setPassword((String) sqlServers.get("password"));
        server.setUser((String) sqlServers.get("user"));

        List<Command> commandList = new ArrayList<Command>();
        List<Map> commands = (List<Map>) sqlServers.get("commands");

        for (Map sqlCommand : commands) {

            Command command = new Command();
            command.setCommand((String) sqlCommand.get("command"));
            command.setDisplayPrefix((String) sqlCommand.get("displayPrefix"));

            commandList.add(command);
        }

        server.setCommands(commandList);

        return server;
    }

    private void executeCommands(Server server) {
        Connection conn = null;
        // loop through the statements
        try {
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

        } catch (SQLException sqle) {
            logger.error("SQLException: ", sqle);
        } catch (ClassNotFoundException ce) {
            logger.error("Class not found: ", ce);
        } finally {
            if (conn != null) try {
                conn.close();
            } catch (SQLException e) {
                logger.error("Unable to close the connection", e);
            }
        }
    }

    protected Data executeQuery(Connection conn, String query) {
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

    protected Connection connect(Server server) throws SQLException, ClassNotFoundException {
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
            MetricWriteHelper metricWriter = configuration.getMetricWriter();
            metricWriter.printMetric(metricName, data.getValue(), aggregationType, timeRollup, clusterRollup);
        } else {
            logger.info(String.format("Ignoring metric %s with null value", metricName));
        }
    }
}
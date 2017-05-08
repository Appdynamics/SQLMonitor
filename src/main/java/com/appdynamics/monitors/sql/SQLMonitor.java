package com.appdynamics.monitors.sql;

import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.extensions.util.MetricWriteHelper;
import com.appdynamics.extensions.util.MetricWriteHelperFactory;

import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.TaskOutput;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by akshay.srivastava on 27/04/17.
 */
public class SQLMonitor extends AManagedMonitor {

    private static final Logger logger = Logger.getLogger(SQLMonitor.class);

    private static final String METRIC_PREFIX = "Custom Metrics|SQL|";
    private static final String CONFIG_ARG = "config-file";
    private static final String LOG_PREFIX = "log-prefix";

    private boolean initialized;
    private MonitorConfiguration configuration;
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

            Thread thread = Thread.currentThread();
            ClassLoader originalCl = thread.getContextClassLoader();
            thread.setContextClassLoader(AManagedMonitor.class.getClassLoader());

            try {

                if (!initialized) {
                    initialize(taskArguments);
                }

                configuration.executeTask();
                logger.info("Finished SQL monitor execution");
                return new TaskOutput("Finished SQL monitor execution");
            } catch (Exception e) {
                logger.error("Failed to execute the SQL monitoring task", e);
                throw new TaskExecutionException("Failed to execute the SQL monitoring task" + e);
            } finally {
                thread.setContextClassLoader(originalCl);
            }
        }
        throw new TaskExecutionException(getLogPrefix() + "SQL monitoring task completed with failures.");
    }

    private void initialize(Map<String, String> argsMap) {
        if (!initialized) {
            final String configFilePath = argsMap.get(CONFIG_ARG);

            MetricWriteHelper metricWriteHelper = MetricWriteHelperFactory.create(this);
            MonitorConfiguration conf = new MonitorConfiguration(METRIC_PREFIX, new TaskRunnable(), metricWriteHelper);
            conf.setConfigYml(configFilePath);

            conf.checkIfInitialized(MonitorConfiguration.ConfItem.CONFIG_YML, MonitorConfiguration.ConfItem.METRIC_PREFIX,
                    MonitorConfiguration.ConfItem.METRIC_WRITE_HELPER, MonitorConfiguration.ConfItem.EXECUTOR_SERVICE);
            this.configuration = conf;
            initialized = true;
        }
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

    private class TaskRunnable implements Runnable {

        public void run() {
            if (!initialized) {
                logger.info("SQL Monitor is still initializing");
                return;
            }
            Map<String, ?> config = configuration.getConfigYml();

            List<Map> sqlServers = (List<Map>) config.get("dbServers");

            for (Map sqlServer : sqlServers) {

                SQLMonitoringTask task = new SQLMonitoringTask(configuration, sqlServer);
                configuration.getExecutorService().execute(task);
            }
        }
    }

    public static void main(String[] args) throws TaskExecutionException {

        ConsoleAppender ca = new ConsoleAppender();
        ca.setWriter(new OutputStreamWriter(System.out));
        ca.setLayout(new PatternLayout("%-5p [%t]: %m%n"));
        ca.setThreshold(Level.TRACE);

        logger.getRootLogger().addAppender(ca);

        final SQLMonitor monitor = new SQLMonitor();

        final Map<String, String> taskArgs = new HashMap<String, String>();
        taskArgs.put(CONFIG_ARG, "/Users/akshay.srivastava/AppDynamics/extensions/SQLMonitor/src/main/resources/conf/config.yml");
        taskArgs.put(LOG_PREFIX, "[SQLMonitorAppDExt] ");

        //monitor.execute(taskArgs, null);


        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                try {
                    monitor.execute(taskArgs, null);
                } catch (Exception e) {
                    logger.error("Error while running the task", e);
                }
            }
        }, 2, 60, TimeUnit.SECONDS);

    }
}
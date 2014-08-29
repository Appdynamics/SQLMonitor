package com.appdynamics.monitors.sql;

import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by abhi.pandey on 8/6/14.
 */
public class SQLMonitorTest {
    private static final String CONFIG_ARG = "config-file";

    private SQLMonitor testClass;

    @Before
    public void init() throws Exception {
        testClass = new SQLMonitor();
    }

    public void testSQLMonitor() throws TaskExecutionException {
        Map<String, String> taskArgs = new HashMap();
        taskArgs.put(CONFIG_ARG, "src/test/resources/conf/config.yml");
        testClass.execute(taskArgs, null);

    }

    @Test
    public void testPrintMetric(){
        testClass.metricPrefix = "Custom Metrics|SQL|";
        String  displayPrefix = "abc";
        Data data = new Data();
        data.setName("xyz");
        data.setValue("20");
        testClass.printMetric(data, displayPrefix);
    }
}

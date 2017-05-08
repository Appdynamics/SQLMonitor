package com.appdynamics.monitors.sql;

import com.google.common.collect.Maps;
import com.singularity.ee.agent.systemagent.api.TaskOutput;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;

import static org.junit.Assert.assertTrue;
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

    @Test
    public void testSQLMonitor() throws TaskExecutionException {
        Map<String, String> taskArgs = new HashMap();
        taskArgs.put(CONFIG_ARG, "src/test/resources/conf/config.yml");
        TaskOutput result = testClass.execute(taskArgs, null);
        assertTrue(result.getStatusMessage().contains("Finished SQL monitor execution"));

    }

    @Test(expected = TaskExecutionException.class)
    public void testWithNullArgsShouldResultInException() throws Exception {
        testClass.execute(null, null);
    }

    @Test(expected = TaskExecutionException.class)
    public void testWithNoValidLogConfigResultInException() throws Exception {
        Map<String, String> args = Maps.newHashMap();
        args.put("config-file", "src/test/resources/conf/invalidConfig.yml");

        testClass.execute(args, null);
    }
}

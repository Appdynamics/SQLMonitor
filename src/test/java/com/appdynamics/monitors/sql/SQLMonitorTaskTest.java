package com.appdynamics.monitors.sql;

import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.monitors.sql.config.Command;
import com.appdynamics.monitors.sql.config.Server;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.powermock.api.mockito.PowerMockito.mock;

import static org.powermock.api.mockito.PowerMockito.verifyPrivate;

/**
 * Created by akshay.srivastava on 28/04/17.
 */
public class SQLMonitorTaskTest {

    private SQLMonitoringTask testClass;

    @Mock
    Server server;

    @Before
    public void init() throws Exception {

        server = new Server();
        server.setUser("root");
        server.setPassword("root");
        server.setDriver("Instance1");
        server.setDriver("com.mysql.jdbc.Driver");
        server.setConnectionString("jdbc:mysql://localhost:3388/test");
        server.setIsolationLevel("TRANSACTION_READ_UNCOMMITTED");

        testClass = new SQLMonitoringTask(mock(MonitorConfiguration.class), mock(Map.class));

    }

    @Test
    public void executeCommandsTest() throws Exception{

        List<Command> commandList = new ArrayList<Command>();

        Command command1 = new Command();
        command1.setDisplayPrefix("Person1");
        command1.setCommand("select age from test1 where age = 29");
        commandList.add(command1);

        server.setCommands(commandList);

        verifyPrivate(testClass).invoke("executeCommands",
                server);
    }

    @Test
    public void executeQueryTest() throws Exception {

        Command command1 = new Command();
        command1.setDisplayPrefix("Person1");
        command1.setCommand("select age from test1 where age = 29");

        Connection conn = testClass.connect(server);
        Assert.assertNotNull(testClass.executeQuery(conn, command1.getCommand()));
    }

    @Test
    public void executeQueryNullDataTest() throws Exception{
        Command command1 = new Command();
        command1.setDisplayPrefix("Person1");
        command1.setCommand("select age from test1 where age = 9");

        Connection conn = testClass.connect(server);
        Assert.assertNull(testClass.executeQuery(conn, command1.getCommand()).getValue());
    }

    @Test
    public void incorrectQueryReturnNullTest() throws Exception{
        Command command1 = new Command();
        command1.setDisplayPrefix("Person1");
        command1.setCommand("insert into test1 (name,age) values('Person3',10)");

        Connection conn = testClass.connect(server);
        Assert.assertNull(testClass.executeQuery(conn, command1.getCommand()).getValue());

    }

}

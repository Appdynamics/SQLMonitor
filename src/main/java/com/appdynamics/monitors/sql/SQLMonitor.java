import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import com.singularity.ee.agent.systemagent.api.MetricWriter;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.TaskOutput;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class ArbitrarySqlMonitor extends AManagedMonitor
{
	public String metricPrefix;
    public static final String CONFIG_ARG = "config-file";
    public static final String LOG_PREFIX = "log-prefix";
    private static String logPrefix;
    private static final Log logger = LogFactory.getLog(ArbitrarySqlMonitor.class);
    private boolean cleanFieldNames;
    private String metricPath;  
    
    protected void printMetric(String name, String value, String aggType, String timeRollup, String clusterRollup)
    {
        String metricName = getMetricPrefix() + name;
        MetricWriter metricWriter = getMetricWriter(metricName, aggType, timeRollup, clusterRollup);
        metricWriter.printMetric(value);

        if (logger.isDebugEnabled())
        {
            logger.debug("METRIC:  NAME:" + metricName + " VALUE:" + value + " :" + aggType + ":" + timeRollup + ":" + clusterRollup);
        }
    }

    private String cleanFieldName(String name)
    {
        if (cleanFieldNames)
        {
            /**
             * Unicode characters sometimes look weird in the UI, so we replace all Unicode hyphens with
             * regular hyphens. The \d{Pd} character class matches all hyphen characters.
             * @see <a href="URL#http://www.regular-expressions.info/unicode.html">this reference</a>
             */
            return name.replaceAll("\\p{Pd}", "-")
                    .replaceAll("_", " ");
        }
        else
        {
            return name;
        }
    }
    
    private void processMetricPrefix(String metricPrefix) 
    {

        if (!metricPrefix.endsWith("|")) 
        {
            metricPrefix = metricPrefix + "|";
        }
        if (!metricPrefix.startsWith("Custom Metrics|")) {
            metricPrefix = "Custom Metrics|" + metricPrefix;
        }
        this.metricPrefix = metricPrefix;
    }
    
    public TaskOutput execute(Map<String, String> taskArguments, TaskExecutionContext taskContext) throws TaskExecutionException 
    {	
        if (taskArguments != null) 
        {
            setLogPrefix(taskArguments.get(LOG_PREFIX));
            logger.info(getLogPrefix() + "Starting the SQL Monitoring task.");
            
            if (logger.isDebugEnabled()) 
            {
                logger.debug(getLogPrefix() + "Task Arguments Passed ::" + taskArguments);
            }
            String status = "Success";
            String configFilename = getConfigFilename(taskArguments.get(CONFIG_ARG));

            try 
            {  			
                Configuration config = YmlReader.readFromFile(configFilename, Configuration.class);

                if (config.getCommands().isEmpty()) 
                {
                    return new TaskOutput("Failure");
                }          
                processMetricPrefix(config.getMetricPrefix());
                status = executeCommands(config, status);                	
            }                                                     
            catch (Exception ioe) 
            {
                logger.error("Exception", ioe);
            }
            return new TaskOutput(status);
        }
        throw new TaskExecutionException(getLogPrefix() + "SQL monitoring task completed with failures.");
    }
  
    private String executeCommands(Configuration config, String status) 
    {
        Connection conn = null;
        
        try 
        {
            for (Server server : config.getServers()) 
            {
                conn = connect(server);

                for (Command command : config.getCommands()) 
                {
                    try 
                    {
                        int counter = 1;
                        logger.info("sql statement: " + counter++);
                        String statement = command.getCommand().trim();
                        String displayPrefix = command.getDisplayPrefix();
                        
                        if (statement != null) 
                        {                       	
                            // parse into statement and roll up
                            logger.info("Running " + statement);                        
                            executeQuery(conn, statement, displayPrefix);
                        } 
                        else 
                        {
                            logger.error("Didn't find statement: " + counter);
                        }
                    } 
                    catch (Exception e) 
                    {
                        e.printStackTrace();
                    }
                }
                if (conn != null) 
                {
                    try 
                    {
                        conn.close();
                    } catch (SQLException e) 
                    {
                        e.printStackTrace();
                    }
                }
            }
        } 
        catch (SQLException sqle) 
        {
            logger.error("SQLException: ", sqle);
            status = "Failure";
        } 
        catch (ClassNotFoundException ce) 
        {
            logger.error("Class not found: ", ce);
            status = "Failure";
        } finally 
        {
            if (conn != null) try 
            {
                conn.close();
            } 
            catch (SQLException e) 
            {
                e.printStackTrace();
            }
        }
        return status;
    }

    private Data executeQuery(Connection conn, String query, String displayPrefix) 
    {
        Data retval = new Data();
        Statement stmt = null;
        ResultSet rs = null;
        
        try 
        {	
        	long rowCount10 = 0;
            stmt = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);                                  
            rs = stmt.executeQuery(query);
            logger.info("display prefix: " + displayPrefix);
                       
            //get row and column count of result set
            int rowCount = 0;
            while (rs.next()) 
            {
                ++rowCount;              
            }
            logger.info("row count of resultset: " + rowCount);
            
            ResultSetMetaData rsmd = rs.getMetaData();
            int columnCount = rsmd.getColumnCount();
            logger.info("column count: " + columnCount);
            
            //set cursor to beginning
            rs.beforeFirst();
            
            //this deals with the single row/column case
            if(columnCount == 1 && rowCount == 1)
            {           	           	
            	if(rs.next())
            	{
            		String key = cleanFieldName(rs.getString(1));
                	String metricPreF = metricPrefix + displayPrefix;
            		String metricName = cleanFieldName(rs.getMetaData().getColumnName(1));              	              	
                    String value = rs.getString(1);
                    ResultSetMetaData metaData = rs.getMetaData();
                    String name = metaData.getColumnLabel(1);
                    retval.setName(name);
                    retval.setValue(value);
                                     
            		printMetricArb(metricPreF + "|" + key + "|" + metricName, rs.getString(1),
                            MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION,
                            MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
                            MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
                    logger.debug(key + "|" + metricName + " = " + rs.getString(1));
                    logger.info("metric key: " + key);	                    
                    logger.info("metric path: " +metricPreF + "|" + key + "|" + metricName);
            	}
            }
            // multi row columns returned 
            else
            {           	
	            while(rs.next())
	            {
	            	String key = cleanFieldName(rs.getString(1));
	            	
	            	for (int i = 2; i <= rs.getMetaData().getColumnCount(); i++)
	            	{           			
	            		String metricPreF = metricPrefix + displayPrefix;
	            		String metricName = cleanFieldName(rs.getMetaData().getColumnName(i));
	            		
	            		retval.setName(metricName);
	            		retval.setValue(rs.getString(i));            	
	            		
	            		printMetricArb(metricPreF + "|" + key + "|" + metricName, rs.getString(i),
	                            MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION,
	                            MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
	                            MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
	                    logger.debug(key + "|" + metricName + " = " + rs.getString(i));
	                    logger.info("metric key: " + key);	                    
	                    logger.info("metric path: " +metricPreF + "|" + key + "|" + metricName);	            	
	            	}
	            	rowCount10 += 1;   	
	            }          
            }         
        } 
        catch (SQLException sqle) 
        {
            logger.error("SQLException: ", sqle);
        } 
        finally 
        {
            if (rs != null) try 
            {
                rs.close();
            } 
            catch (SQLException e) 
            {}
            if (stmt != null) 
            	try 
            	{
            		stmt.close();
            	} 
            	catch (SQLException e) {}
        }
        return retval;
    }

    private Connection connect(Server server) throws SQLException, ClassNotFoundException 
    {
        Connection conn = null;
        String driver = server.getDriver();
        String connectionString = server.getConnectionString();
        String user = server.getUser();
        String password = server.getPassword();

        if (driver != null && connectionString != null) 
        {
            Class.forName(driver);
            conn = DriverManager.getConnection(connectionString, user, password);
            logger.info("Got connection " + conn);
        }
        return conn;
    }
    
    public void printMetric(Data data, String displayPrefix) 
    {
        String metricName = metricPrefix + displayPrefix.concat("|") + data.getName();

        // don't write empty data
        if (data.getValue() != null) 
        {
            logger.info("Data " + data);
            // default roll ups
            String aggregationType = MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE;
            String timeRollup = MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE;
            String clusterRollup = MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL;
            MetricWriter writer = getMetricWriter(metricName, aggregationType, timeRollup, clusterRollup);
            writer.printMetric(data.getValue());
        }
    }
    
    protected void printMetricArb(String name, String value, String aggType, String timeRollup, String clusterRollup)
    {
    	String metricName = name;
        MetricWriter metricWriter = getMetricWriter(metricName, aggType, timeRollup, clusterRollup);
        metricWriter.printMetric(value);

        if (logger.isDebugEnabled())
        {
            logger.debug("METRIC:  NAME:" + metricName + " VALUE:" + value + " :" + aggType + ":" + timeRollup + ":"
                    + clusterRollup);
        }
    }

    private String getConfigFilename(String filename) 
    {
        if (filename == null) 
        {
            return "";
        }
        //for absolute paths
        if (new File(filename).exists()) 
        {
            return filename;
        }
        //for relative paths
        File jarPath = PathResolver.resolveDirectory(AManagedMonitor.class);
        String configFileName = "";
        if (!Strings.isNullOrEmpty(filename)) 
        {
            configFileName = jarPath + File.separator + filename;
        }
        return configFileName;
    }

    private String getLogPrefix() 
    {
        return logPrefix;
    }

    private void setLogPrefix(String logPrefix) {
        this.logPrefix = (logPrefix != null) ? logPrefix : "";
    }

    protected String getMetricPrefix()
    {
        if (metricPath != null)
        {
            if (!metricPath.endsWith("|"))
            {
                metricPath += "|";
            }
            return metricPath;
        }
        else
        {
            return "Custom Metrics|SQLMonitor|";
        }
    }

    //main method initializes variables for testing purposes
    public static void main(String[] argv) throws Exception
    {
    	Map<String, String> taskArguments = new HashMap<String, String>();
    	taskArguments.put("config-file", "c:\\MA5\\MachineAgent\\monitors\\ArbitrarySQLMonitor\\config.yml");
    	taskArguments.put("log-prefix", "[SQLMonitorAppDExt]");
    	
        new ArbitrarySqlMonitor().execute(taskArguments, null);
    }
}

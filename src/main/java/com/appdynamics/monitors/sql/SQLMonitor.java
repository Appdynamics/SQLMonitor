package com.singularity.ee.agent.systemagent.monitors;

import com.appdynamics.extensions.PathResolver;
import com.appdynamics.extensions.yml.YmlReader;
import com.google.common.base.Strings;
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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import org.joda.time.DateTime;

public class ArbitrarySqlMonitor extends AManagedMonitor
{
	public String metricPrefix;
    public static final String CONFIG_ARG = "config-file";
    public static final String LOG_PREFIX = "log-prefix";
    private static String logPrefix;
    private static final Log logger = LogFactory.getLog(ArbitrarySqlMonitor.class);
    private boolean cleanFieldNames;
    private String metricPath;  
    private String dateStampFromFile = null; 
    boolean hasDateStamp = false;
    BufferedReader br = null;
    private String relativePath = null;
	private DateTime timeLastExecuted = new DateTime(new DateTime());
	private String timeper_in_sec = null;
	private String execution_freq_in_secs = null;
	
	DateTime currentTime = new DateTime(new DateTime());
	float diffInMillis = -1.0F;
	Float DiffInSec = null;
    boolean skippedMetricWrite = false;

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
    	Long timeper_in_secConv = null;
    	Long execution_freq_in_secsConv = null;
    	
    	//relativePath for reading/writing time stamp that tracks last execution of queries
    	relativePath = taskArguments.get("machineAgent-relativePath");
		relativePath += "\\monitors\\ArbitrarySqlMonitor\\timeStamp.txt";
		
		File file = new File(relativePath);
    	FileWriter fw;
    	logger.info("path: " + relativePath);
    	
    	timeper_in_sec = taskArguments.get("timeper_in_sec");
		execution_freq_in_secs = taskArguments.get("execution_freq_in_secs");
		timeper_in_secConv = Long.valueOf(timeper_in_sec).longValue();
		execution_freq_in_secsConv = Long.valueOf(execution_freq_in_secs).longValue();
		logger.info("timePeriod_in_sec: " + timeper_in_sec);
		
        if (taskArguments != null) 
        {
        	try 
        	{	 	
    			String sCurrentLine;
     
    			br = new BufferedReader(new FileReader("C:\\MA5\\MachineAgent\\monitors\\ArbitrarySqlMonitor\\timeStamp.txt"));
    			
    			//execution frequency should always greater than time period passed into queries to prevent duplicate data
    			if(execution_freq_in_secsConv < timeper_in_secConv)
    			{
    				logger.error("CANNOT set execution_freq_in_secs in monitor.xml to a lesser value than timeper_in_sec");
    				logger.error("execution_freq_in_secs: " + execution_freq_in_secs);
    				logger.error("timeper_in_sec: " + timeper_in_sec);
    			}
    							
    			if(br != null)
    			{ 			 
    				while ((sCurrentLine = br.readLine()) != null) 
    				{  		
    					dateStampFromFile = sCurrentLine; 					
    					timeLastExecuted = DateTime.parse(dateStampFromFile);
    				}   			
    			}
    		
    		} 
        	catch (IOException e) 
    		{
    			e.printStackTrace(); 
    		} finally 
    		{
    			try 
    			{
    				if (br != null)br.close();
    			} 
    			catch (IOException ex) 
    			{
    				ex.printStackTrace();
    			}
    		}
        	
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
            	fw = new FileWriter(file.getAbsoluteFile());
        		BufferedWriter bw = new BufferedWriter(fw);       		
        		
                Configuration config = YmlReader.readFromFile(configFilename, Configuration.class);

                if (config.getCommands().isEmpty()) 
                {
                    return new TaskOutput("Failure");
                }
                
                logger.info("metric path: " + config.getMetricPrefix());
                processMetricPrefix(config.getMetricPrefix());
  
                logger.info("instant time (current time): " + currentTime);
                logger.info("old time (Time last executed query): " + timeLastExecuted);
                
                diffInMillis = Math.abs(timeLastExecuted.getMillis() - currentTime.getMillis());
            	float diffInSec = diffInMillis / 1000;
                float diffInMin = diffInSec / 60;
                float diffInHours = diffInMin / 60;                
                
                if(timeper_in_secConv > diffInSec)
                {
                	logger.info("execution frequency > time between query execution; no duplicate data.  Time in Minutes since last execution of queries: " + diffInMin );         	                	
                    logger.info("execution frequency in seconds: " + timeper_in_secConv);
                    logger.info("Time in sec: " + diffInSec);                  
                	               	
                	timeLastExecuted = new DateTime();              
                	bw.write(timeLastExecuted.toString());
                	bw.close();
                	logger.info("date written to file: " + timeLastExecuted.toString());
                	
                	skippedMetricWrite = false;
                	status = executeCommands(config, status);                	                	
                }
                else if(timeper_in_secConv <= diffInSec)
                {
                	logger.info("(execution frequency < diffInSec; Metric data will NOT be written to ensure no duplicate data");    	               	                             
                    logger.info("Time in sec: " + diffInSec);                                       
                    timeLastExecuted = new DateTime();
                	bw.write(timeLastExecuted.toString());
                	bw.close();
                	
                	//store this in instance variable, then pass value into queries
                	DiffInSec = diffInSec;
                	
                	logger.info("execution frequency < diffInMin; DiffInSec variable value: " + DiffInSec);
                	skippedMetricWrite = true;
                	status = executeCommands(config, status);                	
                }                                              
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
        String newQuery = null;
        
        try 
        {
        	logger.info("dateStamp: " + dateStampFromFile);
        	
        	long rowCount10 = 0;
            stmt = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            
            if(query.contains("freqInSec"))
            {
            	logger.info("query contains freqInSec - if loop hit ");
            	
            	if(skippedMetricWrite == false)
            	{
            		newQuery = query.replace("freqInSec", timeper_in_sec);
                	rs = stmt.executeQuery(newQuery);
                	logger.info("query with timeDate replaced: " + newQuery);
            	}
            	else if(skippedMetricWrite == true)
            	{
            		String DiffInSecString = DiffInSec.toString();
            		newQuery = query.replace("freqInSec", DiffInSecString);
                	rs = stmt.executeQuery(newQuery);
                	logger.info("query with timeDate replaced: " + newQuery);
                	skippedMetricWrite = false;
            	}           	
            }
            else
            {
            	rs = stmt.executeQuery(query);
                logger.info("SQL query is suitable for monitoring; NO freqInSec set in monitor.xml...");
                logger.info("display prefix: " + displayPrefix);
            }
            
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
            logger.error("timeper_in_sec (value passed to replace freqInSec): " + timeper_in_sec);
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

    public static void main(String[] argv) throws Exception
    {
    	Map<String, String> taskArguments = new HashMap<String, String>();
    	taskArguments.put("config-file", "c:\\MA5\\MachineAgent\\monitors\\ArbitrarySQLMonitor\\config.yml");
    	taskArguments.put("log-prefix", "[SQLMonitorAppDExt]");
    	taskArguments.put("machineAgent-relativePath", "c:\\MA5\\MachineAgent");
    	
    	taskArguments.put("timeper_in_sec", "121");
    	taskArguments.put("execution_freq_in_secs", "240");
    	
        new ArbitrarySqlMonitor().execute(taskArguments, null);
    }
}

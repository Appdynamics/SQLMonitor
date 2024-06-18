import java.sql.*;
import java.util.Properties;

public class SQLDatabaseConnection {

    // Connect to your database.
    // Replace server name, username, and password with your credentials
    public static void main(String[] args) {
        String connectionUrl =
                args.length == 3 ? args[2] : "jdbc:sqlserver://localhost:1434;servername=localhost\\MSSQLSERVER01;database=master;integratedSecurity=true;encrypt=false;trustServerCertificate=false";

        ResultSet resultSet = null;
        try{
             //System.loadLibrary(args[2]);
             System.setProperty("java.library.path", args[0]);
             Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
             Connection connection = DriverManager.getConnection(connectionUrl);
             DatabaseMetaData metadata = connection.getMetaData();
             Statement statement = connection.createStatement();

            // Create and execute a SELECT SQL statement.
            String selectSql = "SELECT TOP (1000) [lastrun]" +
                    "      ,[cpu_busy]" +
                    "      ,[io_busy]" +
                    "      ,[idle]" +
                    "      ,[pack_received]" +
                    "      ,[pack_sent]" +
                    "      ,[connections]" +
                    "      ,[pack_errors]" +
                    "      ,[total_read]" +
                    "      ,[total_write]" +
                    "      ,[total_errors]" +
                    "  FROM [master].[dbo].[spt_monitor]";
            resultSet = statement.executeQuery(selectSql);

            // Print results from select statement
            while (resultSet.next()) {
                System.out.println(resultSet.getString(Integer.parseInt(args[1])));
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
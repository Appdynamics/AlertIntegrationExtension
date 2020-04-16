/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.appdynamics.saasAlertIntegration;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import com.appdynamics.saasAlertIntegration.connectionUtil;
import com.appdynamics.saasAlertIntegration.getEventsThread;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import org.h2.jdbcx.JdbcConnectionPool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;
/**
 *
 * @author igor.simoes
 */
public class AlertIntegrationJob implements Job{

    //final private static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    final private static Logger LOGGER = LogManager.getRootLogger();
    
    public static final String controllerURL = "controller_url";
    public static final String controllerUser = "controller_user";
    public static final String controllerAccount = "controller_account";
    public static final String controllerKey = "controller_key";
    public static final String pollInterval = "poll_interval";
    public static final String MetricRootProperty = "metric_prefix";
    public static final String integrationHostname = "integration_hostname";
    public static final String integrationPort = "integration_port";
    public static final String integrationProtocol = "integration_protocol";
    public static final String alertServerViz = "alert_server_viz";
    public static final String alertDBViz = "alert_db_viz";
    public static final String ITMWarningCode = "itm_warning_code";
    public static final String ITMCriticalCode = "itm_critical_code";
    public static final String ITMClearCode = "itm_clear_code";
    private JdbcConnectionPool connectionPool;
    
    private void setH2ConnectionPool(){
        this.connectionPool = JdbcConnectionPool.create(
        "jdbc:h2:file:./appd_events_db;TRACE_LEVEL_FILE=0;TRACE_LEVEL_SYSTEM_OUT=0;LOCK_TIMEOUT=10000", "appd", "appdintegration");
        //System.out.println("Default max connections: "+this.connectionPool.getMaxConnections());
        this.connectionPool.setMaxConnections(500);
        LOGGER.log(Level.INFO, "{}: Creating new connection pool.", new Object(){}.getClass().getEnclosingMethod().getName());
    }
    
    public void execute(JobExecutionContext context) throws JobExecutionException{
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();
        String controller_url = dataMap.getString(controllerURL); 
        String controller_user = dataMap.getString(controllerUser); 
        String controller_account = dataMap.getString(controllerAccount); 
        String controller_key = dataMap.getString(controllerKey);
        String metric_prefix = dataMap.getString(MetricRootProperty);
        String integration_hostname = dataMap.getString(integrationHostname);
        String integration_port = dataMap.getString(integrationPort);
        String integration_protocol = dataMap.getString(integrationProtocol);
        String alert_server_viz = dataMap.getString(alertServerViz);
        String alert_db_viz = dataMap.getString(alertDBViz);
        HashMap<String, String> severity_mapping_codes = new HashMap<>();// itm_warning_code = dataMap.getString(ITMWarningCode);
        severity_mapping_codes.put("WARNING", dataMap.getString(ITMWarningCode));
        severity_mapping_codes.put("CRITICAL", dataMap.getString(ITMCriticalCode));
        severity_mapping_codes.put("CLEAR", dataMap.getString(ITMClearCode));
        int poll_interval = dataMap.getInt(pollInterval);
        setH2ConnectionPool();
        
        try{
            Connection deletecon = this.connectionPool.getConnection();
            deletecon.setAutoCommit(false);
            long old_entries_limit = System.currentTimeMillis()-86400000;

            deletecon.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            Statement deletestm = deletecon.createStatement();
            
            String delete_old_entries = "delete from events where SENT_DATE <= "+Long.toString(old_entries_limit);
            
            LOGGER.log(Level.INFO, "{}: Deleting old entries from log: {}", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), delete_old_entries});
            deletestm.execute(delete_old_entries);
            
            delete_old_entries = "delete from temp_events";
            LOGGER.log(Level.INFO, "{}: Deleting old entries from log: {}", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), delete_old_entries});
            deletestm.execute(delete_old_entries);
            deletecon.commit();
            connectionUtil controllerConnection = new connectionUtil(metric_prefix, controller_account, severity_mapping_codes);
            controllerConnection.writeMetric("Job excution count", "AVERAGE", "1");
            Application[] app_list = controllerConnection.getApplications(controller_url, controller_user, controller_account, controller_key, alert_server_viz, alert_db_viz);

            ThreadGroup appThreadGroup = new ThreadGroup("appThreadGroup");
            if (app_list != null){
                for (Application application:app_list){
                    LOGGER.log(Level.INFO, "{}: Application id: {}, Applicarion name: {}",new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(),application.getId(), application.getName()});
                    Connection con = this.connectionPool.getConnection();
                    con.setAutoCommit(false);
                    con.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                    Runnable getEventsThreadRunnable = new getEventsThread(application.getId(), 
                                                                          application.getName(), 
                                                                          controller_url, 
                                                                          controller_user, 
                                                                          controller_account, 
                                                                          controller_key, 
                                                                          poll_interval, 
                                                                          con,
                                                                          metric_prefix,
                                                                          integration_hostname, 
                                                                          integration_port, 
                                                                          integration_protocol,
                                                                          severity_mapping_codes);
                    new Thread(appThreadGroup, getEventsThreadRunnable).start();
                }
                boolean threadsEnded = false;
                int count = 0;
                while (threadsEnded!=true){
                    count++;
                    if (appThreadGroup.activeCount()==0){
                        threadsEnded = true;
                        this.connectionPool.dispose();
                        LOGGER.log(Level.INFO, "{}: Disposing connection pool.", new Object(){}.getClass().getEnclosingMethod().getName());
                    }
                    else{
                        if (count<=1) LOGGER.log(Level.INFO, "{}: There threads still active, waiting for them to finish..", new Object(){}.getClass().getEnclosingMethod().getName());
                        Thread.sleep(100);
                    }
                }
            }
        }
        catch(SQLException ex){
            LOGGER.log(Level.ERROR, "{}: There was an exception getting a connection from connection pool: ", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), ex.getMessage()});
        }
        catch(InterruptedException ex2){
            LOGGER.log(Level.ERROR, "{}: There was an exception sleeping the trhead.", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), ex2.getMessage()});
        }
    }
}

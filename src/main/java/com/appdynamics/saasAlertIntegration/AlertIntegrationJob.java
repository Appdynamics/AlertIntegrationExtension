/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.appdynamics.saasAlertIntegration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.roxstudio.utils.CUrl;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import com.appdynamics.saasAlertIntegration.connectionUtil;
import com.appdynamics.saasAlertIntegration.getEventsThread;
import java.sql.Connection;
import java.sql.SQLException;
import org.h2.jdbcx.JdbcConnectionPool;

/**
 *
 * @author igor.simoes
 */
public class AlertIntegrationJob implements Job{

    final private static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    
    public static final String controllerURL = "controller_url";
    public static final String controllerUser = "controller_user";
    public static final String controllerAccount = "controller_account";
    public static final String controllerKey = "controller_key";
    public static final String pollInterval = "poll_interval";
    public static final String MetricRootProperty = "metric_prefix";
    public static final String integrationHostname = "integration_hostname";
    public static final String integrationPort = "integration_port";
    public static final String integrationProtocol = "integration_protocol";
    private JdbcConnectionPool connectionPool;
    
    private void setH2ConnectionPool(){
        this.connectionPool = JdbcConnectionPool.create(
        "jdbc:h2:file:./appd_events_db;TRACE_LEVEL_FILE=0;TRACE_LEVEL_SYSTEM_OUT=0", "appd", "appdintegration");
        System.out.println("Default max connections: "+this.connectionPool.getMaxConnections());
        this.connectionPool.setMaxConnections(500);
        System.out.println("Updated max connections: "+this.connectionPool.getMaxConnections());
    }
    
    public void execute(JobExecutionContext context) throws JobExecutionException{
        System.out.println("Executing...");
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();
        String controller_url = dataMap.getString(controllerURL); 
        String controller_user = dataMap.getString(controllerUser); 
        String controller_account = dataMap.getString(controllerAccount); 
        String controller_key = dataMap.getString(controllerKey);
        String metric_prefix = dataMap.getString(MetricRootProperty);
        String integration_hostname = dataMap.getString(integrationHostname);
        String integration_port = dataMap.getString(integrationPort);
        String integration_protocol = dataMap.getString(integrationProtocol);
        int poll_interval = dataMap.getInt(pollInterval);
        setH2ConnectionPool();

        connectionUtil controllerConnection = new connectionUtil(metric_prefix, controller_account);
        controllerConnection.writeMetric("Job excution count", "AVERAGE", "1");
        Application[] app_list = controllerConnection.getApplications(controller_url, controller_user, controller_account, controller_key);
        
        //int monitored_applications = 2;
        //int i = 0;
        for (Application application:app_list){
            //if (i < monitored_applications){
                System.out.println(application.getId()+" - "+application.getName());
                
                try{
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
                                                                          integration_protocol);
                    new Thread(getEventsThreadRunnable).start();
                }
                catch(SQLException ex){
                    LOGGER.log(Level.SEVERE, "{0}: There was an exception getting a connection from connection pool: {1}", new Object[]{Thread.currentThread().getName(), ex.getMessage()});
                }
                
            /*}
            i++;*/
        }
        //this.connectionPool.dispose();
    }
    /*public void execute(JobExecutionContext context)
	throws JobExecutionException {
		
		System.out.println("Hello Integration!");	
    }*/
}

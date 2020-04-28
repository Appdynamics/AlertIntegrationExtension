/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.appdynamics.saasAlertIntegration;

import java.util.Date;
import com.appdynamics.saasAlertIntegration.AlertIntegrationJob;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;
//import java.util.logging.Level;
//import java.util.logging.Logger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import java.util.LinkedList;
import java.util.Queue;
import com.roxstudio.utils.CUrl;
import com.roxstudio.utils.CUrl.IO;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.sql.Connection;
import org.h2.jdbcx.JdbcConnectionPool;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;

/**
 *
 * @author igor.simoes
 */

/*class ControllerThread extends Thread implements Job 
{
    
    
}*/

public class JobTrigger {
    
        final private static Logger LOGGER = LogManager.getRootLogger();//Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        
        private static String validateITMSeverityCodes(String code){
            String validatedCode;
            int codeConversion = Integer.parseInt(code);
            if ((codeConversion > 6)||(codeConversion < 0)||(codeConversion == 2)||(codeConversion == 1)){
                validatedCode = "3";
            }
            else{
                validatedCode = code;
            }
            
            return validatedCode;
        }
        
	public static void main(String[] args) throws Exception {
                //setUpLogger();
                //LOGGER.log(Level.WARN, arg1);
		LOGGER.log(Level.INFO, "{}: Starting Alert Integration...", new Object(){}.getClass().getEnclosingMethod().getName());
		Queue<String> waitingQueue = new LinkedList<>();
                ConfigReader configuration = new ConfigReader();
                try {
                    //JdbcConnectionPool connectionPool = setH2ConnectionPool();
                    LOGGER.log(Level.INFO, "{}: Reading configuration from file config.properties.", new Object(){}.getClass().getEnclosingMethod().getName());
                    Properties configProperties = configuration.getPropValues("config.properties");
                    String controllerURL = configProperties.getProperty("controller_url");
                    String controllerUser = configProperties.getProperty("controller_user");
                    String controllerAccount = configProperties.getProperty("controller_account");
                    String controllerKey  = configProperties.getProperty("controller_key");
                    String read_interval  = configProperties.getProperty("read_interval");
                    String MetricRootProperty = configProperties.getProperty("MetricRootProperty");
                    String integration_hostname = configProperties.getProperty("integration_hostname");
                    String integration_port = configProperties.getProperty("integration_port");
                    String integration_protocol = configProperties.getProperty("integration_protocol");
                    String alert_server_viz = configProperties.getProperty("alert_server_viz");
                    String alert_db_viz = configProperties.getProperty("alert_db_viz");
                    String itm_warning_code = validateITMSeverityCodes(configProperties.getProperty("itm_warning_code"));
                    String itm_critical_code = validateITMSeverityCodes(configProperties.getProperty("itm_critical_code"));
                    String itm_clear_code = validateITMSeverityCodes(configProperties.getProperty("itm_clear_code"));
                    String update_incidents = configProperties.getProperty("update_incidents");
                    if (update_incidents == null || (!update_incidents.equals("true") && !update_incidents.equals("false")))
                        update_incidents = "false";
                    LOGGER.log(Level.INFO, "{}: Finished reading configuration properties.", new Object(){}.getClass().getEnclosingMethod().getName());
                    
                    //Defining the Job
                    JobDetail job = JobBuilder.newJob(AlertIntegrationJob.class)
				.withIdentity("ControllerThread", "group1").build();
                    
                    LOGGER.log(Level.INFO, "{}: Setting up jobDataMap.", new Object(){}.getClass().getEnclosingMethod().getName());
                    job.getJobDataMap().put(AlertIntegrationJob.controllerURL, controllerURL);
                    job.getJobDataMap().put(AlertIntegrationJob.controllerUser, controllerUser);
                    job.getJobDataMap().put(AlertIntegrationJob.controllerAccount, controllerAccount);
                    job.getJobDataMap().put(AlertIntegrationJob.controllerKey, controllerKey);
                    job.getJobDataMap().put(AlertIntegrationJob.pollInterval, read_interval);
                    job.getJobDataMap().put(AlertIntegrationJob.MetricRootProperty, MetricRootProperty);
                    job.getJobDataMap().put(AlertIntegrationJob.integrationHostname, integration_hostname);
                    job.getJobDataMap().put(AlertIntegrationJob.integrationPort, integration_port);
                    job.getJobDataMap().put(AlertIntegrationJob.integrationProtocol, integration_protocol);
                    job.getJobDataMap().put(AlertIntegrationJob.alertServerViz, alert_server_viz);
                    job.getJobDataMap().put(AlertIntegrationJob.alertDBViz, alert_db_viz);
                    job.getJobDataMap().put(AlertIntegrationJob.ITMWarningCode, itm_warning_code);
                    job.getJobDataMap().put(AlertIntegrationJob.ITMCriticalCode, itm_critical_code);
                    job.getJobDataMap().put(AlertIntegrationJob.ITMClearCode, itm_clear_code);
                    job.getJobDataMap().put(AlertIntegrationJob.UpdateIncidents, update_incidents);
                    LOGGER.log(Level.INFO, "{}: JobDataMap defined successfully.", new Object(){}.getClass().getEnclosingMethod().getName());
                    LOGGER.log(Level.INFO, "{}: Building and scheduling job.", new Object(){}.getClass().getEnclosingMethod().getName());
                    
                    // Trigger the job to run on the next round minute
                    Trigger trigger = TriggerBuilder
                                    .newTrigger()
                                    .withIdentity("AlertIntegrationTrigger", "group1")
                                    .withSchedule(
                                                    SimpleScheduleBuilder.simpleSchedule()
                                                                    .withIntervalInSeconds(Integer.parseInt(read_interval)).repeatForever())
                                    .build();
                    
                    // schedule it
                    Scheduler scheduler = new StdSchedulerFactory().getScheduler();
                    scheduler.start();
                    scheduler.scheduleJob(job, trigger);
                }
                catch (Exception e) {
                    LOGGER.log(Level.WARN, "{}: Could not read configuration file: {}", new Object[]{new Object(){}.getClass().getEnclosingMethod().getName(), e.getMessage()});
                    e.printStackTrace();
                }
                
                
                //starting the controller thread

	}
}

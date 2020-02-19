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
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
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
import javax.swing.text.Document;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.sql.Connection;
import java.util.logging.LogRecord;
import org.h2.jdbcx.JdbcConnectionPool;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 *
 * @author igor.simoes
 */

/*class ControllerThread extends Thread implements Job 
{
    
    
}*/

public class JobTrigger {
    
        final private static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        
        private static void setUpLogger(){
           try{
               FileHandler fileTxt = new FileHandler("AlertIntegration.log", 10000000, 5, true);
               fileTxt.setFormatter(new SimpleFormatter() {
                   private static final String format = "[%1$tF %1$tT] [%2$-7s] %3$s %n";
                   
                   @Override
                   public synchronized String format(LogRecord lr) {
                        return String.format(format,
                             new Date(lr.getMillis()),
                             lr.getLevel().getLocalizedName(),
                             lr.getMessage()
                        );
                   }
               });
               
               //fileTxt.setFormatter(loggerFormatter);
               LOGGER.addHandler(fileTxt);
               LOGGER.setLevel(Level.INFO);
               LOGGER.setUseParentHandlers(false);
//               LOGGER.setUseParentHandlers(false);//removeHandler(ConsoleHandler.class);
           } 
           catch (IOException e){
               System.out.println("Unable to write log: "+ e.getMessage());
           }
        }
        
	public static void main(String[] args) throws Exception {
                setUpLogger();
		LOGGER.log(Level.INFO, "Starting Alert Integration...");
		Queue<String> waitingQueue = new LinkedList<>();
                ConfigReader configuration = new ConfigReader();
                try {
                    //JdbcConnectionPool connectionPool = setH2ConnectionPool();
                    LOGGER.log(Level.INFO, "Reading configuration from file config.properties.");
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
                    LOGGER.log(Level.INFO, "Finished reading configuration properties.");
                    
                    //Defining the Job
                    JobDetail job = JobBuilder.newJob(AlertIntegrationJob.class)
				.withIdentity("ControllerThread", "group1").build();
                    
                    LOGGER.log(Level.INFO, "Setting up jobDataMap.");
                    job.getJobDataMap().put(AlertIntegrationJob.controllerURL, controllerURL);
                    job.getJobDataMap().put(AlertIntegrationJob.controllerUser, controllerUser);
                    job.getJobDataMap().put(AlertIntegrationJob.controllerAccount, controllerAccount);
                    job.getJobDataMap().put(AlertIntegrationJob.controllerKey, controllerKey);
                    job.getJobDataMap().put(AlertIntegrationJob.pollInterval, read_interval);
                    job.getJobDataMap().put(AlertIntegrationJob.MetricRootProperty, MetricRootProperty);
                    job.getJobDataMap().put(AlertIntegrationJob.integrationHostname, integration_hostname);
                    job.getJobDataMap().put(AlertIntegrationJob.integrationPort, integration_port);
                    job.getJobDataMap().put(AlertIntegrationJob.integrationProtocol, integration_protocol);
                    LOGGER.log(Level.INFO, "JobDataMap defined successfully.");
                    LOGGER.log(Level.INFO, "Building and scheduling job.");
                    
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
                        LOGGER.log(Level.WARNING, "Could not read configuration file: {0}", e.getMessage());
                        e.printStackTrace();
                }
                
                
                //starting the controller thread

	}
}

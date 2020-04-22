/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.appdynamics.saasAlertIntegration;

import org.apache.logging.log4j.Level;

/**
 *
 * @author igor.simoes
 */
public class Event { 

    private String deepLinkUrl;//": "https://ibm-metalurgicagerdau-nonprod.saas.appdynamics.com/#location=APP_INCIDENT_DETAIL_MODAL&incident=10356788&application=19823",
    private String severity;//": "CRITICAL",
    private triggeredEntityDefinition triggeredEntityDefinition = new triggeredEntityDefinition();
    private long startTimeInMillis;//": 1580248015000,
    private long detectedTimeInMillis;//": 0,
    private long endTimeInMillis;//": 0,
    private String name;//": "Machine Agent availability too low",
    private String description;//": "AppDynamics has detected a problem with Node <b>SCM-SG1_ggadb010_SG1_11<\/b>.<br><b>Machine Agent availability too low<\/b> continues to violate with <b>critical<\/b>.<br>All of the following conditions were found to be violating<br>For Node <b>SCM-SG1_ggadb010_SG1_11<\/b>:<br>1) Machine Agent Availability<br><b>Availability's<\/b> value <b>0.00<\/b> was <b>less than<\/b> the threshold <b>95.00<\/b> for the last <b>5<\/b> minutes<br>2) App Agent Availability<br><b>Availability's<\/b> value <b>1.00<\/b> was <b>less than<\/b> the threshold <b>95.00<\/b> for the last <b>5<\/b> minutes<br>",
    private String id;//": 10356788,
    private affectedEntityDefinition affectedEntityDefinition = new affectedEntityDefinition();  
    private String incidentStatus;//": "OPEN"
    private String ITMCode;//This will hold the ITM code being sent

    public triggeredEntityDefinition getTriggeredEntityDefinition() {
          return triggeredEntityDefinition;
    }
    
    public void setTriggeredEntityDefinition(triggeredEntityDefinition triggeredEntityDefinition) {
        this.triggeredEntityDefinition = triggeredEntityDefinition;
    }
    
    public affectedEntityDefinition getAffectedEntityDefinition() {
          return affectedEntityDefinition;
    }
    
    public void setAffectedEntityDefinition(affectedEntityDefinition affectedEntityDefinition) {
        this.affectedEntityDefinition = affectedEntityDefinition;
    }
    
    public String getDeepLinkUrl() {
          return deepLinkUrl;
    }
    
    public void setDeepLinkUrl(String deepLinkUrl) {
        this.deepLinkUrl = deepLinkUrl;
    }
    
    public String getSeverity() {
        if (severity != null) {
            if ((getIncidentStatus().equals("RESOLVED"))||(getIncidentStatus().equals("CANCELED"))||((getIncidentStatus().equals("CANCELED")))){
                this.severity = "CLEAR";
            }
            return severity;
        }
        else return getIncidentStatus();
    }
    
    public int getSeverityCode() {
        int severity_code = 0;
        if ((getIncidentStatus().equals("RESOLVED"))||(getIncidentStatus().equals("CANCELED"))||(getIncidentStatus().equals("CANCELLED"))){
            this.severity = "CLEAR";
            severity_code = 0;
        }
        else{
            severity = getSeverity();
            if (severity.equals("WARNING")) severity_code = 1;
            else
                if (severity.equals("CRITICAL")) severity_code = 2;
                else severity_code = -1;
        }
        
        return severity_code;
    }
    
    public void setITMSeverityCode(String code){
        this.ITMCode = code;
    }
    
    public void setSeverity(String severity) {
        if ((getIncidentStatus().equals("RESOLVED"))||(getIncidentStatus().equals("CANCELED"))||(getIncidentStatus().equals("CANCELLED"))){
            severity = "CLEAR";
        }
        this.severity = severity;
    }
    
    public long getStartTimeInMillis() {
          return startTimeInMillis;
    }
    
    public void setStartTimeInMillis(long startTimeInMillis) {
        this.startTimeInMillis = startTimeInMillis;
    }
    
    public long getDetectedTimeInMillis() {
          return detectedTimeInMillis;
    }
    
    public void setDetectedTimeInMillis(long detectedTimeInMillis) {
        this.detectedTimeInMillis = detectedTimeInMillis;
    }
    
    public long getEndTimeInMillis() {
          return endTimeInMillis;
    }
    
    public void setEndTimeInMillis(long endTimeInMillis) {
        this.endTimeInMillis = endTimeInMillis;
    }
    
    public String getName() {
          return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
          return description;
    }
    
    public String getReducedDescription(){
        String reducedDescription = description;
        //removing HTML markup
        reducedDescription = reducedDescription.replace("<b>", "");
        reducedDescription = reducedDescription.replace("</b>", "");
        reducedDescription = reducedDescription.replace("<\\/b>", "");
        reducedDescription = reducedDescription.replace("<br>", "");
        //removing unnecessary parts of the msg
        int reducedDescriptionStart = reducedDescription.indexOf("1)");
        if (reducedDescriptionStart == -1){
            reducedDescriptionStart = 0;
        }
        reducedDescription = reducedDescription.substring(reducedDescriptionStart);
        int reducedDescriptionEnd = 255;
        
        /*if (reducedDescriptionEnd == -1){
            if (description.length() < 255) reducedDescriptionEnd = description.length();
            else reducedDescriptionEnd = 255;
        }*/

        if (reducedDescription.length()>255)
            reducedDescription = reducedDescription.substring(0, reducedDescriptionEnd);// removed begin of string, now leaving a 255 char long message
    
        
        return reducedDescription;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getId() {
          return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getIncidentStatus() {
          return incidentStatus;
    }
    
    public void setIncidentStatus(String incidentStatus) {
        this.incidentStatus = incidentStatus;
    }
    
    @Override
    public String toString() {
        return "Event [deepLinkUrl=" + deepLinkUrl + 
               ", severity=" + severity + 
               ", severity_code=" + ITMCode + 
               ", triggeredEntityDefinition=" +triggeredEntityDefinition + 
               ", startTimeInMillis=" +startTimeInMillis + 
               ", detectedTimeInMillis=" +detectedTimeInMillis + 
               ", endTimeInMillis=" +endTimeInMillis+ 
               ", name=" + name + 
               ", description=" +description+ 
               ", reducedDescription=" + getReducedDescription() + 
               ", id=" + id+ 
               ", affectedEntityDefinition=" + affectedEntityDefinition + 
               ", incidentStatus=" +incidentStatus + "]";
    }

    
}

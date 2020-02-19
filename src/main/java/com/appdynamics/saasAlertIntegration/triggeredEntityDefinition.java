/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.appdynamics.saasAlertIntegration;

/**
 *
 * @author igor.simoes
 */
public class triggeredEntityDefinition {
    //"triggeredEntityDefinition":   {
    private String entityType;//": "POLICY",
    private String name;//": "Machine Agent availability too low",
    private String entityId;//": 99269
    
    public String getEntityType() {
          return entityType;
    }
    
    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }
    
    public String getName() {
          return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getEntityId() {
          return entityId;
    }
    
    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }
    
    @Override
    public String toString() {
        return "TriggeredEntityDefinition [entityType=" + entityType + 
               ", name=" + name + 
               ", entityId=" +entityId + "]";
    }
  //},
}

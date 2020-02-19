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
public class Application {
    private String name;
    private String description;
    private String id;
    private String accountGuid;
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    public void setDescription(String name) {
        this.description = description;
    }
    
    public String getId() {
        return id;
    }
    public void setId(String name) {
        this.id = id;
    }
    
    public String getAccountGuid() {
        return accountGuid;
    }
    public void setAccountGuid(String name) {
        this.accountGuid = accountGuid;
    }
    
    @Override
    public String toString() {
        return "Application [id=" + id + ", name=" + name + ", description=" + name + ", accountGuid=" + accountGuid + "]";
    }
}

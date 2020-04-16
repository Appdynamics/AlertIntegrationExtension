# AlertIntegrationExtension
This is an extension to integrate alerts to OnPrem infrastructure. This a job that keeps getting events from the controller and forwarding them to the local http endpoint. Right the extension is focused on sending messages to ITM HTTP Probe.

## How to use this extension

Download the release package and unpack under <machine-agent-home>/monitors. This will create the extension folder with some configuration files:

1. quartz.properties
   - No need to change this
2. config.properties
   - Change the values with controller information and ITM endpoint information
3. itm_appd_mapping.properties
   - This changes the way fields are mapped from AppDynamics to ITM. Currently we cannot add new ITM fields, but we can configure the existing fields with any combination of the available variables.

Restart the machine agent and the extension will start:

1. It will check events from the controller
2. It will send events to the ITM
3. It will write metrics of each steps so these metrics can be monitored to check if the integration is working properly.


## New to this version

1. Config properties now allow to specify severity code mapping. Just add there the desired code for WARNING, CRITICAL and CLEAR/CANCELED on destination integration.

#!/bin/bash
runningPID=$(ps -ef | grep sensoradapter_gps_hight.jar | grep -v grep | awk '{print $2}');
if [[ $runningPID != "" ]]; then
   echo "true";
else
   echo "false";
fi
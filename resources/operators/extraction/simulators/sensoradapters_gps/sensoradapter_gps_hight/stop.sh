#!/bin/bash
sudo kill -9 $(ps -ef | grep sensoradapter_gps_hight.jar | grep -v grep | awk '{print $2}')
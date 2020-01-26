#!/bin/bash
sudo kill -9 $(ps -ef | grep sensoradapter_gps_stub.py | grep -v grep | awk '{print $2}')
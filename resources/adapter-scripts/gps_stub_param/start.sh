#!/bin/bash
cd $1
nohup python3 sensoradapter_gps_stub.py $2 > start.log &
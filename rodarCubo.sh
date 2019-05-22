#!/bin/bash

cd /vmspace/workspace/humble/
sudo rm -rf RELEASE/
mvn clean package
java -jar RELEASE/humble-mk-i.jar


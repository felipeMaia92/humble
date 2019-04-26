#!/bin/bash

cd /vmspace/workspace/humble/
sudo rm -rf RELEASE/
sudo rm -rf arquivos/
mvn clean package
java -jar RELEASE/humble-mk-i.jar


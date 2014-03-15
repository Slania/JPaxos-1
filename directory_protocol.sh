#!/usr/local/bin/bash

java -ea -server -Dlogback.configurationFile=logback.xml -cp bin:lib/logback-classic-1.0.13.jar:lib/logback-core-1.0.13.jar:lib/slf4j-api-1.7.5.jar:lib/postgresql-9.3-1100.jdbc41.jar:lib/joda-time-2.3.jar lsr.paxos.test.directory.DirectoryProtocol $1

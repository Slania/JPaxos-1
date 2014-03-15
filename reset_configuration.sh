#!/usr/local/bin/bash

echo `psql -U postgres -h localhost -d $1 -f reset_configuration.sql`
rm command_packet_handler_times*.txt
rm -rf jpaxosLogs/*

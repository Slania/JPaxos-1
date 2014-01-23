#!/bin/bash

echo `pg_dump -U $1 -h $2  $3 > $3.sql`


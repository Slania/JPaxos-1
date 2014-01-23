#!/bin/bash

echo `dropdb -U $1 -h $2  $3`
echo `createdb -U $1 -h $2  $3`
echo `psql -U $1 -h $2 -d $3 -f $4`


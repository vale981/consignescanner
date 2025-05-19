#! /usr/bin/env bash

rm consignation.db
sqlite3 consignation.db <<EOS
.mode csv
.import consignation.csv containers
EOS

sqlite-utils transform consignation.db containers \
    --type Consigne FLOAT \

#! /usr/bin/env bash

rm -f consignaction.db
sqlite3 consignaction.db <<EOS
.mode csv
.import consignaction.csv containers
EOS

sqlite-utils transform consignaction.db containers \
             --pk 'Code Barre' \
             --not-null 'Code Barre' \
             --not-null 'Consigne'

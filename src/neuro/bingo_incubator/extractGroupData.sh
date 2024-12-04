#!/bin/bash
trackedMethods=$1
filePath=$2
fileName=$3

grep "<group " $filePath/$fileName | tr -d '"' |  tr -d ">" > $filePath/groupsExtracted
awk 'BEGIN{FS="="}{print $3, $6}' $filePath/groupsExtracted | awk '($1 != '$trackedMethods'){for(i=3;i<=NF;++i)print $i}' | tr '[:space:]' '\n' | sort | uniq > $filePath/GroupUniqueLocalizedMethods.dat
awk 'BEGIN{FS="="}{print $3, $4}' $filePath/groupsExtracted | awk '{print $1, $3}' | sort -n > $filePath/groupsExtracted2
sort -nr +1 -2 $filePath/groupsExtracted2 > $filePath/groupsExtracted3
awk '{ x[$1]=x[$1] " " $2; } 
END { 
   for (k in x) print k,x[k];  
}' $filePath/groupsExtracted3 | sort -n > $filePath/MethodQueriesHistogram.dat

rm -f $filePath/groupsExtracted $filePath/groupsExtracted2 $filePath/groupsExtracted3

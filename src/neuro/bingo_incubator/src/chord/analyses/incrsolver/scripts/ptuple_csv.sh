#!/bin/bash

log_dir=$1
ptuple_file=$2
out_file=$3

all_rels="DIH DI reachableT RobjValAsgnInst DIC rootCM RgetInstFldInst RobjVarAsgnInst reachableCM RgetStatFldInst reachableCI RputInstFldInst DVDV CVC DVC RputStatFldInst CMCM CICM CFC FC"

rm -f $out_file

echo -n "Relations" >> $out_file 
for i in $all_rels
do
   echo -n " "$i >> $out_file
done
echo >> $out_file

echo -n "Participating_Lib_Tuples" >> $out_file
for i in $all_rels
do
   cnt=`grep -w $i $ptuple_file | wc -l`
   echo -n " "$cnt >> $out_file
done
echo >> $out_file

echo -n "Baseline_Lib_Tuples" >> $out_file
for i in $all_rels
do
   cnt_line=`grep "IS:RELATION LIB CNT:" $log_dir/log.txt | grep -w $i`
   cnt=`echo $cnt_line | awk '{print $5;}'`
   echo -n " "$cnt >> $out_file
done
echo >> $out_file

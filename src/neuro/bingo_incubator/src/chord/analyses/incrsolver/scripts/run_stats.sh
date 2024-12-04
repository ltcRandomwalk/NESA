#!/bin/bash

#app_list="elevator ftp hedc weblech-0.0.3 antlr avrora luindex lusearch hsqldb" 
app_list="ftp hedc" 
dest_dir=$1
max_dir=chord_output_incrsolver_times7
times_dir="chord_output_incrsolver_times1 chord_output_incrsolver_times2 chord_output_incrsolver_times3 chord_output_incrsolver_times4"

set_log_dir()
{
   if [ "$1" = "elevator" ]; then
      app_log_dir="$PJBENCH/elevator"
   elif [ "$1" = "hedc" ]; then
      app_log_dir="$PJBENCH/hedc"
   elif [ "$1" = "ftp" ]; then
      app_log_dir="$PJBENCH/ftp"
   elif [ "$1" = "weblech-0.0.3" ]; then
      app_log_dir="$PJBENCH/weblech-0.0.3"
   elif [ "$1" = "antlr" ]; then
      app_log_dir="$PJBENCH/dacapo/benchmarks/antlr"
   elif [ "$1" = "luindex" ]; then
      app_log_dir="$PJBENCH/dacapo/benchmarks/luindex"
   elif [ "$1" = "lusearch" ]; then
      app_log_dir="$PJBENCH/dacapo/benchmarks/lusearch"
   elif [ "$1" = "avrora" ]; then
      app_log_dir="$PJBENCH/dacapo/benchmarks/avrora"
   elif [ "$1" = "hsqldb" ]; then
      app_log_dir="$PJBENCH/dacapo/benchmarks/hsqldb"
   fi
}

for i in $app_list
do
   set_log_dir $i
   ./stats.sh $app_log_dir/chord_output_incrsolver_baseline/log.txt ${i}_baseline_
   ./stats.sh $app_log_dir/$max_dir/log.txt ${i}_times7_
   cp $app_log_dir/chord_output_incrsolver_baseline/*stat* $dest_dir
   cp $app_log_dir/$max_dir/*stat* $dest_dir

   for j in $times_dir
   do
      if [ -e $app_log_dir/$j ]; then
         ./stats.sh $app_log_dir/$j/log.txt ${i}_times7_
         cp $app_log_dir/$j/*stat* $dest_dir
      fi
   done
done

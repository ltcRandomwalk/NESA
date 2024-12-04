#!/bin/bash

curr_log_file=$1
fname_prefix=$2
dir_name=`dirname $curr_log_file`
rm -f $dir_name/iteration*

last_iteration=`grep "Iteration no\.:" $curr_log_file | tail -n 1 | awk '{print $6;}'`
if [ "$last_iteration" != "" ]; then
   begin_pattern=0
   for i in `seq 1 $last_iteration`
   do
      end_pattern=$i
      awk "/Iteration no\.: $begin_pattern/ {p=1}; p; /Iteration no\.: $end_pattern/ {p=0}" $curr_log_file > $dir_name/iteration_$i 
      begin_pattern=$i
   done
   awk "/Iteration no\.: $begin_pattern/ {p=1}; p; /Num root node summaries/ {p=0}" $curr_log_file > $dir_name/iteration_last 
fi
awk "/SWEEPING PASS/ {p=1}; p;" $curr_log_file > $dir_name/iteration_sweep 


echo "Iteration" "Total_Tuples" "Lib_Tuples" "Rule_Updates" "Dlog_Running_Time" "Num_Summaries_Loaded" "Increase_in_Lib_Tuples" > $dir_name/${fname_prefix}stats.csv

sum_rule_updates=0
sum_dlog_time=0
for f in `ls $dir_name/iteration_*`
do
   # number of rule updates
   grep "Updates:" $f > $dir_name/ups
   total_rule_updates=`awk '{sum += $2;} END {print sum;}' $dir_name/ups`
   rm -f $dir_name/ups
   sum_rule_updates=$((sum_rule_updates + total_rule_updates))

   # dlog run time
   dlog_run_time=`grep "DLOG RUN TIME" $f | awk '{print $4;}'`
   sum_dlog_time=$((sum_dlog_time + dlog_run_time))
   

   # Number of summaries loaded
   num_summaries=`grep "LOADING SUMMARIES FROM" $f | wc -l`

   # relation counts after saturation
   sat_total_cnt=`grep "TUPLE COUNT" $f | awk '{print $5;}'`
   sat_lib_cnt=`grep "TUPLE COUNT" $f | awk '{print $8;}'`

   # Total tuples contributed by summary loading
   grep "SAVING rel" $f | grep -v _app | grep -v _full > $dir_name/ld_cnt
   total_after_load=`awk '{sum += $5;} END {print sum;}' $dir_name/ld_cnt`
   lib_cnt_loaded_by_sum=$((total_after_load - sat_total_cnt))
   rm -f $dir_name/ld_cnt


   f_base=`basename $f`
   # Dump stats in csv format
   if [ $lib_cnt_loaded_by_sum -lt 0 ]; then
      echo $f_base $sat_total_cnt $sat_lib_cnt $total_rule_updates $dlog_run_time $num_summaries >> $dir_name/${fname_prefix}stats.csv
   else
      echo $f_base $sat_total_cnt $sat_lib_cnt $total_rule_updates $dlog_run_time $num_summaries $lib_cnt_loaded_by_sum >> $dir_name/${fname_prefix}stats.csv
   fi

   echo $f_base
   echo "After frontier saturation:" "Total tuples:" $sat_total_cnt "   Lib tuples:" $sat_lib_cnt "   Rule updates:" $total_rule_updates "   Dlog running time:" $dlog_run_time
   if [ $lib_cnt_loaded_by_sum -lt 0 ]; then
      echo "Number of summaries loaded:" $num_summaries 
   else
      echo "Number of summaries loaded:" $num_summaries "   Increase in lib tuples:" $lib_cnt_loaded_by_sum
   fi
   echo
done
   echo "Total" "" "" $sum_rule_updates $sum_dlog_time >> $dir_name/${fname_prefix}stats.csv

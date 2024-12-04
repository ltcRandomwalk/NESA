#!/bin/bash

# Variables
training_apps_var=$INCRSOLVER_TRAINING_APPS
training_apps=`echo $training_apps_var | tr "," " "`
test_app=$INCRSOLVER_TEST_APP
all_apps="$training_apps $test_app"

summary_dir=$1
scope_dir=$2
reorder="true"
iterations=7
num_training_apps=0

kobj_analysis_options="-D chord.kobj.k=1"
analysis_options=$kobj_analysis_options
scope_options=""



# Functions

set_number_of_training_apps()
{
   for app in $training_apps
   do
      num_training_apps=$((num_training_apps+1))
   done
}


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


dump_domc_for_all_apps()
{
   for app in $all_apps 
   do
      set_log_dir $app
      if [[ "$num_training_apps" -gt 1 ]]; then
         scope_options=" -D chord.reuse.scope=true -D chord.methods.file=$app_log_dir/$scope_dir/methods.txt -D chord.reflect.file=$app_log_dir/$scope_dir/reflect.txt"
      elif [ "$training_apps" != "$test_app" ]; then 
         scope_options=" -D chord.reuse.scope=true -D chord.methods.file=$app_log_dir/$scope_dir/methods.txt -D chord.reflect.file=$app_log_dir/$scope_dir/reflect.txt"
      else
         scope_options=""
      fi
      if [ "$training_apps" != "$test_app" ]; then 
         echo ./runner.pl -mode=serial -program=$app -analysis=incr-domcdumper $analysis_options $scope_options -D chord.incrsolver.summaryDir=$summary_dir -D chord.incrsolver.appName=$app
         ./runner.pl -mode=serial -program=$app -analysis=incr-domcdumper $analysis_options $scope_options -D chord.incrsolver.summaryDir=$summary_dir -D chord.incrsolver.appName=$app
      fi
   done
}

dump_libaxioms_for_all_apps()
{
   for app in $all_apps 
   do
      set_log_dir $app
      if [[ "$num_training_apps" -gt 1 ]]; then
         scope_options=" -D chord.reuse.scope=true -D chord.methods.file=$app_log_dir/$scope_dir/methods.txt -D chord.reflect.file=$app_log_dir/$scope_dir/reflect.txt"
      elif [ "$training_apps" != "$test_app" ]; then 
         scope_options=" -D chord.reuse.scope=true -D chord.methods.file=$app_log_dir/$scope_dir/methods.txt -D chord.reflect.file=$app_log_dir/$scope_dir/reflect.txt"
      else
         scope_options=""
      fi
      if [ "$training_apps" != "$test_app" ]; then 
         echo ./runner.pl -mode=serial -program=$app -analysis=incr-libaxiomdumper $analysis_options $scope_options -D chord.incrsolver.summaryDir=$summary_dir -D chord.incrsolver.appName=$app -D chord.incrsolver.reorderDoms=$reorder
         ./runner.pl -mode=serial -program=$app -analysis=incr-libaxiomdumper $analysis_options $scope_options -D chord.incrsolver.summaryDir=$summary_dir -D chord.incrsolver.appName=$app -D chord.incrsolver.reorderDoms=$reorder
      fi
   done
}


dump_summaries_for_training_apps()
{
   for app in $training_apps 
   do
      set_log_dir $app
      if [[ "$num_training_apps" -gt 1 ]]; then
         scope_options=" -D chord.reuse.scope=true -D chord.methods.file=$app_log_dir/$scope_dir/methods.txt -D chord.reflect.file=$app_log_dir/$scope_dir/reflect.txt"
      elif [ "$training_apps" != "$test_app" ]; then
         scope_options=" -D chord.reuse.scope=true -D chord.methods.file=$app_log_dir/$scope_dir/methods.txt -D chord.reflect.file=$app_log_dir/$scope_dir/reflect.txt"
      else
         scope_options=""
      fi
      echo ./runner.pl -mode=serial -program=$app -analysis=derivsz $analysis_options $scope_options -D chord.incrsolver.summaryDir=$summary_dir -D chord.incrsolver.appName=$app -D chord.incrsolver.reorderDoms=$reorder
      ./runner.pl -mode=serial -program=$app -analysis=derivsz $analysis_options $scope_options -D chord.incrsolver.summaryDir=$summary_dir -D chord.incrsolver.appName=$app -D chord.incrsolver.reorderDoms=$reorder
   done
}


run_baseline_for_test_app()
{
   set_log_dir $test_app
   if [[ "$num_training_apps" -gt 1 ]]; then
      scope_options=" -D chord.reuse.scope=true -D chord.methods.file=$app_log_dir/$scope_dir/methods.txt -D chord.reflect.file=$app_log_dir/$scope_dir/reflect.txt"
   elif [ "$training_apps" != "$test_app" ]; then
      scope_options=" -D chord.reuse.scope=true -D chord.methods.file=$app_log_dir/$scope_dir/methods.txt -D chord.reflect.file=$app_log_dir/$scope_dir/reflect.txt"
   else
      scope_options=""
   fi
   echo ./runner.pl -mode=serial -program=$test_app -analysis=incrsolver $analysis_options $scope_options -D chord.incrsolver.summaryDir=$summary_dir -D chord.incrsolver.appName=$test_app -D chord.incrsolver.reorderDoms=$reorder -D chord.incrsolver.baseline=true -D chord.incrsolver.printRelCount=true
   ./runner.pl -mode=serial -program=$test_app -analysis=incrsolver $analysis_options $scope_options -D chord.incrsolver.summaryDir=$summary_dir -D chord.incrsolver.appName=$test_app -D chord.incrsolver.reorderDoms=$reorder -D chord.incrsolver.baseline=true -D chord.incrsolver.printRelCount=true
   mv $app_log_dir/chord_output_incrsolver $app_log_dir/chord_output_incrsolver_baseline
}


run_solver_for_test_app()
{
   set_log_dir $test_app
   if [[ "$num_training_apps" -gt 1 ]]; then
      scope_options=" -D chord.reuse.scope=true -D chord.methods.file=$app_log_dir/$scope_dir/methods.txt -D chord.reflect.file=$app_log_dir/$scope_dir/reflect.txt"
   elif [ "$training_apps" != "$test_app" ]; then
      scope_options=" -D chord.reuse.scope=true -D chord.methods.file=$app_log_dir/$scope_dir/methods.txt -D chord.reflect.file=$app_log_dir/$scope_dir/reflect.txt"
   else
      scope_options=""
   fi
   echo ./runner.pl -mode=serial -program=$test_app -analysis=incrsolver $analysis_options $scope_options -D chord.incrsolver.summaryDir=$summary_dir -D chord.incrsolver.appName=$test_app -D chord.incrsolver.reorderDoms=$reorder -D chord.incrsolver.times=$iterations -D chord.incrsolver.printRelCount=true
   ./runner.pl -mode=serial -program=$test_app -analysis=incrsolver $analysis_options $scope_options -D chord.incrsolver.summaryDir=$summary_dir -D chord.incrsolver.appName=$test_app -D chord.incrsolver.reorderDoms=$reorder -D chord.incrsolver.times=$iterations -D chord.incrsolver.printRelCount=true
}


# Main program starts here

set_number_of_training_apps
dump_domc_for_all_apps
echo
dump_libaxioms_for_all_apps
echo
dump_summaries_for_training_apps
echo
run_baseline_for_test_app
echo
run_solver_for_test_app
echo


exit
ls
cd dynaboost
source init.sh
ls
cd eval
./instrument-all.sh 
ps -few
./instrument-all.sh 
ps -few
kill 324072
kill 324069
kill 323999
ps -few

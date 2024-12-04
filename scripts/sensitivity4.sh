cd $ARTIFACT_ROOT_DIR/src/neuro/neuroanalysis/Driver
python driver.py -pb moldyn toba-s+hedc > /dev/null 2> /dev/null &
python driver.py -pb montecarlo toba-s+hedc > /dev/null 2> /dev/null &

python driver.py -pb jspider toba-s+hedc > /dev/null 2> /dev/null &
python driver.py -pb weblech toba-s+hedc > /dev/null 2> /dev/null &

python driver.py -pb javasrc-p toba-s+hedc > /dev/null 2> /dev/null &
python driver.py -pb ftp toba-s+hedc > /dev/null 2> /dev/null &
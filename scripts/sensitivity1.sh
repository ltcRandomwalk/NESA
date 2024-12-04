cd $ARTIFACT_ROOT_DIR/src/neuro/neuroanalysis/Driver
python driver.py -pb moldyn javasrc-p+montecarlo > /dev/null 2> /dev/null &

python driver.py -pb hedc javasrc-p+montecarlo > /dev/null 2> /dev/null &
python driver.py -pb jspider javasrc-p+montecarlo > /dev/null 2> /dev/null &
python driver.py -pb weblech javasrc-p+montecarlo > /dev/null 2> /dev/null &
python driver.py -pb toba-s javasrc-p+montecarlo > /dev/null 2> /dev/null &

python driver.py -pb ftp javasrc-p+montecarlo > /dev/null 2> /dev/null &
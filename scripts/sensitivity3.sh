cd $ARTIFACT_ROOT_DIR/src/neuro/neuroanalysis/Driver
python driver.py -pb moldyn weblech+jspider > /dev/null 2> /dev/null &
python driver.py -pb montecarlo weblech+jspider > /dev/null 2> /dev/null &
python driver.py -pb hedc weblech+jspider > /dev/null 2> /dev/null &

python driver.py -pb toba-s weblech+jspider > /dev/null 2> /dev/null &
python driver.py -pb javasrc-p weblech+jspider > /dev/null 2> /dev/null &
python driver.py -pb ftp weblech+jspider > /dev/null 2> /dev/null &
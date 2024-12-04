cd $ARTIFACT_ROOT_DIR/src/neuro/neuroanalysis/Driver
python driver.py -n moldyn small > /dev/null 2> /dev/null &
python driver.py -n montecarlo small > /dev/null 2> /dev/null &
python driver.py -n hedc small > /dev/null 2> /dev/null &
python driver.py -n jspider small > /dev/null 2> /dev/null &
python driver.py -n weblech small > /dev/null 2> /dev/null &
python driver.py -n toba-s small > /dev/null 2> /dev/null &
python driver.py -n javasrc-p small > /dev/null 2> /dev/null &
python driver.py -n ftp small > /dev/null 2> /dev/null &
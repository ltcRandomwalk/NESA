cd $ARTIFACT_ROOT_DIR/src/neuro/neuroanalysis/Driver
python driver.py -lspbr moldyn small > /dev/null 2> /dev/null &
python driver.py -lspbr montecarlo small > /dev/null 2> /dev/null &
python driver.py -lspbr hedc small > /dev/null 2> /dev/null &
python driver.py -lspbr jspider small > /dev/null 2> /dev/null &
python driver.py -lspbr weblech small > /dev/null 2> /dev/null &
python driver.py -lspbr toba-s small > /dev/null 2> /dev/null &
python driver.py -lspbr javasrc-p small > /dev/null 2> /dev/null &
python driver.py -lspbr ftp small > /dev/null 2> /dev/null &
cd $ARTIFACT_ROOT_DIR/src/neuro/neuroanalysis/Driver

python driver.py -pb montecarlo ftp+moldyn > /dev/null 2> /dev/null &
python driver.py -pb hedc ftp+moldyn > /dev/null 2> /dev/null &
python driver.py -pb jspider ftp+moldyn > /dev/null 2> /dev/null &
python driver.py -pb weblech ftp+moldyn > /dev/null 2> /dev/null &
python driver.py -pb toba-s ftp+moldyn > /dev/null 2> /dev/null &
python driver.py -pb javasrc-p ftp+moldyn > /dev/null 2> /dev/null &

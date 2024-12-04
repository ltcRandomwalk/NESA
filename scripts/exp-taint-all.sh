cd $ARTIFACT_ROOT_DIR/src/neuro/neuroanalysis/Driver
python taintdriver.py -blr app-324 > /dev/null 2> /dev/null &
python taintdriver.py -blr noisy-sounds  > /dev/null 2> /dev/null &
python taintdriver.py -blr app-ca7  > /dev/null 2> /dev/null &
python taintdriver.py -blr app-kQm  > /dev/null 2> /dev/null &
python taintdriver.py -blr tilt-mazes  > /dev/null 2> /dev/null &
python taintdriver.py -blr andors-trail  > /dev/null 2> /dev/null &
python taintdriver.py -blr ginger-master  > /dev/null 2> /dev/null &
python taintdriver.py -blr app-018  > /dev/null 2> /dev/null &
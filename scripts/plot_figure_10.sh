mkdir -p $ARTIFACT_ROOT_DIR/reproduced_results/RQ1
cd $ARTIFACT_ROOT_DIR/reproduced_results/RQ1
rm -rf figure10
mkdir figure10
cd $ARTIFACT_ROOT_DIR/scripts
python boxplot-taint.py app-324
python boxplot-taint.py andors-trail
python boxplot-taint.py app-018
python boxplot-taint.py app-ca7
python boxplot-taint.py app-kQm
python boxplot-taint.py ginger-master
python boxplot-taint.py noisy-sounds
python boxplot-taint.py tilt-mazes
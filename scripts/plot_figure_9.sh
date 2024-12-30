mkdir -p $ARTIFACT_ROOT_DIR/reproduced_results/RQ1
cd $ARTIFACT_ROOT_DIR/reproduced_results/RQ1
rm -rf figure9
mkdir figure9
cd $ARTIFACT_ROOT_DIR/scripts
python boxplot-pts.py moldyn
python boxplot-pts.py montecarlo
python boxplot-pts.py hedc
python boxplot-pts.py weblech
python boxplot-pts.py jspider
python boxplot-pts.py toba-s
python boxplot-pts.py javasrc-p
python boxplot-pts.py ftp

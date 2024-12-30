mkdir -p $ARTIFACT_ROOT_DIR/reproduced_results/RQ3
cd $ARTIFACT_ROOT_DIR/reproduced_results/RQ3
rm -rf txts
mkdir txts

cd $ARTIFACT_ROOT_DIR
python ./scripts/get_bingo_txt.py montecarlo
python ./scripts/get_bingo_txt.py moldyn
python ./scripts/get_bingo_txt.py hedc
python ./scripts/get_bingo_txt.py jspider
python ./scripts/get_bingo_txt.py weblech
python ./scripts/get_bingo_txt.py toba-s
python ./scripts/get_bingo_txt.py javasrc-p
python ./scripts/get_bingo_txt.py ftp

cd $ARTIFACT_ROOT_DIR/reproduced_results/RQ3
rm -rf figure11
mkdir figure11

cd $ARTIFACT_ROOT_DIR/scripts
python plot_figure11.py montecarlo
python plot_figure11.py moldyn
python plot_figure11.py hedc
python plot_figure11.py jspider
python plot_figure11.py weblech
python plot_figure11.py toba-s
python plot_figure11.py javasrc-p
python plot_figure11.py ftp
mkdir -p $ARTIFACT_ROOT_DIR/reproduced_results/RQ4
cd $ARTIFACT_ROOT_DIR/reproduced_results/RQ4
rm -rf figure14
mkdir figure14

python $ARTIFACT_ROOT_DIR/scripts/gen_csv_14.py

cd $ARTIFACT_ROOT_DIR/reproduced_results/neuro/pts

python $ARTIFACT_ROOT_DIR/scripts/plot_figure_12-14.py -i ./moldyn/median.CSV -rotateXLabel 15 -o $ARTIFACT_ROOT_DIR/reproduced_results/RQ4/figure12/moldyn.pdf
python $ARTIFACT_ROOT_DIR/scripts/plot_figure_12-14.py -i ./montecarlo/median.CSV -rotateXLabel 15 -o $ARTIFACT_ROOT_DIR/reproduced_results/RQ4/figure12/montecarlo.pdf
python $ARTIFACT_ROOT_DIR/scripts/plot_figure_12-14.py -i ./weblech/median.CSV -rotateXLabel 15 -o $ARTIFACT_ROOT_DIR/reproduced_results/RQ4/figure12/weblech.pdf
python $ARTIFACT_ROOT_DIR/scripts/plot_figure_12-14.py -i ./toba-s/median.CSV -rotateXLabel 15 -o $ARTIFACT_ROOT_DIR/reproduced_results/RQ4/figure12/toba-s.pdf
python $ARTIFACT_ROOT_DIR/scripts/plot_figure_12-14.py -i ./hedc/median.CSV -rotateXLabel 15 -o $ARTIFACT_ROOT_DIR/reproduced_results/RQ4/figure12/hedc.pdf
python $ARTIFACT_ROOT_DIR/scripts/plot_figure_12-14.py -i ./jspider/median.CSV -rotateXLabel 15 -o $ARTIFACT_ROOT_DIR/reproduced_results/RQ4/figure12/jspider.pdf
python $ARTIFACT_ROOT_DIR/scripts/plot_figure_12-14.py -i ./javasrc-p/median.CSV -rotateXLabel 15 -o $ARTIFACT_ROOT_DIR/reproduced_results/RQ4/figure12/javasrc-p.pdf
python $ARTIFACT_ROOT_DIR/scripts/plot_figure_12-14.py -i ./ftp/median.CSV -rotateXLabel 15 -o $ARTIFACT_ROOT_DIR/reproduced_results/RQ4/figure12/ftp.pdf
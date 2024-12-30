mkdir -p $ARTIFACT_ROOT_DIR/reproduced_results/RQ4
cd $ARTIFACT_ROOT_DIR/reproduced_results/RQ4
rm -rf figure12
mkdir figure12

python $ARTIFACT_ROOT_DIR/scripts/gen_csv_12.py

cd $ARTIFACT_ROOT_DIR/reproduced_results/neuro/pts

python $ARTIFACT_ROOT_DIR/scripts/plot_figure_12-14.py -i ./moldyn/inversion.CSV -rotateXLabel 15 -o $ARTIFACT_ROOT_DIR/reproduced_results/RQ4/figure12/moldyn.pdf
python $ARTIFACT_ROOT_DIR/scripts/plot_figure_12-14.py -i ./montecarlo/inversion.CSV -rotateXLabel 15 -o $ARTIFACT_ROOT_DIR/reproduced_results/RQ4/figure12/montecarlo.pdf
python $ARTIFACT_ROOT_DIR/scripts/plot_figure_12-14.py -i ./weblech/inversion.CSV -rotateXLabel 15 -o $ARTIFACT_ROOT_DIR/reproduced_results/RQ4/figure12/weblech.pdf
python $ARTIFACT_ROOT_DIR/scripts/plot_figure_12-14.py -i ./toba-s/inversion.CSV -rotateXLabel 15 -o $ARTIFACT_ROOT_DIR/reproduced_results/RQ4/figure12/toba-s.pdf
python $ARTIFACT_ROOT_DIR/scripts/plot_figure_12-14.py -i ./hedc/inversion.CSV -rotateXLabel 15 -o $ARTIFACT_ROOT_DIR/reproduced_results/RQ4/figure12/hedc.pdf
python $ARTIFACT_ROOT_DIR/scripts/plot_figure_12-14.py -i ./jspider/inversion.CSV -rotateXLabel 15 -o $ARTIFACT_ROOT_DIR/reproduced_results/RQ4/figure12/jspider.pdf
python $ARTIFACT_ROOT_DIR/scripts/plot_figure_12-14.py -i ./javasrc-p/inversion.CSV -rotateXLabel 15 -o $ARTIFACT_ROOT_DIR/reproduced_results/RQ4/figure12/javasrc-p.pdf
python $ARTIFACT_ROOT_DIR/scripts/plot_figure_12-14.py -i ./ftp/inversion.CSV -rotateXLabel 15 -o $ARTIFACT_ROOT_DIR/reproduced_results/RQ4/figure12/ftp.pdf
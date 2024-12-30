
mkdir -p $ARTIFACT_ROOT_DIR/reproduced_results/RQ1
python $ARTIFACT_ROOT_DIR/scripts/gen_csv_8a.py
python $ARTIFACT_ROOT_DIR/scripts/gen_csv_8b.py
python $ARTIFACT_ROOT_DIR/scripts/gen_csv_8c.py
python $ARTIFACT_ROOT_DIR/scripts/gen_csv_8d.py
python $ARTIFACT_ROOT_DIR/scripts/gen_csv_8e.py
python $ARTIFACT_ROOT_DIR/scripts/gen_csv_8f.py
cd $ARTIFACT_ROOT_DIR/reproduced_results/RQ1
rm -rf figure8
mkdir figure8
python $ARTIFACT_ROOT_DIR/scripts/plot_figure8.py -i median-pts.CSV -percentage -legend -rotateXLabel 15  -o figure8/figure_8e.pdf -top median-pts.top
python $ARTIFACT_ROOT_DIR/scripts/plot_figure8.py -i inversion-pts.CSV -percentage -legend -rotateXLabel 15  -o figure8/figure_8a.pdf -top inversion-pts.top
python $ARTIFACT_ROOT_DIR/scripts/plot_figure8.py -i mean-pts.CSV -percentage -legend -rotateXLabel 15  -o figure8/figure_8c.pdf -top mean-pts.top
python $ARTIFACT_ROOT_DIR/scripts/plot_figure8.py -i median-taint.CSV -percentage -legend -rotateXLabel 15  -o figure8/figure_8f.pdf -top median-taint.top
python $ARTIFACT_ROOT_DIR/scripts/plot_figure8.py -i mean-taint.CSV -percentage -legend -rotateXLabel 15  -o figure8/figure_8d.pdf -top mean-taint.top
python $ARTIFACT_ROOT_DIR/scripts/plot_figure8.py -i inversion-taint.CSV -percentage -legend -rotateXLabel 15  -o figure8/figure_8b.pdf -top inversion-taint.top
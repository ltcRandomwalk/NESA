mkdir -p figs
python plot.py -i median-pts.CSV -percentage -legend -rotateXLabel 15  -o figs/median-pts.pdf -top median-pts.top
python plot.py -i inversion-pts.CSV -percentage -legend -rotateXLabel 15  -o figs/inversion-pts.pdf -top inversion-pts.top
python plot.py -i mean-pts.CSV -percentage -legend -rotateXLabel 15  -o figs/mean-pts.pdf -top mean-pts.top
python plot.py -i median-taint.CSV -percentage -legend -rotateXLabel 15  -o figs/median-taint.pdf -top median-taint.top
python plot.py -i mean-taint.CSV -percentage -legend -rotateXLabel 15  -o figs/mean-taint.pdf -top mean-taint.top
python plot.py -i inversion-taint.CSV -percentage -legend -rotateXLabel 15  -o figs/inversion-taint.pdf -top inversion-taint.top
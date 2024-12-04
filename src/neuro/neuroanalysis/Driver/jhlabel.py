import subprocess
import logging
import math
import sys
from config import *
from driver import bench_path_map
import os
import random

bench_path =  bench_path_map[sys.argv[1]]
augmentDir = 'noaugment_base'
query = "pts"
problem_out_path = bench_path + '/' + f"chord_output_mln-{query}-problem"


softEvidencesFileName = problem_out_path + '/soft_evi.txt'
aliasLabelFileName = problem_out_path + '/aliasLabels_bk.txt'
outputFileName = problem_out_path + '/aliasLabels.txt'


logging.basicConfig(level=logging.INFO, \
                    format="[%(asctime)s] %(levelname)s [%(name)s.%(funcName)s:%(lineno)d] %(message)s", \
                    datefmt="%H:%M:%S")


aliasLabels = [ line[:-1] for line in open(aliasLabelFileName) if len(line) > 0 ]
posLabels = [ label for label in aliasLabels if label[-1] == '+' ]
negLabels = [ label for label in aliasLabels if label[-1] == '-' ]
random.shuffle(posLabels)
random.shuffle(negLabels)

with open(outputFileName, 'w') as f:
    max_len = 10
    for i in range(max_len):
        print(posLabels[i], file=f)
    for i in range(max_len):
        print(negLabels[i], file=f) 
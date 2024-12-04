import subprocess
import logging
import math
import sys
from config import *
from driver import bench_path_map
import os

bench_path =  bench_path_map[sys.argv[1]]
augmentDir = 'noaugment_base'
query = "pts"
problem_out_path = bench_path + '/' + f"chord_output_mln-{query}-problem"


softEvidencesFileName = problem_out_path + '/soft_evi.txt'
aliasLabelFileName = problem_out_path + '/aliasLabels.txt'


logging.basicConfig(level=logging.INFO, \
                    format="[%(asctime)s] %(levelname)s [%(name)s.%(funcName)s:%(lineno)d] %(message)s", \
                    datefmt="%H:%M:%S")


aliasLabels = { line[:-2]: line[-2] for line in open(aliasLabelFileName) if len(line) > 0 }
softEvidences = [ line.strip().split() for line in open(softEvidencesFileName) if len(line) > 0]
softEvidenceName = set([ evi[0] for evi in softEvidences ])
#print(softEvidenceName)
for evi in softEvidences:
    eviName = evi[0]
    assert eviName in aliasLabels
    evi.append(aliasLabels[eviName])

for i in range(len(softEvidences)):
    for j in range(i+1, len(softEvidences)):
        if float(softEvidences[i][1]) < float(softEvidences[j][1]):
            tmp = softEvidences[j].copy()
            softEvidences[j] = softEvidences[i].copy()
            softEvidences[i] = tmp.copy()



outputFileName = problem_out_path + '/combine.txt'
with open(outputFileName, 'w') as f:
    for a in softEvidences:
        print('{0} {1} {2}'.format(a[0], a[1], a[2]), file = f)
import matplotlib.pyplot as plt
import matplotlib

import os
import sys
query = "taint"

artifact_root_dir = os.getenv("ARTIFACT_ROOT_DIR")
reproduced_dir = os.path.join(artifact_root_dir, "reproduced_results")
original_dir = os.path.join(artifact_root_dir, "original_results")


out1_path = os.path.join(reproduced_dir, f"neuro/taint/{sys.argv[1]}/rank-baseline.txt")
out2_path = os.path.join(reproduced_dir,f"neuro/taint/{sys.argv[1]}/rank-our-approach-ICCmodel.txt")
oracle_path = os.path.join(reproduced_dir,f"neuro/taint/{sys.argv[1]}/rank-oracle.txt")

if not os.path.isfile(out1_path):
    print(f"WARNING: Missing data {sys.argv[1]}/rank-baseline.txt! Using original data.")
    out1_path = os.path.join(original_dir, f"neuro/taint/{sys.argv[1]}/rank-baseline.txt")
if not os.path.isfile(out2_path):
    print(f"WARNING: Missing data {sys.argv[1]}/rank-our-approach-ICCmodel.txt! Using original data.")
    out2_path = os.path.join(original_dir,f"neuro/taint/{sys.argv[1]}/rank-our-approach-ICCmodel.txt")
if not os.path.isfile(oracle_path):
    print(f"WARNING: Missing data {sys.argv[1]}/rank-oracle.txt! Using original data.")
    oracle_path = os.path.join(original_dir,f"neuro/taint/{sys.argv[1]}/rank-oracle.txt")


def getDataFromRank(filepath, maxAlarm=1e9):
    f = open(filepath, 'r')
    lines = f.readlines()
    trueRank = []
    for cur_line in range(1, len(lines)):
        line = lines[cur_line]
        if line.startswith('#'):
            break
        if cur_line > maxAlarm:
            break
        Rank, Confidence, Ground, Label, Comments, Tuple = line.split()
        if Ground.startswith('True'):
            trueRank.append(int(Rank))
        
    f.close()
    return trueRank
    
    
def getDataFromBingo(filepath, timeout=60*60*24*7, maxAlarm=1e9):
    f = open(filepath, 'r')
    lines = f.readlines()
    trueRank = []
    times = 0
    for cur_line in range(1, len(lines)):
        line = lines[cur_line]
        _, _, Ground, _, _, _, _, _, Time = line.split()
        Time = float(Time)
        times += Time
        if times > timeout:
            break
        if cur_line > maxAlarm:
            break
        if Ground.startswith('True'):
            trueRank.append(cur_line)
    return trueRank
        
    

data = [getDataFromRank(out1_path), getDataFromRank(out2_path), getDataFromRank(oracle_path)]
matplotlib.rcParams.update({'font.size': 20})

plt.boxplot(data, flierprops=dict(marker='x', markersize=2), labels=['baseline', 'our approach', 'oracle'], whis=3)

plt.title(f'{sys.argv[1]}')

plt.ylabel('Rank')


plt.savefig(os.path.join(reproduced_dir, f"RQ1/figure10/{sys.argv[1]}-{query}.pdf"), bbox_inches='tight')

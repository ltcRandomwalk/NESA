import matplotlib.pyplot as plt
import matplotlib

import os
import sys
query = "pts"

out1_path = f"./pts/{sys.argv[1]}/rank-baseline.txt"
out2_path = f"./pts/{sys.argv[1]}/rank-our-approach-small.txt"
oracle_path = f"./pts/{sys.argv[1]}/rank-oracle.txt"


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


plt.savefig(f"boxplot/{sys.argv[1]}-{query}.pdf", bbox_inches='tight')

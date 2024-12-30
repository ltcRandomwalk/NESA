import matplotlib.pyplot as plt
import matplotlib

import os
import sys
query = "pts"


artifact_root_dir = os.getenv("ARTIFACT_ROOT_DIR")
reproduced_dir = os.path.join(artifact_root_dir, "reproduced_results")
original_dir = os.path.join(artifact_root_dir, "original_results")

out1_path = os.path.join(reproduced_dir, f"neuro/pts/{sys.argv[1]}/rank-baseline.txt")
out2_path = os.path.join(reproduced_dir,f"neuro/pts/{sys.argv[1]}/rank-our-approach-small.txt")
bingo_path = os.path.join(reproduced_dir,f"neuro/pts/{sys.argv[1]}/naivebingo.txt")

if not os.path.isfile(out1_path):
    print(f"WARNING: Missing data {sys.argv[1]}/rank-baseline.txt! Using original data.")
    out1_path = os.path.join(original_dir, f"neuro/pts/{sys.argv[1]}/rank-baseline.txt")
if not os.path.isfile(out2_path):
    print(f"WARNING: Missing data {sys.argv[1]}/rank-our-approach-small.txt! Using original data.")
    out2_path = os.path.join(original_dir,f"neuro/pts/{sys.argv[1]}/rank-our-approach-small.txt")
if not os.path.isfile(bingo_path):
    print(f"WARNING: Missing data {sys.argv[1]}/naivebingo.txt! Using original data.")
    bingo_path = os.path.join(original_dir,f"neuro/pts/{sys.argv[1]}/naivebingo.txt")
outpathpre = os.path.join(reproduced_dir, f"RQ3/txts/{sys.argv[1]}-pre.txt")
outpath = os.path.join(reproduced_dir, f"RQ3/txts/{sys.argv[1]}.txt")
outpathbingo = os.path.join(reproduced_dir, f"RQ3/txts/{sys.argv[1]}-bingo.txt")


def getDataFromRank(filepath, outpath, maxAlarm=1e9):
    f = open(filepath, 'r')
    fw = open(outpath, 'w')
    lines = f.readlines()
    trueRank = []
    numTrue = 0
    for cur_line in range(1, len(lines)):
        line = lines[cur_line]
        if line.startswith('#'):
            break
        if cur_line > maxAlarm:
            break
        Rank, Confidence, Ground, Label, Comments, Tuple = line.split()
        if Ground.startswith('True'):
            trueRank.append(int(Rank))
            numTrue += 1
        print(numTrue, file=fw)
    f.close()
    fw.close()
    return trueRank
    
    
def getDataFromBingo(filepath, outpath, timeout=60*60*24*7, maxAlarm=1e9):
    f = open(filepath, 'r')
    fw = open(outpath, 'w')
    lines = f.readlines()
    trueRank = []
    times = 0
    numTrue = 0
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
            numTrue += 1
        print(numTrue, file=fw)
    f.close()
    fw.close()
    return trueRank

def getTimeFromBingo(filepath, maxAlarm=200):
    f = open(filepath, 'r')
    #fw = open(outpath, 'w')
    lines = f.readlines()
    trueRank = []
    times = 0
    numTrue = 0
    for cur_line in range(1, len(lines)):
        line = lines[cur_line]
        _, _, Ground, _, _, _, _, _, Time = line.split()
        Time = float(Time)
        times += Time
        #if times > timeout:
            #break
        if cur_line > maxAlarm:
            break
        if Ground.startswith('True'):
            trueRank.append(cur_line)
            numTrue += 1
        #print(numTrue, file=fw)
    f.close()
   # fw.close()
    return times  

print(getTimeFromBingo(bingo_path))
datapre, data, databingo = [getDataFromRank(out1_path, outpathpre, maxAlarm=200), getDataFromRank(out2_path, outpath, maxAlarm=200), getDataFromBingo(bingo_path,outpathbingo, maxAlarm=200)]


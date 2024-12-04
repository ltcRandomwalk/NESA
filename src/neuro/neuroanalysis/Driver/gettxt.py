import matplotlib.pyplot as plt
import matplotlib
from config import *
import os
import sys
query = "pts"
bench_path_map = {"cache4j":"cache4j", "weblech":"weblech-0.0.3", "hedc":"hedc", "javasrc-p":"ashesJSuite/benchmarks/javasrc-p", "toba-s":"ashesJSuite/benchmarks/toba-s", "antlr":"dacapo/benchmarks/antlr",
                   "ftp": "ftp", 
                  "jspider": "jspider",
                  "avrora": "dacapo/benchmarks/avrora",
                  "luindex": "dacapo/benchmarks/luindex",
                  "sunflow": "dacapo/benchmarks/sunflow",
                  "xalan": "dacapo/benchmarks/xalan",
                  "montecarlo": "java_grande/montecarlo",
                  "moldyn": "java_grande/moldyn",
                  "raytracer": "java_grande/raytracer",
                  }
bench_path_map = {p : os.path.join(bench_folder_path,bench_path_map[p]) for p in bench_path_map}
bench_path = bench_path_map[sys.argv[1]]
out1_path = os.path.join(bench_path, f"chord_output_mln-{query}-problem/out1.txt")
out2_path = os.path.join(bench_path, f"chord_output_mln-{query}-problem/out2-lll.txt")
oracle_path = os.path.join(bench_path, f"chord_output_mln-{query}-problem/labelout2-Apr.txt")
bingo_path = os.path.join(bench_path, f"chord_output_mln-{query}-problem/naivebingo.txt")
outpathpre = f"txts/{sys.argv[1]}-pre.txt"
outpath = f"txts/{sys.argv[1]}.txt"
outpathbingo = f"txts/{sys.argv[1]}-bingo.txt"


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


import argparse
from config import *
import os
import subprocess

query = "pts"
bench_path_map = {"elevator":"elevator", "cache4j":"cache4j", "weblech":"weblech-0.0.3", "hedc":"hedc", "javasrc-p":"ashesJSuite/benchmarks/javasrc-p", "toba-s":"ashesJSuite/benchmarks/toba-s", "antlr":"dacapo/benchmarks/antlr",
                  "soot-c":"ashesJsuite/benchmarks/soot-c", "ftp": "ftp", "sor": "sor", "tsp": "tsp", "jpat-p":"ashesJSuite/benchmarks/jpat-p",
                  "gj":"ashesJSuite/benchmarks/gj",
                  "kawa-c":"ashesJSuite/benchmarks/kawa-c",
                  "rhino-a":"ashesJSuite/benchmarks/rhino-a",
                  "sablecc-j":"ashesJSuite/benchmarks/sablecc-j",
                  "sablecc-w":"ashesJSuite/benchmarks/sablecc-w",
                  "schroeder-m":"ashesJSuite/benchmarks/schroeder-m",
                  "schroeder-s":"ashesJSuite/benchmarks/schroeder-s",
                  "soot-j":"ashesJSuite/benchmarks/soot-j",
                  "symjpack-t":"ashesJSuite/benchmarks/symjpack-t",
                  "bingo_test": "bingo_test",
                  "test": "test",
                  "weka": "weka",
                  "jspider": "jspider",
                  "avrora": "dacapo/benchmarks/avrora",
                  "luindex": "dacapo/benchmarks/luindex",
                  "sunflow": "dacapo/benchmarks/sunflow",
                  "xalan": "dacapo/benchmarks/xalan",
                  "lusearch": "dacapo/benchmarks/lusearch",
                  "jspider": "jspider",
                  "test":"test",
                  "imageFilter": "boofcv/benchmarks/imageFilter",
                  "account": "contest/account",
                  "mergesort": "contest/mergesort",
                  "weka": "weka",
                  "montecarlo": "java_grande/montecarlo",
                  "moldyn": "java_grande/moldyn",
                  "philo": "java_grande/philo",
                  "raytracer": "java_grande/raytracer",
                  "bubblesort": "bubblesort",
                  "calibrateMono": "boofcv/benchmarks/calibrateMono",
                  "pingpong": "contest/pingpong",
                  "shop": "contest/shop",
                  "airlinestickets": "contest/airlinestickets",
                  "manager": "contest/manager",
                  "calibrateStereo": "boofcv/benchmarks/calibrateStereo",
                  "fitPolygon": "boofcv/benchmarks/fitPolygon",
                  "fourierTrans": "boofcv/benchmarks/fourierTrans",
                  "imageSegment": "boofcv/benchmarks/imageSegment",
                  "imageStitch": "boofcv/benchmarks/imageStitch",
                  "lineDetection": "boofcv/benchmarks/lineDetection",
                  "odometryStereo": "boofcv/benchmarks/odometryStereo",
                  "overheadView": "boofcv/benchmarks/overheadView",
                  "pointFeatureTracker": "boofcv/benchmarks/pointFeatureTracker",
                  "poseOfCalib": "boofcv/benchmarks/poseOfCalib",
                  "sceneConstruction": "boofcv/benchmarks/sceneConstruction",
                  "stereoDisparity": "boofcv/benchmarks/stereoDisparity",
                  "stereoTwoViews": "boofcv/benchmarks/stereoTwoViews",
                  "trackerMeanShift": "boofcv/benchmarks/trackerMeanShift",
                  "videoMosaic": "boofcv/benchmarks/videoMosaic",
                  "videoStabilize": "boofcv/benchmarks/videoStabilize",
                  "bufwriter": "contest/bufwriter",
                  "test_soft": "test_soft"
                  }
bench_path_map = {p : os.path.join(bench_folder_path,bench_path_map[p]) for p in bench_path_map}

parser = argparse.ArgumentParser(description="run a Bayesian analysis with soft evidence")

parser.add_argument("bench", help="benchmark name")
parser.add_argument("model", nargs="?", help="model name", default="small")
parser.add_argument("-f", "--frontend", help="run front-end analysis through jchord", action="store_true")
parser.add_argument("-s", "--similarity", help="run javaparser and CodeGraphBERT to calculate similarity scoares of var names", action="store_true")
parser.add_argument("-b", "--backend", help="run the backend bayesian analysis", action="store_true")
parser.add_argument("-l", "--labelAlias", help="produce labels for alias tuples", action="store_true")
parser.add_argument("-r", "--runlabels", action="store_true")
parser.add_argument("-a", "--allhard", action="store_true")
parser.add_argument("-t", "--traindata", action="store_true")
parser.add_argument("-p", "--predictprob", action="store_true")
parser.add_argument("-n", "--naivebingo", action="store_true")
parser.add_argument("-q", "--softbingo", action="store_true")

args = parser.parse_args()

bench_path = bench_path_map[args.bench]
oracle_out_path = os.path.join(bench_path, f"chord_output_mln-{query}-oracle")
oracle_queries_path = os.path.join(oracle_out_path,"oracle_queries.txt")
problem_out_path = os.path.join(bench_path, f"chord_output_mln-{query}-problem")
alias_file_path = os.path.join(problem_out_path, "aliasNames.txt")
pa_file_path = os.path.join(problem_out_path, "processedAlias.txt")
vname_ext_path = os.path.join(root_path, "VarNameExtractor.jar")
sim_cal_path = os.path.join(root_path, "SimilarityCalculator/sim_cal.py")
sim_file = os.path.join(problem_out_path, "soft_evi_jh.txt")
sim_file_bk = os.path.join(problem_out_path, "soft_evi.txt")
label_alias_path = os.path.join(root_path, "AliasLabeler/label_alias.py")
alias_oracle_file_path = os.path.join(oracle_out_path, "aliasNames.txt")
alias_label_file_path = os.path.join(problem_out_path, "aliasLabels.txt")
train_data_path = os.path.join(problem_out_path, "trainDatas.txt")
train_data_handle_path = os.path.join(root_path, "SimilarityCalculator/getTrainData.py")
model_train_path = os.path.join(root_path, "SimilarityCalculator/model_train.py")
prob_predict_path = os.path.join(root_path, "SimilarityCalculator/prob_predict.py")
prob_out_path = os.path.join(problem_out_path, "predict_prob.txt")
model_path = os.path.join(alias_model_path, args.model+".pth")

def label_alias():
    runner_path = os.path.join(chord_incubator_path, "runner_serial.pl")
    if subprocess.call(f"{runner_path} -analysis=mln-{query}-oracle -program={args.bench} -mode=serial -D chord.mln.kobj-alias=true -D chord.mln.nonpldiK={oracle_k}", shell=True):
        exit(1)
    if subprocess.call(f"{runner_path} -analysis=mln-{query}-problem -program={args.bench} -mode=serial -D chord.mln.kobj-alias=true \
    -D chord.mln.oraclePath={oracle_queries_path}", shell=True):
        exit(1)
    subprocess.call(f"cp {oracle_queries_path} {problem_out_path}/", shell=True)
    if subprocess.call(f"python {label_alias_path} {alias_file_path} {alias_oracle_file_path} {alias_label_file_path}", shell=True):
        exit(1)

def run_frontend():
    runner_path = os.path.join(chord_incubator_path, "runner_serial.pl")
    if subprocess.call(f"{runner_path} -analysis=mln-{query}-oracle -program={args.bench} -mode=serial -D chord.mln.nonpldiK={oracle_k} -D chord.mln.kobj-alias=true", shell=True):
        exit(1)
    if subprocess.call(f"{runner_path} -analysis=mln-{query}-problem -program={args.bench} -mode=serial -D chord.mln.kobj-alias=true \
    -D chord.mln.oraclePath={oracle_queries_path} ", shell=True):
        exit(1)
    subprocess.call(f"cp {oracle_queries_path} {problem_out_path}/", shell=True)

def cal_similarity():
    if subprocess.call(f"{java11_path} -jar {vname_ext_path} {alias_file_path} {pa_file_path}", shell=True):
        exit(1)
    #if subprocess.call(f"python {sim_cal_path} {pa_file_path} {sim_file_bk}", shell=True):
    #    exit(1)
    #if subprocess.call(f"head -n 1000 {sim_file_bk} > {sim_file}", shell=True):
    #    exit(1)

def predict_prob():
    if subprocess.call(f"python {prob_predict_path} {alias_label_file_path} {pa_file_path} {sim_file_bk} {model_path}", shell=True):
        exit(1)
        
def run_backend():
    #subprocess.call(f"cat /dev/null &> {problem_out_path}/rule-prob.txt", shell=True)
    os.chdir(bingo_path)
    subprocess.call(f"cp {bingo_path}/rule-prob.txt {problem_out_path}/rule-prob.txt",shell=True)
    if subprocess.call(f"./scripts/bnet/build-bnet.sh {problem_out_path} noaugment_base {problem_out_path}/rule-prob.txt {problem_out_path}/soft_evi.txt", shell=True):
        exit(1)
    if subprocess.call(f"./scripts/bnet/softdriver1.py {problem_out_path} noaugment_base", shell=True):
        exit(1)
    if subprocess.call(f"./scripts/bnet/softdriver2.py {problem_out_path} noaugment_base {args.model}", shell=True):
        exit(1)
        
def runlabels():
    os.chdir(bingo_path)
    if subprocess.call(f"./scripts/bnet/build-bnet.sh {problem_out_path} noaugment_base {problem_out_path}/rule-prob.txt {problem_out_path}/soft_evi.txt", shell=True):
        exit(1)
    if subprocess.call(f"./scripts/bnet/labelalias.py {problem_out_path}", shell=True):
        exit(1)
        
def naivebingo():
    os.chdir(bingo_path)
    if subprocess.call(f"./scripts/bnet/naivebingo.py {problem_out_path} noaugment_base", shell=True):
        exit(1)
        
def softbingo():
    os.chdir(bingo_path)
    if subprocess.call(f"./scripts/bnet/softbingo.py {problem_out_path}", shell=True):
        exit(1)
        
def allhard():
    os.chdir(bingo_path)
    #if subprocess.call(f"./scripts/bnet/build-bnet.sh {problem_out_path} noaugment_base {problem_out_path}/rule-prob.txt {problem_out_path}/soft_evi.txt", shell=True):
    #    exit(1)
    if subprocess.call(f"./scripts/bnet/allhardevi.py {problem_out_path}", shell=True):
        exit(1)
        

        


def main():
    if args.frontend:
        run_frontend()
    
    if args.labelAlias:
        label_alias()
    if args.similarity:
        cal_similarity()
    if args.predictprob:
        predict_prob()
    if args.backend:
        run_backend()
    if args.runlabels:
        runlabels()
    if args.allhard:
        allhard()
    if args.naivebingo:
        naivebingo()
    if args.softbingo:
        softbingo()



if __name__ == "__main__":
    main()

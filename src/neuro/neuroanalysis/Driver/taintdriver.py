import argparse
from config import *
import os
import subprocess
import random

query = "taint"
bench_folder_path = taint_bench_folder_path
#bench_path_map = {p : os.path.join(bench_folder_path,bench_path_map[p]) for p in bench_path_map}

parser = argparse.ArgumentParser(description="run a Bayesian analysis with soft evidence")

parser.add_argument("bench", help="benchmark name")
parser.add_argument("-f", "--frontend", help="run front-end analysis through jchord", action="store_true")
parser.add_argument("-s", "--similarity", help="run javaparser and CodeGraphBERT to calculate similarity scoares of var names", action="store_true")
parser.add_argument("-b", "--backend", help="run the backend bayesian analysis", action="store_true")
parser.add_argument("-l", "--labelAlias", help="produce labels for alias tuples", action="store_true")
parser.add_argument("-r", "--runlabels", action="store_true")
parser.add_argument("-n", "--naivebingo", action="store_true")
parser.add_argument("-p", "--pldi13", action="store_true")


args = parser.parse_args()

bench_path = os.path.join(bench_folder_path, args.bench)
oracle_out_path = os.path.join(bench_path, f"chord_output_mln-{query}-oracle")
oracle_queries_path = os.path.join(oracle_out_path,"oracle_queries.txt")
problem_out_path = os.path.join(bench_path, f"chord_output_mln-{query}-problem")
alias_file_path = os.path.join(problem_out_path, "ICCNames.txt")
pa_file_path = os.path.join(problem_out_path, "processedCICM.txt")
vname_ext_path = os.path.join(root_path, "VarNameExtractor.jar")
sim_cal_path = os.path.join(root_path, "SimilarityCalculator/embed_cal.py")
predict_prob_path = os.path.join(root_path, "SimilarityCalculator/prob_predict.py")
model_path = os.path.join(root_path, "SimilarityCalculator/my_model.pth")
sim_file = os.path.join(problem_out_path, "soft_evi.txt")
embedded_file_bk = os.path.join(problem_out_path, "embedded.txt")
label_alias_path = os.path.join(root_path, "AliasLabeler/label_cecs.py")
alias_oracle_file_path = os.path.join(oracle_out_path, "oracle_ICCNames.txt")
alias_label_file_path = os.path.join(problem_out_path, "aliasLabels.txt")
proven_mapped_path = os.path.join(bench_path, "proven_queries_mapped.txt")


def run_pldi13():
    runner_path = os.path.join(chord_incubator_path, "runner.pl")
    if subprocess.call(f"{runner_path} -mode=parallel -analysis=thresc_metaback -program={args.bench} -workers=2 -port={10000 + random.randint(1, 10000)}", shell=True):
        exit(1)
    
    
def label_alias():
    if subprocess.call(f"python {label_alias_path} {alias_file_path} {alias_oracle_file_path} {alias_label_file_path}", shell=True):
        exit(1)

def run_frontend():
    runner_path = os.path.join(chord_incubator_path, "runner_serial.pl")
    Emap_path = os.path.join(bench_path, "chord_output_thresc_metaback/Master/bddbddb/E.map")
    proven_queries_path = os.path.join(bench_path, "chord_output_thresc_metaback/Master/proven_queries.txt")
    #if subprocess.call(f"./proven {Emap_path} {proven_queries_path} > {proven_mapped_path}", shell=True):
     #   exit(1)
    if subprocess.call(f"{runner_path} -analysis=mln-{query}-oracle -program={args.bench} -mode=serial -D chord.mln.nonpldiK={oracle_k} -D chord.mln.datarace-cec=true -D chord.mln.threscFile={proven_mapped_path}", shell=True):
        exit(1)
    if subprocess.call(f"{runner_path} -analysis=mln-{query}-problem -program={args.bench} -mode=serial -D chord.mln.datarace-cec=true \
    -D chord.mln.oraclePath={oracle_queries_path} ", shell=True):
        exit(1)
    subprocess.call(f"cp {oracle_queries_path} {problem_out_path}/", shell=True)

def cal_similarity():
    if subprocess.call(f"cp {alias_file_path} {pa_file_path}", shell=True):
        exit(1)
    if subprocess.call(f"python {sim_cal_path} {pa_file_path} {embedded_file_bk}", shell=True):
        exit(1)
    if subprocess.call(f"python {predict_prob_path} {embedded_file_bk} {sim_file} {alias_label_file_path}", shell=True):
        exit(1)

def run_backend():
    subprocess.call(f"cp {taint_bench_folder_path}/VideoActivity/chord_output_mln-taint-problem/rule-prob.txt {problem_out_path}/rule-prob.txt", shell=True)
    os.chdir(bingo_path)
    if subprocess.call(f"./scripts/bnet/build-bnet.sh {problem_out_path} noaugment_base {problem_out_path}/rule-prob.txt {problem_out_path}/soft_evi.txt", shell=True):
        exit(1)
    if subprocess.call(f"./scripts/bnet/softdriver1.py {problem_out_path} noaugment_base", shell=True):
        exit(1)
    if subprocess.call(f"./scripts/bnet/softdriver2.py {problem_out_path} noaugment_base ICCmodel", shell=True):
        exit(1)

def runlabels():
    subprocess.call(f"cp {taint_bench_folder_path}/VideoActivity/chord_output_mln-taint-problem/rule-prob.txt {problem_out_path}/rule-prob.txt", shell=True)
    #if subprocess.call(f"python ./sss.py  {alias_file_path} {problem_out_path}/soft_evi.txt", shell=True):
     #   exit(1)
    os.chdir(bingo_path)
    
    if subprocess.call(f"./scripts/bnet/build-bnet.sh {problem_out_path} noaugment_base {problem_out_path}/rule-prob.txt {problem_out_path}/soft_evi.txt", shell=True):
        exit(1)
    if subprocess.call(f"./scripts/bnet/labelalias.py {problem_out_path}", shell=True):
        exit(1)

def naivebingo():
    os.chdir(bingo_path)
    if subprocess.call(f"./scripts/bnet/naivebingo.py {problem_out_path} noaugment_base", shell=True):
        exit(1)


def main():
    if args.pldi13:
        run_pldi13()
    if args.frontend:
        run_frontend()
    if args.labelAlias:
        label_alias()
    if args.similarity:
        cal_similarity()
    if args.backend:
        run_backend()
    
    if args.runlabels:
        runlabels()
    if args.naivebingo:
        naivebingo()



if __name__ == "__main__":
    main()

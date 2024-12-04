import os

root_path = os.path.join(os.getenv("ARTIFACT_ROOT_DIR"), "src/neuro/neuroanalysis")
chord_incubator_path = os.path.join(os.getenv("ARTIFACT_ROOT_DIR"), "src/neuro/bingo_incubator")
bench_folder_path = os.path.join(os.getenv("ARTIFACT_ROOT_DIR"), "benchmarks/pjbench")
bingo_path = os.path.join(os.getenv("ARTIFACT_ROOT_DIR"), "src/neuro/bingo")
taint_bench_folder_path = os.path.join(os.getenv("ARTIFACT_ROOT_DIR"), "benchmarks/android_bench")
alias_model_path = os.path.join(os.getenv("ARTIFACT_ROOT_DIR"), "data/alias_model")
java11_path = "/usr/lib/jvm/java-11-openjdk-amd64/bin/java"
chord_main_path = ""
oracle_k = 2
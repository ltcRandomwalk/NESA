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

#
#softEvidencesFileName = problem_out_path + '/soft_evi.txt'
#aliasLabelFileName = problem_out_path + '/aliasLabels.txt'
labelFileName = problem_out_path + '/combine.txt'


logging.basicConfig(level=logging.INFO, \
                    format="[%(asctime)s] %(levelname)s [%(name)s.%(funcName)s:%(lineno)d] %(message)s", \
                    datefmt="%H:%M:%S")

dataset = [ line.strip().split() for line in open(labelFileName) ]
positiveLabels = [ data for data in dataset if data[2] == '+']
positiveRate = len(positiveLabels) / len(dataset)
negativeRate = 1 - positiveRate

learningRate = 5e-4

def sigmoid(x):
    return 1.0 / (1.0 + math.exp(-x))

def logisticRegreesion(dataset, learningRate):
    theta_0 = 0.0
    theta_1 = 0.0
    maxiters = 2000000
    tolerance = 1e-10
    iter = 0
    while iter < maxiters:
        delta_0, delta_1 = 0.0, 0.0
        for data in dataset:
            predict = sigmoid(theta_0 + theta_1 * float(data[1]))
            truelabel = 1.0 if data[2] == '+' else 0.0
            delta_0 += learningRate * (truelabel - predict) #* (negativeRate if data[2] == '+' else positiveRate)
            delta_1 += learningRate * (truelabel - predict) * float(data[1]) #* (negativeRate if data[2] == '+' else positiveRate)
        theta_0 += delta_0
        theta_1 += delta_1
        print('{0} {1}'.format(theta_0, theta_1))
        if abs(delta_0) < tolerance and abs(delta_1) < tolerance:
            break
        iter += 1
        
    return (theta_0, theta_1, True if iter < maxiters else False)
    

if __name__ == "__main__":
    ret = logisticRegreesion(dataset, learningRate)
    with open(problem_out_path + '/logistic.txt', 'w') as f:
        f.write("theta_0 = {0}, theta_1 = {1}".format(ret[0], ret[1]))
        if not ret[2]:
            f.write("not converged.")
            


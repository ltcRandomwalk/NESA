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

labelFileName = problem_out_path + '/combine.txt'


logging.basicConfig(level=logging.INFO, \
                    format="[%(asctime)s] %(levelname)s [%(name)s.%(funcName)s:%(lineno)d] %(message)s", \
                    datefmt="%H:%M:%S")

dataset = [ line.strip().split() for line in open(labelFileName) ]
positiveLabels = [ data for data in dataset if data[2] == '+']
positiveRate = len(positiveLabels) / len(dataset)
negativeRate = 1 - positiveRate

def sigmoid(x):
    return 1.0 / (1.0 + math.exp(-x))

def calloss(dataset):
    theta_0 = 0.414
    theta_1 = 2.167
    #maxiters = 2000000
    #tolerance = 1e-10
    iter = 0
    totalloss = 0
    
        #delta_0, delta_1 = 0.0, 0.0
    for data in dataset:
        predict = sigmoid(theta_0 + theta_1 * float(data[1]))
        truelabel = 1.0 if data[2] == '+' else 0.0
        #delta_0 += learningRate * (truelabel - predict) #* (negativeRate if data[2] == '+' else positiveRate)
        loss = -truelabel * math.log(predict) - (1 - truelabel) * math.log(1-predict)
        totalloss += loss
            #delta_1 += learningRate * (truelabel - predict) * float(data[1]) #* (negativeRate if data[2] == '+' else positiveRate)
        #theta_0 += delta_0
        #theta_1 += delta_1
        #print('{0} {1}'.format(theta_0, theta_1))
        #i#f abs(delta_0) < tolerance and abs(delta_1) < tolerance:
         #   break
        
        
    return totalloss / len(dataset)
    

if __name__ == "__main__":
    print(calloss(dataset))
import torch
from torch import nn
import sys
from transformers import RobertaModel, RobertaTokenizer
from typing import List
from alias_model import GraphCodeBERTMeanPooling, getDataSet
import os

device = 'cuda:1'
model_save_path = sys.argv[4]
aliasFile = sys.argv[1]
processedFile = sys.argv[2]
outputFile = sys.argv[3]
torch.set_default_device(device)



if __name__ == "__main__":
    model = GraphCodeBERTMeanPooling().to(device)
    loaded_paras = torch.load(model_save_path)
    model.load_state_dict(loaded_paras)
    print("begin...")
    model.eval()
    dataSet = getDataSet(aliasFile, processedFile, reservedTuple=True)
    output = open(outputFile, 'w')
    tot, tru = 0, 0
    for data in dataSet:
        alias, list1, list2, label = data
        predict_prob = float(model([list1], [list2]))
        print(f"{alias} {predict_prob}", file=output)
        if (predict_prob > 0.5 and label > 0.5) or (predict_prob < 0.5 and label < 0.5):
            tru += 1
        tot += 1
    #print(float(model(["dog1"],["aszrtdfhfsedtf "])))
    

    
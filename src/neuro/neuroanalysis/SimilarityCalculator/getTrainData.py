import os
import sys

processed_alias_file = sys.argv[1]
alias_label_file = sys.argv[2]
train_data_out_file = sys.argv[3]

alias_dict = dict()
label_dict = dict()

with open(processed_alias_file, 'r') as f:
    line = f.readline()
    while line:
        vv_name = line.strip()
        line = f.readline()
        var_1_names = line.strip()
        line = f.readline()
        var_2_names = line.strip()
        alias_dict[vv_name] = (var_1_names, var_2_names)
        line = f.readline()
        
with open (alias_label_file, 'r') as f:
    line = f.readline()
    while line:
        vv_name = line.strip()[:-1]
        label = line.strip()[-1]
        label_dict[vv_name] = label
        line = f.readline()
        
with open(train_data_out_file, 'w') as f:
    for vv_name in label_dict.keys():
        if vv_name in alias_dict:
            print(vv_name, file=f)
            print(alias_dict[vv_name][0], file=f)
            print(alias_dict[vv_name][1], file=f)
            print(label_dict[vv_name], file=f)
                
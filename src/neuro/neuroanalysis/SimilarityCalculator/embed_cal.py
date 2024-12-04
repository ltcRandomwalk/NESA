from transformers import RobertaModel, RobertaTokenizer
import torch
from scipy.spatial.distance import cdist
import numpy as np
import sys

device = 'cuda:1'
gcb = RobertaModel.from_pretrained('./graphcodebert-base').to(device)
tk_code = RobertaTokenizer.from_pretrained('./graphcodebert-base')

def get_embed(code, precode="", postcode=""):
    ids = torch.as_tensor(tk_code.encode(code)[1:-1]).unsqueeze(0)
    pre_ids = torch.as_tensor(tk_code.encode(precode)[1:-1]).unsqueeze(0)
    post_ids = torch.as_tensor(tk_code.encode(postcode)[1:-1]).unsqueeze(0)
    print(ids, pre_ids,post_ids)
    id_size = ids.shape[-1]
    pre_size = pre_ids.shape[-1]
    #post_size = post_ids.shape[-1]
    # embeddings of 'func'
    cat_tk = torch.cat((pre_ids, ids, post_ids), dim=-1)
    cat_tk = cat_tk.to(torch.int32)
    print(cat_tk)
    embeddings = gcb(torch.as_tensor(cat_tk).to(device)).last_hidden_state.squeeze(1).detach()
    ori_emb = embeddings.cpu().numpy()[0]
    
    if(len(np.shape(ori_emb))>1):
        ori_emb = ori_emb[pre_size:pre_size+id_size]
        return np.sum(ori_emb, axis=0)
    else:
        return ori_emb

def cos_sim(em1, em2):
    Y = cdist([em1], [em2], 'cosine')[0][0]
    cos_sim = 1.0 - Y
    return cos_sim

def cal_sim(name1, name2):
    em_1 = get_embed(name1)
    em_2 = get_embed(name2)
    return cos_sim(em_1, em_2)

def main():
    in_file_name = sys.argv[1]
    out_file_name = sys.argv[2]
    print("Read from "+in_file_name+", output to "+out_file_name)
    in_file = open(in_file_name, "r")
    out_file = open(out_file_name, "w")
    lines = in_file.readlines()
    cur_line = 0
    while cur_line < len(lines):
        alias = lines[cur_line].strip()
        cur_line+=1
        #method_name = lines[cur_line].strip()
        #cur_line += 1
        var1_names = lines[cur_line].split("/")[0].strip()
        cur_line+=1
        var2_names_ori = lines[cur_line].strip()
        var2_names = lines[cur_line].strip().split(".")[-1]
        var2_names_pre = " ".join(lines[cur_line].strip().split(".")[:-1])
        
        cur_line+=1
        
        cur_line+=1
        if var2_names == var2_names_ori:
            continue
        highest_sim = -1
        #Debug
        #if "java" not in var2_names_ori:
         #   continue
        print(f"Calculating {alias}")
        print(var1_names)
        print(var2_names)
        em1 = get_embed(var1_names)#, precode=method_name)
        em2 = get_embed(var2_names)#, precode=var2_names_pre)
        out_file.write(alias+"\n"+str(list(em1))+"\n"+str(list(em2))+"\n")
        
       

    in_file.close()
    out_file.close()

def test():
    print(cal_sim("cacheFactory","this"))

if __name__ == "__main__":
    main()
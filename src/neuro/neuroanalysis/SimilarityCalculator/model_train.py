import torch
from torch import nn
from torch.utils.data import Dataset, DataLoader 
from transformers import RobertaModel, RobertaTokenizer
import numpy as np
from typing import List, Tuple, Dict
import sys
import os

device = os.getenv("DEFAULT_DEVICE")
#torch.set_default_tensor_type(torch.DoubleTensor)

dataset_file = sys.argv[1]
model_save_file = sys.argv[2]


def get_train_data(filepath):
    codes_list = [] # List[Tuple[List[str], List[str]]]
    label_list = [] # List[int]
    with open(filepath, 'r') as f:
        line = f.readline() #VV
        while line:
            line = f.readline()
            var_1_names = line.strip().split(',')[:-1]
            line = f.readline()
            var_2_names = line.strip().split(',')[:-1]
            codes_list.append((var_1_names, var_2_names))
            line = f.readline()
            label_list.append(1.0 if line.strip() == '+' else 0.0)
            line = f.readline()
    assert len(codes_list) == len(label_list)
    return codes_list, label_list
            

class CodeDataset(Dataset):
    def __init__(self, codes_list: List[Tuple[List[str], List[str]]], label_list: List[int], device: str = device) -> None:
        super().__init__()
        assert len(codes_list) == len(label_list)
        self.codes_list = codes_list
        self.label_list = label_list
        
    def __getitem__(self, index)  -> Tuple[List[str], List[str], List[int]]:
        label = self.label_list[index]
        codes_1 = self.codes_list[index][0]
        codes_2 = self.codes_list[index][1]
        return codes_1, codes_2, label
    
    def __len__(self):
        return len(self.codes_list)
    
def collate_fn(batch_items: List):
    code_1_batch = [_item[0] for _item in batch_items]
    code_2_batch = [_item[1] for _item in batch_items]
    label_batch = torch.as_tensor([_item[2] for _item in batch_items]).to(device)
    return code_1_batch, code_2_batch, label_batch

class SoftEvidenceModel(nn.Module):
    def __init__(self) -> None:
        super().__init__()
        self.gcb = RobertaModel.from_pretrained('./~/graphcodebert-base').to(device)
        self.tk_code = RobertaTokenizer.from_pretrained('./~/graphcodebert-base')
        self.cos_sim = nn.CosineSimilarity(dim=-1)
        self.linear_layer = nn.Linear(1, 1).to(device)
        self.sigmoid = nn.Sigmoid()
        
    def get_embed(self, codes: List[str]):
        results = []
        for code in codes:
            ids = torch.as_tensor(self.tk_code.encode(code)[1:-1]).unsqueeze(0)
            embeddings = self.gcb(torch.as_tensor(ids).to(device)).last_hidden_state.squeeze(1)[0]

            if(len(embeddings.size())>1):
                results.append(torch.sum(embeddings, dim=0).unsqueeze(0))
            else:
                results.append(embeddings.unsqueeze(0))
        res = torch.cat(tuple(results))
        return res
        
    def forward(self, codes_1:List[List[str]], codes_2:List[List[str]]):
        #print(codes_1, codes_2)
        ems_1 = [self.get_embed(code) for code in codes_1]
        ems_2 = [self.get_embed(code) for code in codes_2]
        assert len(ems_1) == len(ems_2)
        #print(ems_1)
        #print(ems_2)
        coss = []
        for i in range(len(ems_1)):
            cos_values = self.cos_sim(ems_1[i].unsqueeze(1), ems_2[i].unsqueeze(0))
           # print(cos_values)
            max_cos_value = torch.max(cos_values.flatten(), dim=-1).values.unsqueeze(0)
            #print(max_cos_value)
            coss.append(max_cos_value)
        
        #print(coss)
        coss = torch.cat(tuple(coss)).unsqueeze(1)
        #print(coss)
        # print(coss)
        predict_prob = self.sigmoid(self.linear_layer(coss).squeeze(1))
        return predict_prob
    
def main():
    epoch = 50
    learning_rate = 1e-4
    codes_list, label_list = get_train_data(dataset_file)
    #print(codes_list, label_list) #debug
    dataset = CodeDataset(codes_list=codes_list, label_list=label_list, device=device)
    dataloader = DataLoader(dataset=dataset, batch_size=64, collate_fn=collate_fn)

    model = SoftEvidenceModel()
    
    #loaded_paras = torch.load(model_save_path)
    #model.load_state_dict(loaded_paras)
    optim = torch.optim.Adam(params=model.parameters(), lr = learning_rate)
    loss_function = nn.BCELoss()

    global_step = 0
    model.train()
    for i in range(epoch):
        total_loss, total_batch = 0, 0
        for item in dataloader:
            global_step += 1
            code_1, code_2, label = item

            #print(code_1, code_2, label)
            outputs = model(code_1, code_2)
            #print(outputs, label)
            #print(outputs, labels)
            loss = loss_function(outputs, label)
            total_loss += loss.item()
            total_batch += 1
            
            print(f'global_step{global_step}, loss {loss.item()}')
            
            optim.zero_grad()
            loss.backward()
            optim.step()
        
        print(f"epoch {i}, average loss = {total_loss / total_batch}")
            
    torch.save(model.state_dict(), model_save_file)


def test():
    model = SoftEvidenceModel()
    print(model([['cacheFactory', 'cacheFactory']], [['test', 'test', 'test']]))
    
if __name__ == "__main__":
    test()

        

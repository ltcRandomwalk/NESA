import torch
import torch.nn as nn
from transformers import RobertaModel, RobertaTokenizer
import torch.nn.functional as F
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader 
from typing import List, Tuple
import random
import os

device = os.getenv("DEFAULT_DEVICE")
torch.set_default_dtype(torch.float32)
torch.set_default_device(device)
learning_rate = 0.000001

def getDataSet(aliasLabelFile, processedFile, reservedTuple=False):
    labels = {line.strip()[:-1]: line.strip()[-1] for line in open(aliasLabelFile, 'r').readlines() }
    dataSet = []
    with open(processedFile, 'r') as f:
        line = f.readline()
        while line:
            alias = line.strip()
            list1 = f.readline().strip()[:-1].split(',')
            list2 = f.readline().strip()[:-1].split(',')
            line = f.readline()
            if alias not in labels:
                continue
            label = 1.0 if labels[alias] == '+' else 0.0
            if labels[alias] == '+' and random.random() < 0.2:
                continue
            if not reservedTuple:
                dataSet.append((list1, list2, label))
            else:
                dataSet.append((alias, list1, list2, label))
    return dataSet


class CodeDataset(Dataset):
    def __init__(self, codes_list: List[Tuple[List[str], List[str], int]],  device: str = device) -> None:
        super().__init__()
        self.codes_list = [(data[0], data[1]) for data in codes_list]
        self.label_list = [data[2] for data in codes_list]
        
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


class GraphCodeBERTMeanPooling(nn.Module):
    def __init__(self):
        super(GraphCodeBERTMeanPooling, self).__init__()
        self.bert_model = RobertaModel.from_pretrained(os.getenv("GRAPHCODEBERT_DIR"))
        self.tokenizer = RobertaTokenizer.from_pretrained(os.getenv("GRAPHCODEBERT_DIR"))
        self.fc1 = nn.Linear(768 * 2, 128)
        self.fc2 = nn.Linear(128 , 1)
    
    def get_embed(self, word_list):
        encoded_inputs = self.tokenizer(word_list, padding=True, truncation=True, return_tensors="pt")
        outputs = self.bert_model(**encoded_inputs)
        last_hidden_states = outputs.last_hidden_state.squeeze(1)[0]
        pooled_output = torch.mean(last_hidden_states, dim=0).unsqueeze(0)
        return pooled_output

    def forward(self, list1, list2):
        # Tokenize input words
        em_1 = torch.cat([self.get_embed(word) for word in list1]).to(device)
        em_2 = torch.cat([self.get_embed(word) for word in list2]).to(device)
        
        catted = torch.cat([em_1, em_2], dim=1)
        hidden_state = F.leaky_relu(self.fc1(catted))
        output = F.sigmoid(self.fc2(hidden_state).squeeze(1))
        return output
# Example usage

if __name__ == "__main__":
    labelFile = './aliasLabels.txt'
    processedFile = './processedAlias.txt'
    dataSet = getDataSet(labelFile, processedFile)
    dataSet = random.sample(dataSet, 1200)
    trainSet = random.sample(dataSet, len(dataSet) // 5 * 4)
    print(dataSet)
    
    validSet = []
    for data in dataSet:
        if data not in trainSet:
            validSet.append(data)
    dataset = CodeDataset(trainSet)
    dataloader = DataLoader(dataset, batch_size=64, collate_fn=collate_fn)
    
    validCodes1 = [ data[0] for data in validSet ]
    validCodes2 = [ data[1] for data in validSet ]
    validLabels = torch.as_tensor([ data[2] for data in validSet ])
    
    model = GraphCodeBERTMeanPooling()
    criterion = nn.BCELoss()
    optimizer = optim.RMSprop(model.parameters(), lr = learning_rate)
    num_epochs = 1000
    model.train()
    global_step = 0
    
    for epoch in range(num_epochs):
       
        total_loss, total_batch = 0, 0
        for item in dataloader:
            global_step += 1
            codes_1, codes_2, label = item
            outputs = model(codes_1, codes_2)
            loss = criterion(outputs, label)
            total_loss += loss.item()
            total_batch += 1
            
          
            optimizer.zero_grad()
            loss.backward()
            optimizer.step()

        if (epoch + 1) % 2 == 0:
            model.eval()
           
            outputTest = model(validCodes1, validCodes2)
            lossTest = criterion(outputTest,validLabels)
            model.train()
            print(f'Epoch [{epoch + 1}/{num_epochs}], Loss: {loss.item()}, Losstest: {total_loss / total_batch}, LossTest: {lossTest.item()}')
            torch.save(model.state_dict(), f"./model/my_model{epoch+1}.pth")

    
    torch.save(model.state_dict(), 'my_model.pth')
    
#include <bits/stdc++.h>
using namespace std;

char nc(){ static char buf[1000000], *p1 = buf, *p2 = buf; return p1 == p2 && (p2 = (p1 = buf) + fread(buf,1,1000000,stdin),p1 == p2) ? EOF : *p1++; }
#define nc getchar
void read(string& s){ static char tp[1000005]; char ch = nc(); int num = 0; while(ch != ' ' && ch != '\n' && ch != '\r' && ch != EOF){tp[num++] = ch;ch = nc();} tp[num] = '\0';    s = (string)tp; }


int main(int argc, char* argv[]){
 freopen(argv[1], "r", stdin);
 vector<string> v;
 while(1){
  string s;
  read(s);
  if(s.empty()) break;
  v.push_back(s);
 }
 freopen(argv[2], "r", stdin);
 for(int i = 0;;i++){
        int x;
        if(scanf("%d", &x) == EOF) break;
  printf("%s\n", v[x].c_str());
 }
}
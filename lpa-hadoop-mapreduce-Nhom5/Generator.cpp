#include <iostream>
#include <fstream>
#include <vector>
#include <set>
#include <random>
#include <string>
#include <stdexcept>
// Undirected simple planted graph: four groups, mostly internal candidate edges.
int main(int argc,char** argv) {
 try {
  if(argc!=5) {std::cerr<<"Usage: Generator N targetDegree seed output.txt\n"; return 2;}
  int n=std::stoi(argv[1]),d=std::stoi(argv[2]); unsigned seed=std::stoul(argv[3]);
  if(n<20 || n>1000000 || d<2 || d>n/8) throw std::runtime_error("Require N>=20 and 2<=degree<=N/8");
  std::mt19937 rng(seed); std::vector<std::set<int>> adj(n);
  auto add=[&](int u,int v) {if(u!=v) {adj[u].insert(v); adj[v].insert(u);}};
  // Ring inside each group gives every node a neighbor.
  for(int g=0;g<4;g++) {int lo=g*n/4,hi=(g+1)*n/4; for(int u=lo;u<hi;u++) add(u,u+1==hi?lo:u+1);}
  long long target=1LL*n*d/2,edges=0; for(auto& a:adj) edges+=a.size(); edges/=2;
  std::uniform_int_distribution<int> node(0,n-1),pct(0,99);
  while(edges<target) {
   int u=node(rng),v,group=0; while(u>=(group+1)*n/4 && group<3) group++;
   if(pct(rng)<85) {std::uniform_int_distribution<int> inside(group*n/4,(group+1)*n/4-1); v=inside(rng);}
   else {do {v=node(rng);} while(v>=group*n/4 && v<(group+1)*n/4);}
   if(u!=v && !adj[u].count(v)) {add(u,v); edges++;}
  }
  std::ofstream out(argv[4]); if(!out) throw std::runtime_error("Cannot open output");
  for(int u=0;u<n;u++) {out<<u+1<<'\t'<<u+1<<"\t0\t"; bool first=true; for(int v:adj[u]) {if(!first) out<<','; out<<v+1; first=false;} out<<'\n';}
  out.close(); if(!out) throw std::runtime_error("Write failed");
  std::cout<<"N="<<n<<" E="<<edges<<" seed="<<seed<<"\n";
 } catch(const std::exception& e) {std::cerr<<e.what()<<'\n'; return 1;}
}

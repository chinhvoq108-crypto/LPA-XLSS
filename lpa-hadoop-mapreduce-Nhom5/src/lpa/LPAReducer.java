package lpa;
import java.io.IOException;
import java.util.*;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.Reducer;
public class LPAReducer extends Reducer<IntWritable,Text,IntWritable,Text> {
 public void reduce(IntWritable id,Iterable<Text> values,Context c) throws IOException,InterruptedException {
  Map<Integer,Integer> votes=new TreeMap<Integer,Integer>();
  int structures=0, old=0; String neighbors="";
  for(Text t:values) {
   String[] f=t.toString().split("\t",-1);
   if(f[0].equals("S")) {structures++; old=Integer.parseInt(f[1]); neighbors=f[2];}
   else if(f[0].equals("L")) {int l=Integer.parseInt(f[1]); votes.put(l,votes.containsKey(l)?votes.get(l)+1:1);}
   else throw new IOException("Unknown message");
  }
  if(structures!=1) throw new IOException("Node "+id+" has "+structures+" structural records");
  int best=old,max=0;
  for(Map.Entry<Integer,Integer> e:votes.entrySet()) if(e.getValue()>max) {max=e.getValue(); best=e.getKey();}
  // Keep current label when it ties for maximum. Otherwise select smallest maximum label.
  if(votes.containsKey(old) && votes.get(old)==max) best=old;
  int changed=(best==old?0:1);
  if(changed==1) c.getCounter(LPAMapper.Stats.CHANGED).increment(1);
  c.write(id,new Text(best+"\t"+changed+"\t"+neighbors));
 }
}

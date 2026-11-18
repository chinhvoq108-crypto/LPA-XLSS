package lpa;
import java.io.IOException;
import java.util.HashSet;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.Mapper;
public class LPAMapper extends Mapper<LongWritable,Text,IntWritable,Text> {
 public enum Stats { CHANGED, NODES, EDGE_ENDPOINTS }
 public void map(LongWritable key, Text value, Context c) throws IOException,InterruptedException {
  String[] f=value.toString().split("\t",-1);
  if(f.length!=4) throw new IOException("Expected id TAB label TAB status TAB neighbors: "+value);
  int id=Integer.parseInt(f[0]), label=Integer.parseInt(f[1]);
  HashSet<Integer> seen=new HashSet<Integer>();
  if(!f[3].isEmpty()) for(String s:f[3].split(",")) {
   int n=Integer.parseInt(s);
   if(n==id || !seen.add(n)) throw new IOException("Self-loop or duplicate neighbor at "+id);
   c.write(new IntWritable(n),new Text("L\t"+label));
  }
  c.write(new IntWritable(id),new Text("S\t"+label+"\t"+f[3]));
  c.getCounter(Stats.NODES).increment(1);
  c.getCounter(Stats.EDGE_ENDPOINTS).increment(seen.size());
 }
}

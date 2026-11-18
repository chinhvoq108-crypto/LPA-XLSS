package lpa;
import java.io.*;
import java.util.*;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.*;
public class LPADriver extends Configured implements Tool {
 public int run(String[] args) throws Exception {
  if(args.length<2 || args.length>4) {System.err.println("Usage: input outputBase [maxIterations=20] [reducers=2]"); return 2;}
  int limit=args.length>2?Integer.parseInt(args[2]):20;
  int reducers=args.length>3?Integer.parseInt(args[3]):2;
  if(limit<1 || reducers<1) throw new IllegalArgumentException("Limits must be positive");
  Path input=new Path(args[0]),base=new Path(args[1]);
  FileSystem fs=base.getFileSystem(getConf());
  if(fs.exists(base)) throw new IOException("Output already exists; choose a NEW path: "+base);
  if(!input.getFileSystem(getConf()).exists(input)) throw new IOException("Input missing: "+input);
  fs.mkdirs(base);
  long start=System.nanoTime(); String reason="MAX_ITERATIONS";
  try(PrintWriter metrics=new PrintWriter(new OutputStreamWriter(fs.create(new Path(base,"metrics.csv"),false),"UTF-8"))) {
   metrics.println("iteration,nodes,edges,changed,job_seconds,total_seconds,reason");
   for(int i=1;i<=limit;i++) {
    Path output=new Path(base,String.format("iter-%03d",i));
    Job job=Job.getInstance(getConf(),"LPA iteration "+i);
    job.setJarByClass(LPADriver.class);
    job.setMapperClass(LPAMapper.class); job.setReducerClass(LPAReducer.class);
    job.setMapOutputKeyClass(IntWritable.class); job.setMapOutputValueClass(Text.class);
    job.setOutputKeyClass(IntWritable.class); job.setOutputValueClass(Text.class);
    job.setNumReduceTasks(reducers);
    FileInputFormat.addInputPath(job,input); FileOutputFormat.setOutputPath(job,output);
    long js=System.nanoTime();
    if(!job.waitForCompletion(true)) throw new IOException("Job failed at iteration "+i);
    long changed=job.getCounters().findCounter(LPAMapper.Stats.CHANGED).getValue();
    long nodes=job.getCounters().findCounter(LPAMapper.Stats.NODES).getValue();
    long endpoints=job.getCounters().findCounter(LPAMapper.Stats.EDGE_ENDPOINTS).getValue();
    if(changed==0) reason="CONVERGED";
    String state=changed==0?reason:(i==limit?reason:"RUNNING");
    metrics.printf(Locale.ROOT,"%d,%d,%d,%d,%.3f,%.3f,%s%n",i,nodes,endpoints/2,changed,(System.nanoTime()-js)/1e9,(System.nanoTime()-start)/1e9,state);
    metrics.flush();
    System.out.println("LPA iteration="+i+" changed="+changed+" state="+state);
    input=output;
    if(changed==0) break;
   }
  }
  // Small final export on the driver; iterative label calculation remains MapReduce.
  Map<Integer,List<Integer>> groups=new TreeMap<Integer,List<Integer>>();
  for(FileStatus part:fs.globStatus(new Path(input,"part-r-*"))) {
   try(BufferedReader r=new BufferedReader(new InputStreamReader(fs.open(part.getPath()),"UTF-8"))) {
    String s; while((s=r.readLine())!=null) {
     String[] f=s.split("\t",-1); int label=Integer.parseInt(f[1]);
     if(!groups.containsKey(label)) groups.put(label,new ArrayList<Integer>());
     groups.get(label).add(Integer.parseInt(f[0]));
    }
   }
  }
  try(PrintWriter out=new PrintWriter(new OutputStreamWriter(fs.create(new Path(base,"communities.tsv"),false),"UTF-8"))) {
   out.println("label\tsize\tnodes");
   for(Map.Entry<Integer,List<Integer>> e:groups.entrySet()) {
    Collections.sort(e.getValue()); out.println(e.getKey()+"\t"+e.getValue().size()+"\t"+e.getValue());
   }
  }
  try(PrintWriter out=new PrintWriter(new OutputStreamWriter(fs.create(new Path(base,"summary.txt"),false),"UTF-8"))) {
   out.println("reason="+reason); out.println("communities="+groups.size()); out.println("final_path="+input);
  }
  System.out.println("FINAL reason="+reason+" communities="+groups.size()+" path="+input);
  return 0;
 }
 public static void main(String[] args) throws Exception {System.exit(ToolRunner.run(new LPADriver(),args));}
}

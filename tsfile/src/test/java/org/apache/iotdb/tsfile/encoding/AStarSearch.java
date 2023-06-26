package org.apache.iotdb.tsfile.encoding;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue;
import org.pcollections.TreePSet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Random;

public class AStarSearch {
  static IntArrayList PX,PY;
  static ObjectArrayList<IntArrayList> nearestID;
  static int N;
  static final int EXTEND_K = 1;
  // control this to limit the number of new nodes in extension
  // due to the wrong H, when EXTEND_K = 1, a suboptimal solution (a simple greedy) will be found quickly.
  static int[] indexOccurred;
  static int occurTIME=0;
  public static int getBitWith(int num) {
    if (num == 0) return 1;
    else return 32 - Integer.numberOfLeadingZeros(num);
  }
  static int getDis(int x1,int y1,int x2,int y2){return Math.abs(x1-x2)+Math.abs(y1-y2);}
  static int getDis(int a,int b){return  Math.abs(PX.getInt(a)-PX.getInt(b))+Math.abs(PY.getInt(a)-PY.getInt(b));}
//  static int getDis(int x1,int y1,int x2,int y2){return getBitWith(Math.abs(x1-x2))+getBitWith(Math.abs(y1-y2));}
//  static int getDis(int a,int b){return getBitWith( Math.abs(PX.getInt(a)-PX.getInt(b)))+getBitWith(Math.abs(PY.getInt(a)-PY.getInt(b)));}
  static void init(ObjectArrayList<IntIntPair> input) {
    PX=new IntArrayList();PY=new IntArrayList();
    for(IntIntPair pair:input) {
      PX.add(pair.firstInt());
      PY.add(pair.secondInt());
    }
    N=input.size();
    nearestID=new ObjectArrayList<>();
    for(int i=0;i<N;i++){
      IntArrayList neighbor = new IntArrayList(N);
      for(int j=0;j<N;j++)if(j!=i)neighbor.add(j);
      final int Xi=PX.getInt(i),Yi=PY.getInt(i);
      neighbor.sort((a,b)->Double.compare(getDis(Xi,Yi,PX.getInt(a), PY.getInt(a)),getDis(Xi,Yi,PX.getInt(b), PY.getInt(b))));
      nearestID.add(neighbor);
//      if(i==0)System.out.println("\t\t"+neighbor);
     //(a,b)->Double.compare(getDis(Xi,Yi,PX.getInt(a), PY.getInt(a)),getDis(Xi,Yi,PX.getInt(b), PY.getInt(b)))
    }
  }

  private static class Node {
    TreePSet<Integer> set;
    int lastID;
    long g;
    long h;
    long f;
    HashCode hashA;
    Node parent;

    Node() {
      set = TreePSet.empty();
    }

    Node(int s) {
      set = TreePSet.singleton(s);
      lastID = s;
      hashA = Hashing.murmur3_128().hashInt(s);
      g = Math.abs(PX.getInt(s))+Math.abs(PY.getInt(s));
    }

    Node(int latestNode, Node parent) {
      this.set = parent.set.plus(latestNode);
      this.lastID = latestNode;
      this.g = parent.g+getDis(parent.lastID,this.lastID);
      this.hashA = Hashing.combineUnordered(new ObjectArrayList<>(new HashCode[]{parent.hashA,Hashing.murmur3_128().hashInt(lastID)}));
      this.parent = parent;
    }


    int calculateHeuristic(IntArrayList sequence) {
      if(sequence.isEmpty())return 0;
//    System.out.println("\t\tcalcH\t"+sequence.size());
      int sum = 0;
      int size = sequence.size();
      int minDis=getDis(this.lastID,sequence.getInt(0));
      for (int i = 1; i < size; i++) {
        minDis=Math.min(minDis,getDis(this.lastID,sequence.getInt(i)));
        sum += getDis(sequence.getInt(i - 1), sequence.getInt(i)); // TODO: This is a wrong H
      }
      sum+=minDis;
      return sum;
    }
    void calculateHF() {
      IntArrayList remaining = calculateRemaining(set);
      this.h = calculateHeuristic(remaining);
      this.f=g+h;
    }

    void printNode() {
//      System.out.print("\tunordered set:" + this.set);
      System.out.print("\tset size:" + this.set.size());
      System.out.print("\tlastNodeID:" + this.lastID);
      System.out.print("\tg:" + this.g);
      System.out.print(" h:" + this.h);
      System.out.println("\tf:" + this.f);
    }
  }

  public static IntArrayList calculateRemaining(TreePSet<Integer> current) {
    IntArrayList remaining = new IntArrayList(N-current.size());
//    int size = ts_block.size();
//    int[] indexOccurred = new int[size]; // DONT new array in frequently used method
    occurTIME++;
    for (int index : current) indexOccurred[index] = occurTIME;
    for (int i = 0; i < N; i++)
      if (indexOccurred[i] < occurTIME) remaining.add(i);
//    System.out.println("\t\tcalcRem\t"+current.size()+"\t\tN:"+N+"\t\trem:"+remaining.size());
    return remaining;
  }

  /** return the new indexes
   * */
  public static IntArrayList findOptimalOrder(
      ObjectArrayList<IntIntPair> input) {
    init(input);
    indexOccurred = new int[N];

    ObjectHeapPriorityQueue<Node> openQueue = new ObjectHeapPriorityQueue<>(Comparator.comparingLong(node -> node.f));

    LongOpenHashSet[] uA = new LongOpenHashSet[N];

    // 创建初始节点并加入优先队列
    Node initialNode = new Node();
    //        openQueue.offer(initialNode);

    for (int i = 0; i < N; i++) {
      Node nodeI = new Node(i);
      nodeI.calculateHF();
      uA[i]=new LongOpenHashSet();
      openQueue.enqueue(nodeI);;
    }

    Node now;
    while (!openQueue.isEmpty()) {
      now = openQueue.dequeue();

      if (now.set.size()==N){
        int[] pathID = new int[N];
        int tmp=N-1;
        Node tmpNode = now;
        while(tmp>=0){
          pathID[tmp] = tmpNode.lastID;
          tmp--;
          tmpNode=tmpNode.parent;
        }
        return new IntArrayList(pathID);
      }

      if(uA[now.set.size()].contains(now.hashA.asLong()))
        continue;

      uA[now.set.size()].add(now.hashA.asLong());
//      now.printNode();

      int extended=0;
      for(int next:nearestID.get(now.lastID))if(!now.set.contains(next)){
        extended++;
        if(extended>EXTEND_K)break;
        Node nextNode = new Node(next,now);
        nextNode.calculateHF();
        openQueue.enqueue(nextNode);
      }
    }

    return null;
  }

  public static ArrayList<ArrayList<Integer>> findOptimalOrder(ArrayList<ArrayList<Integer>> input){
    ObjectArrayList<IntIntPair> a =
        new ObjectArrayList<>(input.size());
    for(ArrayList<Integer> p:input)a.add(IntIntPair.of(p.get(0),p.get(1)));
    IntArrayList index = findOptimalOrder(a);
    ArrayList<ArrayList<Integer>> ans=new ArrayList<>();
    for(int id:index)ans.add(input.get(id));
    return ans;
  }


  private static boolean isGoal(ArrayList<Integer> sequence, int block_size) {
    int size = sequence.size();
    return size >= block_size;
  }

  private static int getDistance(ArrayList<Integer> last_pair, ArrayList<Integer> cur_remain_pair) {
    int dis = 0;
    int size = last_pair.size();
    for (int i = 0; i < size; i++) {
      dis += Math.abs(last_pair.get(i) - cur_remain_pair.get(i));
    }
    return dis;
  }

  public static void main(String[] args) {
    long TIME = -new Date().getTime();
    int N=256,K=8;
    ObjectArrayList<IntIntPair> input =
        new ObjectArrayList<>(N);
    Random random = new Random(233);
    for(int i=0;i<N;i++)input.add(IntIntPair.of(i,N*random.nextInt(K)));

    System.out.println(input);
    IntArrayList optimalOrder = findOptimalOrder(input);
    System.out.println("Optimal Order: " + optimalOrder);
    for(int index:optimalOrder)System.out.print("\t("+PX.getInt(index)+","+PY.getInt(index)+")");
    TIME+=new Date().getTime();
    System.out.println("\n+ALL_TIME:\t"+TIME+"\tms");
  }
}

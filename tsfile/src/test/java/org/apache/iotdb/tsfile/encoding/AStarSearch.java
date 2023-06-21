package org.apache.iotdb.tsfile.encoding;
import java.util.*;

import static org.apache.iotdb.tsfile.encoding.MinimumSpanningTree.calculateMinimumSpanningTree;

public class AStarSearch {
    public static ArrayList<ArrayList<Integer>> ts_block;
    static void setTs_block(ArrayList<ArrayList<Integer>> cur_ts_block){
        ts_block = new ArrayList<>();
        for (ArrayList<Integer> list : cur_ts_block) {
            ArrayList<Integer> copyList = new ArrayList<>(list);
            ts_block.add(copyList);
        }
    }

    private static class Node {
        ArrayList<Integer> sequence;
        int g;
        int h;
        int f;
        Node parent;
        Node(){
            sequence = new ArrayList<>();
        }
        Node(ArrayList<Integer> sequence, Node parent) {
            this.sequence = sequence;
            this.parent = parent;
        }
        Node(ArrayList<Integer> sequence, int g, int h, Node parent) {
            this.sequence = sequence;
            this.g = g;
            this.h = h;
            this.f = g + h;
            this.parent = parent;
        }
        void calculateG(){
            this.g = calculateHeuristic(sequence);
        }
        void calculateH(){
            ArrayList<Integer> remaining = calculateRemaining(sequence);
////            ArrayList<Integer> timestamp = new ArrayList<>();
////            ArrayList<Integer> value = new ArrayList<>();
//            List<MinimumSpanningTree.Point> points = new ArrayList<>();
//            for(int i: remaining){
//                points.add(new MinimumSpanningTree.Point(ts_block.get(i).get(0),ts_block.get(i).get(1)));
//            }
//            List<MinimumSpanningTree.Edge> minimumSpanningTree = calculateMinimumSpanningTree(points);
//            for (MinimumSpanningTree.Edge edge : minimumSpanningTree) {
//                this.h += edge.weight;
////                System.out.println(edge.src + " - " + edge.dest + ", Weight: " + edge.weight);
//            }
            this.h = calculateHeuristic(remaining);
        }
        void calculateF(){
            this.f = this.g + this.h;
        }
        void printNode(){
            System.out.println("sequence:"+this.sequence);
            System.out.println("sequence size:"+this.sequence.size());
            System.out.println("g:"+this.g);
            System.out.println("h:"+this.h);
            System.out.println("f:"+this.f);
        }
    }
//    static int[] indexOccurred;
//    static int occurTIME=0;
    public static ArrayList<Integer> calculateRemaining(ArrayList<Integer> current) {
        ArrayList<Integer> remaining = new ArrayList<>();
        int occurTIME=1;

        int size = ts_block.size();
        int[] indexOccurred = new int[size];
        for(int index:current) indexOccurred[index]=occurTIME;
        for(int i =0;i<size;i++){
            if(indexOccurred[i]<occurTIME) remaining.add(i);
        }
//        int size = ts_block.size();
//        for(int i =0;i<size;i++){
//            if(!current.contains(i)){
//                remaining.add(i);
//            }
//        }
        return remaining;
    }
    private static int calculateHeuristic(ArrayList<Integer> sequence) {
        int sum = 0;
        int size = sequence.size();
        for (int i = 1; i < size; i++) {
            sum += Math.abs(ts_block.get(sequence.get(i)).get(0) - ts_block.get(sequence.get(i - 1)).get(0));
            sum += Math.abs(ts_block.get(sequence.get(i)).get(1) - ts_block.get(sequence.get(i - 1)).get(1));
        }
        return sum;
    }

    public static ArrayList<ArrayList<Integer>> findOptimalOrder(ArrayList<ArrayList<Integer>> input) {
        int size = input.size();

        setTs_block(input);
        ArrayList<Integer> input_sequence = new ArrayList<>();
        // 计算初始序列的启发值
        int initialH = calculateHeuristic(input_sequence);

        // 创建优先队列
        PriorityQueue<Node> openQueue = new PriorityQueue<>(Comparator.comparingInt(node -> node.f));

        // 创建已访问集合
        ArrayList<ArrayList<Integer>> visited = new ArrayList<>();

        // 创建初始节点并加入优先队列
        Node initialNode = new Node(new ArrayList<>(), 0, initialH, null);
//        openQueue.offer(initialNode);

        for(int i =0;i<size;i++){
//            System.out.println(initialTV);
            ArrayList<Integer> initial_sequence = new ArrayList<>();
            initial_sequence.add(i);
            Node initialNodeChild = new Node(initial_sequence, initialNode);
            initialNodeChild.calculateG();
            initialNodeChild.calculateH();
            initialNodeChild.calculateF();
            openQueue.offer(initialNodeChild);
        }
        Node currentNode = new Node();

        while (!openQueue.isEmpty()) {
            // 从优先队列中取出f值最小的节点
            currentNode = openQueue.poll();

            if(isGoal(currentNode.sequence,size)){
                ArrayList<ArrayList<Integer>> resultOptimalOrder = new ArrayList<>();
                for(int i : currentNode.sequence){
                    resultOptimalOrder.add(ts_block.get(i));
                }
                return  resultOptimalOrder;
            }

            if (visited.contains(currentNode.sequence)) {
                continue;
            }
            visited.add(currentNode.sequence);
//            if(currentNode.parent == null){
//                continue;
//            }
            currentNode.printNode();
            // 生成当前节点的所有邻居节点
            List<Node> neighbors = generateNeighbors(currentNode);
            for (Node neighbor : neighbors) {
//                visited.add(neighbor.sequence);
                if (!visited.contains(neighbor.sequence)) {
                    openQueue.offer(neighbor);
                }
            }
        }

        // 如果搜索失败，则返回原始序列
        return ts_block;
    }


    private static boolean isGoal(ArrayList<Integer> sequence, int block_size) {
        int size = sequence.size();
        return size >= block_size;
    }
    private static int getDistance(ArrayList<Integer> last_pair,ArrayList<Integer> cur_remain_pair){
        int dis = 0;
        int size = last_pair.size();
        for(int i=0;i<size;i++){
            dis += Math.abs(last_pair.get(i)-cur_remain_pair.get(i));
        }
        return dis;
    }
    private static List<Node> generateNeighbors(Node node) {
        List<Node> neighbors = new ArrayList<>();
        ArrayList<Integer> remaining = calculateRemaining(node.sequence);
        int size = remaining.size();
//        int K = 10;
//        if(size < K){
//            K = size;
//        }
//
//        ArrayList<ArrayList<Integer>> distance = new ArrayList<>();
//        ArrayList<Integer> last_pair = ts_block.get(node.sequence.get(node.sequence.size()-1));
//
//        for (int i = 0; i < size; i++) {
//            ArrayList<Integer> cur_remain_pair = ts_block.get(remaining.get(i));
//            ArrayList<Integer> dis = new ArrayList<>();
//            dis.add(remaining.get(i));
//            dis.add(getDistance(last_pair,cur_remain_pair));
//            distance.add(dis);
//        }
//        Collections.sort(distance, new Comparator<ArrayList<Integer>>() {
//            @Override
//            public int compare(ArrayList<Integer> list1, ArrayList<Integer> list2) {
//                return list1.get(1).compareTo(list2.get(1));
//            }
//        });
//
//        // 获取前K个值
//        List<ArrayList<Integer>> result = new ArrayList<>(distance.subList(0, K));
//        for(int i = 0;i<K;i++){
//            ArrayList<Integer> newSequence = new ArrayList<>(node.sequence);
//            newSequence.set(newSequence.size() - 1, result.get(i).get(0));
//            Node neighbor = new Node(newSequence,  node.parent);
//            neighbor.calculateG();
//            neighbor.calculateH();
//            neighbor.calculateF();
//            neighbors.add(neighbor);
//        }

        for (int i = 0; i < size; i++) {
            ArrayList<Integer> newSequence = new ArrayList<>(node.sequence);
            newSequence.set(node.sequence.size() - 1, remaining.get(i));
            Node neighbor = new Node(newSequence,  node.parent);
            neighbor.calculateG();
            neighbor.calculateH();
            neighbor.calculateF();
            neighbors.add(neighbor);
        }
        for (int i = 0; i < size; i++) {
            ArrayList<Integer> newSequence = new ArrayList<>(node.sequence);
            newSequence.add(remaining.get(i));
            Node neighbor = new Node(newSequence,  node);
            neighbor.calculateG();
            neighbor.calculateH();
            neighbor.calculateF();
            neighbors.add(neighbor);
        }
//        int size = node.sequence.size();
//        for (int i = 0; i < size - 1; i++) {
//            for (int j = i + 1; j < size; j++) {
//                ArrayList<ArrayList<Integer>> newSequence = new ArrayList<>(node.sequence);
//                ArrayList<Integer> temp = newSequence.get(i);
//                newSequence.set(i, newSequence.get(j));
//                newSequence.set(j, temp);
//
////                int g = node.g + 1;
////                int h = calculateHeuristic(newSequence);
//                Node neighbor = new Node(newSequence,  node);
//                neighbor.calculateG();
//                neighbor.calculateH(input);
//                neighbor.calculateF();
//                neighbors.add(neighbor);
//            }
//        }
        return neighbors;
    }

    public static void main(String[] args) {
        ArrayList<ArrayList<Integer>> input = new ArrayList<>(Arrays.asList( new ArrayList<>(Arrays.asList(4,4)),
                new ArrayList<>(Arrays.asList(2,2)), new ArrayList<>(Arrays.asList(1,1)), new ArrayList<>(Arrays.asList(3,3)),
                new ArrayList<>(Arrays.asList(8,8)),
                new ArrayList<>(Arrays.asList(6,6)), new ArrayList<>(Arrays.asList(5,5)), new ArrayList<>(Arrays.asList(7,7))));

        System.out.println(input);
        ArrayList<ArrayList<Integer>> optimalOrder = findOptimalOrder(input);
        System.out.println("Optimal Order: " + optimalOrder);
    }
}


package org.apache.iotdb.tsfile.encoding;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class AStarSearchRaw {
    private static class Node {
        List<Integer> sequence;
        int g;
        int h;
        int f;
        Node parent;

        Node(List<Integer> sequence, int g, int h, Node parent) {
            this.sequence = sequence;
            this.g = g;
            this.h = h;
            this.f = g + h;
            this.parent = parent;
        }
    }

    public static List<Integer> findOptimalOrder(ArrayList<Integer> input) {
        int size = input.size();

        // 计算初始序列的启发值
        int initialH = calculateHeuristic(input);

        // 创建优先队列
        PriorityQueue<Node> openQueue = new PriorityQueue<>(Comparator.comparingInt(node -> node.f));

        // 创建已访问集合
        List<List<Integer>> visited = new ArrayList<>();

        // 创建初始节点并加入优先队列
        Node initialNode = new Node(input, 0, initialH, null);
        openQueue.offer(initialNode);

        while (!openQueue.isEmpty()) {
            // 从优先队列中取出f值最小的节点
            Node currentNode = openQueue.poll();

            // 如果当前节点是目标节点，则返回其序列
            if (isGoal(currentNode.sequence)) {
                return currentNode.sequence;
            }

            // 将当前节点的序列加入已访问集合
            visited.add(currentNode.sequence);

            // 生成当前节点的所有邻居节点
            List<Node> neighbors = generateNeighbors(currentNode);
            for (Node neighbor : neighbors) {
                if (!visited.contains(neighbor.sequence)) {
                    openQueue.offer(neighbor);
                }
            }
        }

        // 如果搜索失败，则返回原始序列
        return input;
    }

    private static int calculateHeuristic(List<Integer> sequence) {
        int sum = 0;
        int size = sequence.size();
        for (int i = 1; i < size; i++) {
            sum += Math.abs(sequence.get(i) - sequence.get(i - 1));
        }
        return sum;
    }

    private static boolean isGoal(List<Integer> sequence) {
        int size = sequence.size();
        for (int i = 1; i < size; i++) {
            if (sequence.get(i) < sequence.get(i - 1)) {
                return false;
            }
        }
        return true;
    }

    private static List<Node> generateNeighbors(Node node) {
        List<Node> neighbors = new ArrayList<>();
        int size = node.sequence.size();
        for (int i = 0; i < size - 1; i++) {
            for (int j = i + 1; j < size; j++) {
                List<Integer> newSequence = new ArrayList<>(node.sequence);
                int temp = newSequence.get(i);
                newSequence.set(i, newSequence.get(j));
                newSequence.set(j, temp);

                int g = node.g + 1;
                int h = calculateHeuristic(newSequence);
                Node neighbor = new Node(newSequence, g, h, node);
                neighbors.add(neighbor);
            }
        }
        return neighbors;
    }

    public static void main(String[] args) {
        ArrayList<Integer> input = new ArrayList<>(Arrays.asList(4, 2, 1, 3));

        List<Integer> optimalOrder = findOptimalOrder(input);
        System.out.println("Optimal Order: " + optimalOrder);
    }
}

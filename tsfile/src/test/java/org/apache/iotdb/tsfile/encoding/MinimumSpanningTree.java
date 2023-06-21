package org.apache.iotdb.tsfile.encoding;
import java.util.*;

public class MinimumSpanningTree {
    static class Edge {
        int src;
        int dest;
        double weight;

        Edge(int src, int dest, double weight) {
            this.src = src;
            this.dest = dest;
            this.weight = weight;
        }
    }

    private static class Subset {
        int parent;
        int rank;
    }

    static class Point {
        int x;
        int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private static double calculateDistance(Point p1, Point p2) {
        int dx = Math.abs(p1.x - p2.x);
        int dy = Math.abs(p1.y - p2.y);
        return dx + dy;
//        return Math.sqrt(dx * dx + dy * dy);
    }

    private static int find(Subset[] subsets, int i) {
        if (subsets[i].parent != i) {
            subsets[i].parent = find(subsets, subsets[i].parent);
        }
        return subsets[i].parent;
    }

    private static void union(Subset[] subsets, int x, int y) {
        int xRoot = find(subsets, x);
        int yRoot = find(subsets, y);

        if (subsets[xRoot].rank < subsets[yRoot].rank) {
            subsets[xRoot].parent = yRoot;
        } else if (subsets[xRoot].rank > subsets[yRoot].rank) {
            subsets[yRoot].parent = xRoot;
        } else {
            subsets[yRoot].parent = xRoot;
            subsets[xRoot].rank++;
        }
    }

    public static List<Edge> calculateMinimumSpanningTree(List<Point> points) {
        int n = points.size();
        List<Edge> result = new ArrayList<>();

        Edge[] edges = new Edge[n * (n - 1) / 2];
        int index = 0;
        for (int i = 0; i < n - 1; i++) {
            for (int j = i + 1; j < n; j++) {
                Point p1 = points.get(i);
                Point p2 = points.get(j);
                double distance = calculateDistance(p1, p2);
                edges[index++] = new Edge(i, j, distance);
            }
        }

        Arrays.sort(edges, Comparator.comparingDouble(e -> e.weight));

        Subset[] subsets = new Subset[n];
        for (int i = 0; i < n; i++) {
            subsets[i] = new Subset();
            subsets[i].parent = i;
            subsets[i].rank = 0;
        }

        int e = 0;
        int i = 0;
        while (e < n - 1) {
            Edge nextEdge = edges[i++];
            int x = find(subsets, nextEdge.src);
            int y = find(subsets, nextEdge.dest);

            if (x != y) {
                result.add(nextEdge);
                union(subsets, x, y);
                e++;
            }
        }

        return result;
    }

    public static void main(String[] args) {
        List<Point> points = new ArrayList<>();
        points.add(new Point(1634665392, 357));
        points.add(new Point(1634665493, 351));
        points.add(new Point(1634665593, 347));
        points.add(new Point(1634665693, 349));
        points.add(new Point(1634665793, 347));
        points.add(new Point(1634665892, 341));
        points.add(new Point(1634665992, 345));
        points.add(new Point(1634666092, 350));
        points.add(new Point(1634666192, 352));
        points.add(new Point(1634666292, 349));
        points.add(new Point(1634666392, 351));
        points.add(new Point(1634666492, 353));
        points.add(new Point(1634666592, 354));
        points.add(new Point(1634666692, 357));

        List<Edge> minimumSpanningTree = calculateMinimumSpanningTree(points);

        System.out.println("Minimum Spanning Tree Edges:");
        for (Edge edge : minimumSpanningTree) {
            System.out.println(edge.src + " - " + edge.dest + ", Weight: " + edge.weight);
        }
    }
}


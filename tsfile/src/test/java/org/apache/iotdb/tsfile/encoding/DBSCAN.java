package org.apache.iotdb.tsfile.encoding;

import java.util.ArrayList;
import java.util.List;

import static org.codehaus.groovy.runtime.DefaultGroovyMethods.abs;

public class DBSCAN {
  private static final int UNCLASSIFIED = -1;
  private static final int NOISE = 0;

  private List<Point> points;
  private double eps; // 邻域半径
  private int minPts; // 邻域内最小样本数

  public static int getBitWith(int num) {
    if (num == 0) return 1;
    else return 32 - Integer.numberOfLeadingZeros(num);
  }

  public DBSCAN(List<Point> points, double eps, int minPts) {
    this.points = points;
    this.eps = eps;
    this.minPts = minPts;
  }

  public List<List<Point>> run() {
    int clusterId = 1;
    for (Point point : points) {
      if (point.getClusterId() == UNCLASSIFIED) {
        if (expandCluster(point, clusterId)) {
          clusterId++;
        }
      }
    }

    // 将点按簇进行分组
    List<List<Point>> clusters = new ArrayList<>();
    int numClusters = clusterId - 1;
    for (int i = 0; i < numClusters; i++) {
      clusters.add(new ArrayList<>());
    }
    List<Point> noise_points = new ArrayList<>();
    for (Point point : points) {
      clusterId = point.getClusterId();
      if (clusterId != NOISE) {
        clusters.get(clusterId - 1).add(point);
      } else {
        noise_points.add(point);
      }
    }
    clusters.add(noise_points);
    return clusters;
  }

  private boolean expandCluster(Point point, int clusterId) {
    List<Point> neighbors = queryRegion(point);
    if (neighbors.size() < minPts) {
      point.setClusterId(NOISE);
      return false;
    }

    point.setClusterId(clusterId);
    for (int i = 0; i < neighbors.size(); i++) {
      Point neighbor = neighbors.get(i);
      if (neighbor.getClusterId() == UNCLASSIFIED || neighbor.getClusterId() == NOISE) {
        if (neighbor.getClusterId() == UNCLASSIFIED) {
          neighbors.add(neighbor);
        }
        neighbor.setClusterId(clusterId);
      }
    }

    return true;
  }

  private List<Point> queryRegion(Point point) {
    List<Point> neighbors = new ArrayList<>();
    for (Point neighbor : points) {
      if (calculateDistance(point, neighbor) <= eps) {
        neighbors.add(neighbor);
      }
    }
    return neighbors;
  }

  private int calculateDistance(Point p1, Point p2) {
    int dx = p1.getX() - p2.getX();
    int dy = p1.getY() - p2.getY();
    return getBitWith((int) abs(dx)) + getBitWith((int) abs(dy));
    //        return Math.sqrt(dx * dx + dy * dy);
  }

  public static class Point {
    private int x;
    private int y;
    private int clusterId;

    private int pointId;

    public Point(int x, int y) {
      this.x = x;
      this.y = y;
      this.clusterId = UNCLASSIFIED;
    }

    public int getX() {
      return x;
    }

    public int getY() {
      return y;
    }

    public int getClusterId() {
      return clusterId;
    }

    public void setClusterId(int clusterId) {
      this.clusterId = clusterId;
    }
  }

  public static void main(String[] args) {
    // 示例数据
    List<Point> points = new ArrayList<>();
    points.add(new Point(1, 1));
    points.add(new Point(2, 2));
    points.add(new Point(2, 3));
    points.add(new Point(3, 2));
    points.add(new Point(8, 7));
    points.add(new Point(8, 8));
    points.add(new Point(25, 80));
    points.add(new Point(30, 80));
    points.add(new Point(30, 85));

    DBSCAN dbscan = new DBSCAN(points, 5, 3);
    List<List<Point>> clusters = dbscan.run();

    // 打印结果
    for (int i = 0; i < clusters.size(); i++) {
      System.out.println("Cluster " + (i + 1) + ":");
      List<Point> clusterPoints = clusters.get(i);
      for (Point p : clusterPoints) {
        System.out.println("(" + p.getX() + ", " + p.getY() + ")");
      }
      System.out.println();
    }
  }
}

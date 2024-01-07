package org.matsim.rebalancingAnalysis.rebalancingDemandAccessibility;

import java.util.*;
import java.util.stream.Stream;

public class CycleCancelingAlgorithm {
    public static class Edge {
        final int to;
        int f;
        final int cap;
        final int cost;
        final int rev;
        Edge(int to, int cap, int cost, int rev) {
            this.to = to;
            this.cap = cap;
            this.cost = cost;
            this.rev = rev;
        }
        public int getFlow() {
            return f;
        }

        public int getTo() {
            return to;
        }

        @Override
        public String toString() {
            return cap + "/" + cost + "->" + to;
        }
    }

    public static void addEdge(List<Edge>[] graph, int s, int t, int cap, int cost) {
        graph[s].add(new Edge(t, cap, cost, graph[t].size()));
        graph[t].add(new Edge(s, 0, -cost, graph[s].size() - 1));
    }

    public static int[] minCostFlow(List<Edge>[] graph, int s, int t, int maxf) {
        int n = graph.length;
        int[] dist = new int[n];
        int[] pot = new int[n];
        int[] prevnode = new int[n];
        int[] prevedge = new int[n];
        int[] mark = new int[n];
        int[] delta = new int[n];

        int flow = 0;
        int flowCost = 0;

        while (flow < maxf) {
            boolean[] inQueue = new boolean[n];
            Arrays.fill(dist, Integer.MAX_VALUE);
            dist[s] = 0;

            Queue<Integer> queue = new LinkedList<>();
            queue.offer(s);
            inQueue[s] = true;

            while (!queue.isEmpty()) {
                int u = queue.poll();
                inQueue[u] = false;

                for (int i = 0; i < graph[u].size(); i++) {
                    Edge e = graph[u].get(i);
                    if (e.cap <= e.f)
                        continue;

                    int v = e.to;
                    int nprio = dist[u] + e.cost + pot[u] - pot[v];

                    if (dist[v] > nprio) {
                        dist[v] = nprio;
                        prevnode[v] = u;
                        prevedge[v] = i;

                        if (!inQueue[v]) {
                            queue.offer(v);
                            inQueue[v] = true;
                        }
                    }
                }
            }

            if (dist[t] == Integer.MAX_VALUE)
                break;

            for (int i = 0; i < n; i++)
                pot[i] += dist[i];

            int df = maxf - flow;
            int v = t;

            while (v != s) {
                Edge e = graph[prevnode[v]].get(prevedge[v]);
                df = Math.min(df, e.cap - e.f);
                v = prevnode[v];
            }

            v = t;
            while (v != s) {
                Edge e = graph[prevnode[v]].get(prevedge[v]);
                e.f += df;
                graph[v].get(e.rev).f -= df;
                v = prevnode[v];
            }

            flow += df;
            flowCost += df * pot[t];
        }

        while (true) {
            Arrays.fill(mark, 0);
            Arrays.fill(delta, 0);

            boolean hasNegativeCycle = false;

            for (int u = 0; u < n; u++) {
                if (mark[u] == 0 && detectNegativeCycle(graph, mark, delta, u)) {
                    hasNegativeCycle = true;

                    // Find the bottleneck capacity along the negative cycle
                    int bottleneck = Integer.MAX_VALUE;
                    int v = u;
                    do {
                        Edge e = graph[prevnode[v]].get(prevedge[v]);
                        bottleneck = Math.min(bottleneck, e.cap - e.f);
                        v = prevnode[v];
                    } while (v != u);

                    // Adjust the flow along the negative cycle
                    v = u;
                    do {
                        Edge e = graph[prevnode[v]].get(prevedge[v]);
                        e.f += bottleneck;
                        graph[v].get(e.rev).f -= bottleneck;
                        v = prevnode[v];
                    } while (v != u);
                }
            }

            if (!hasNegativeCycle)
                break;
        }

        return new int[]{flow, flowCost};
    }

    public static boolean detectNegativeCycle(List<Edge>[] graph, int[] mark, int[] delta, int u) {
        mark[u] = 1;

        for (Edge e : graph[u]) {
            if (e.cap > e.f) {
                int v = e.to;
                int potential = delta[u] + e.cost - delta[v];

                if (potential < 0) {
                    if (mark[v] == 1)
                        return true;

                    if (mark[v] == 0 && detectNegativeCycle(graph, mark, delta, v))
                        return true;
                }
            }
        }

        mark[u] = 2;
        return false;
    }

    // Usage example
    public static void main(String[] args) {
        @SuppressWarnings("unchecked")
        List<Edge>[] graph = Stream.generate(ArrayList::new).limit(3).toArray(List[]::new);
        addEdge(graph, 0, 1, 3, 1);
        addEdge(graph, 0, 2, 2, 1);
        addEdge(graph, 1, 2, 2, 1);
        int[] res = minCostFlow(graph, 0, 2, Integer.MAX_VALUE);
        int flow = res[0];
        int flowCost = res[1];
        System.out.println(4 == flow);
        System.out.println(6 == flowCost);
    }
}

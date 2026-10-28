'use client';

import { useQuery } from '@tanstack/react-query';
import { api, MetricsSummaryDto } from '@/lib/api';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Activity,
  Layers,
  Skull,
  ShieldAlert,
  Bot,
  Clock,
  Coins,
  RefreshCw,
  Cpu,
} from 'lucide-react';

export function MetricsView() {
  const { data: metrics, isLoading, isRefetching, refetch } = useQuery<MetricsSummaryDto>({
    queryKey: ['metrics-summary'],
    queryFn: () => api.metrics.getSummary(),
    refetchInterval: 5000,
  });

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center p-12 text-muted-foreground">
        <RefreshCw className="w-8 h-8 animate-spin mb-4 text-primary" />
        <p>Loading real-time platform metrics...</p>
      </div>
    );
  }

  const agentMetrics = metrics?.agentMetrics || [];
  const stateCounts = metrics?.ticketsByState || {};

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-card/60 backdrop-blur-md p-6 rounded-xl border shadow-sm">
        <div>
          <div className="flex items-center gap-3">
            <h2 className="text-2xl font-bold tracking-tight">System & AI Metrics</h2>
            <Badge variant="outline" className="bg-emerald-500/10 text-emerald-400 border-emerald-500/20 text-xs">
              Live Micrometer Stream
            </Badge>
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            Real-time telemetry measuring virtual-thread queue depth, agent latencies, and token cost.
          </p>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={() => refetch()}
          disabled={isRefetching}
          className="gap-2 self-start sm:self-auto"
        >
          <RefreshCw className={`w-4 h-4 ${isRefetching ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </div>

      {/* Overview Stat Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card className="bg-card/40 border-border/60">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">Total Ingested Tickets</CardTitle>
            <Activity className="w-4 h-4 text-blue-400" />
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold">{metrics?.totalTickets ?? 0}</div>
            <p className="text-xs text-muted-foreground mt-1">Across all workflow states</p>
          </CardContent>
        </Card>

        <Card className="bg-card/40 border-border/60">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">Active Queue Depth</CardTitle>
            <Layers className="w-4 h-4 text-purple-400" />
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-purple-400">{metrics?.queueDepth ?? 0}</div>
            <p className="text-xs text-muted-foreground mt-1">Virtual thread queue jobs in flight</p>
          </CardContent>
        </Card>

        <Card className="bg-card/40 border-border/60">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">Dead-Letter Count</CardTitle>
            <Skull className="w-4 h-4 text-rose-400" />
          </CardHeader>
          <CardContent>
            <div className={`text-3xl font-bold ${metrics?.deadLetterCount ? 'text-rose-400' : 'text-muted-foreground'}`}>
              {metrics?.deadLetterCount ?? 0}
            </div>
            <p className="text-xs text-muted-foreground mt-1">Exhausted retry jobs requiring redrive</p>
          </CardContent>
        </Card>

        <Card className="bg-card/40 border-border/60">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">Rate Limit Drops</CardTitle>
            <ShieldAlert className="w-4 h-4 text-amber-400" />
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-emerald-400">{metrics?.rateLimitRejections ?? 0}</div>
            <p className="text-xs text-muted-foreground mt-1">Enforced by Token-Bucket filter</p>
          </CardContent>
        </Card>
      </div>

      {/* AI Agents Performance Table */}
      <Card className="bg-card/40 border-border/60">
        <CardHeader>
          <div className="flex items-center gap-2">
            <Cpu className="w-5 h-5 text-indigo-400" />
            <CardTitle className="text-lg">AI Agent Latency & Cost Breakdown</CardTitle>
          </div>
          <CardDescription>
            Performance metrics aggregated across specialized AI agents in the processing pipeline.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {agentMetrics.length === 0 ? (
            <p className="text-sm text-muted-foreground text-center py-6">
              No agent runs recorded yet. Use the Simulator to ingest tickets.
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm text-left">
                <thead className="text-xs uppercase bg-muted/30 text-muted-foreground border-b">
                  <tr>
                    <th className="py-3 px-4">Agent</th>
                    <th className="py-3 px-4">Total Runs</th>
                    <th className="py-3 px-4">Avg Duration</th>
                    <th className="py-3 px-4">Total Tokens</th>
                    <th className="py-3 px-4 text-right">Est. Cost (USD)</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/40">
                  {agentMetrics.map((agent) => (
                    <tr key={agent.agentName} className="hover:bg-muted/10 transition-colors">
                      <td className="py-3 px-4 font-semibold flex items-center gap-2">
                        <Bot className="w-4 h-4 text-primary" />
                        {agent.agentName}
                      </td>
                      <td className="py-3 px-4">{agent.totalRuns}</td>
                      <td className="py-3 px-4">
                        <span className="flex items-center gap-1 font-mono text-xs">
                          <Clock className="w-3.5 h-3.5 text-muted-foreground" />
                          {agent.averageDurationMs}ms
                        </span>
                      </td>
                      <td className="py-3 px-4 font-mono text-xs">
                        {agent.totalTokens.toLocaleString()}
                      </td>
                      <td className="py-3 px-4 text-right font-mono text-xs text-emerald-400">
                        ${agent.totalCostUsd.toFixed(6)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Ticket Distribution by State */}
      <Card className="bg-card/40 border-border/60">
        <CardHeader>
          <div className="flex items-center gap-2">
            <Coins className="w-5 h-5 text-amber-400" />
            <CardTitle className="text-lg">Tickets by Workflow State</CardTitle>
          </div>
          <CardDescription>
            Live state machine distribution across all processed and pending tickets.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3">
            {Object.entries(stateCounts).map(([state, count]) => (
              <div
                key={state}
                className="p-3 rounded-lg border bg-card/60 flex flex-col justify-between"
              >
                <span className="text-xs font-mono text-muted-foreground truncate">{state}</span>
                <span className="text-2xl font-bold mt-1 text-foreground">{count}</span>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

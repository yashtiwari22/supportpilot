'use client';

import { useQuery } from '@tanstack/react-query';
import { api, Ticket, TicketEvent, AgentRun } from '@/lib/api';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Clock, Bot, Activity, DollarSign, Cpu, ArrowRight } from 'lucide-react';

interface TicketDetailDialogProps {
  ticket: Ticket | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function TicketDetailDialog({ ticket, open, onOpenChange }: TicketDetailDialogProps) {
  const ticketId = ticket?.id;

  const { data: events = [] } = useQuery<TicketEvent[]>({
    queryKey: ['ticket-events', ticketId],
    queryFn: () => (ticketId ? api.tickets.getEvents(ticketId) : Promise.resolve([])),
    enabled: !!ticketId && open,
  });

  const { data: agentRuns = [] } = useQuery<AgentRun[]>({
    queryKey: ['agent-runs', ticketId],
    queryFn: () => (ticketId ? api.tickets.getAgentRuns(ticketId) : Promise.resolve([])),
    enabled: !!ticketId && open,
  });

  if (!ticket) return null;

  const getPriorityVariant = (priority?: string) => {
    switch (priority?.toUpperCase()) {
      case 'URGENT':
        return 'destructive';
      case 'HIGH':
        return 'default';
      default:
        return 'secondary';
    }
  };

  const getSentimentColor = (sentiment?: string) => {
    switch (sentiment?.toUpperCase()) {
      case 'ANGRY':
        return 'bg-red-500/10 text-red-400 border-red-500/20';
      case 'NEGATIVE':
        return 'bg-amber-500/10 text-amber-400 border-amber-500/20';
      case 'POSITIVE':
        return 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20';
      default:
        return 'bg-slate-500/10 text-slate-400 border-slate-500/20';
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
        <DialogHeader className="border-b pb-4">
          <div className="flex items-center justify-between gap-4">
            <div>
              <DialogTitle className="text-xl font-bold tracking-tight">
                {ticket.subject}
              </DialogTitle>
              <DialogDescription className="text-sm text-muted-foreground mt-1">
                From <span className="font-medium text-foreground">{ticket.customerName}</span> ({ticket.customerEmail}) &bull; Created {new Date(ticket.createdAt).toLocaleString()}
              </DialogDescription>
            </div>
            <div className="flex items-center gap-2">
              <Badge variant="outline" className="capitalize">
                {ticket.status.toLowerCase().replace('_', ' ')}
              </Badge>
              {ticket.priority && (
                <Badge variant={getPriorityVariant(ticket.priority)}>
                  {ticket.priority}
                </Badge>
              )}
            </div>
          </div>
        </DialogHeader>

        <div className="py-2 space-y-6">
          {/* Customer Conversation Message */}
          <div className="rounded-lg border bg-card p-4 space-y-2">
            <div className="flex items-center justify-between text-xs text-muted-foreground">
              <span className="font-semibold text-foreground">Customer Message</span>
              <span>{new Date(ticket.createdAt).toLocaleTimeString()}</span>
            </div>
            <p className="text-sm leading-relaxed whitespace-pre-wrap text-foreground/90">
              {ticket.body}
            </p>
          </div>

          {/* AI Triage Card */}
          <div className="rounded-lg border bg-muted/40 p-4 space-y-3">
            <div className="flex items-center gap-2 text-sm font-semibold text-primary">
              <Bot className="w-4 h-4" />
              <span>AI Triage Intelligence</span>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm">
              <div className="p-2.5 rounded-md border bg-background/50">
                <div className="text-xs text-muted-foreground">Category</div>
                <div className="font-semibold mt-0.5">{ticket.category || 'Pending'}</div>
              </div>
              <div className="p-2.5 rounded-md border bg-background/50">
                <div className="text-xs text-muted-foreground">Priority</div>
                <div className="font-semibold mt-0.5">{ticket.priority || 'Pending'}</div>
              </div>
              <div className="p-2.5 rounded-md border bg-background/50">
                <div className="text-xs text-muted-foreground">Sentiment</div>
                <div className="mt-0.5">
                  <span className={`inline-flex px-2 py-0.5 text-xs font-semibold rounded-full border ${getSentimentColor(ticket.sentiment)}`}>
                    {ticket.sentiment || 'Pending'}
                  </span>
                </div>
              </div>
              <div className="p-2.5 rounded-md border bg-background/50">
                <div className="text-xs text-muted-foreground">Version (Lock)</div>
                <div className="font-semibold font-mono text-xs mt-0.5">v{ticket.version}</div>
              </div>
            </div>
          </div>

          {/* Tabs for Timeline and Audit Events */}
          <Tabs defaultValue="agents" className="w-full">
            <TabsList className="grid w-full grid-cols-2">
              <TabsTrigger value="agents" className="gap-2">
                <Cpu className="w-4 h-4" />
                <span>Agent Execution Runs ({agentRuns.length})</span>
              </TabsTrigger>
              <TabsTrigger value="events" className="gap-2">
                <Activity className="w-4 h-4" />
                <span>State Audit Log ({events.length})</span>
              </TabsTrigger>
            </TabsList>

            {/* Agent Runs Tab */}
            <TabsContent value="agents" className="space-y-3 pt-3">
              {agentRuns.length === 0 ? (
                <div className="text-center py-6 text-sm text-muted-foreground border rounded-lg bg-card/50">
                  No agent runs recorded yet. Triage worker may be currently processing.
                </div>
              ) : (
                agentRuns.map((run) => (
                  <div key={run.id} className="border rounded-lg p-3.5 bg-card/60 space-y-2">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <Bot className="w-4 h-4 text-primary" />
                        <span className="font-semibold text-sm">{run.agentName}</span>
                        <Badge variant="outline" className="text-xs font-mono">
                          {run.model}
                        </Badge>
                      </div>
                      <div className="flex items-center gap-3 text-xs text-muted-foreground">
                        <span className="flex items-center gap-1">
                          <Clock className="w-3.5 h-3.5" />
                          {run.durationMs}ms
                        </span>
                        <span className="flex items-center gap-1">
                          <Cpu className="w-3.5 h-3.5" />
                          {run.promptTokens + run.completionTokens} tokens
                        </span>
                        <span className="flex items-center gap-1 text-emerald-400 font-mono">
                          <DollarSign className="w-3 h-3" />
                          ${Number(run.estimatedCostUsd).toFixed(6)}
                        </span>
                        <Badge variant={run.status === 'SUCCESS' ? 'default' : 'destructive'} className="text-xs">
                          {run.status}
                        </Badge>
                      </div>
                    </div>
                    {run.outputPayload && (
                      <div className="text-xs bg-muted/60 rounded p-2.5 font-mono overflow-x-auto text-muted-foreground">
                        {run.outputPayload}
                      </div>
                    )}
                  </div>
                ))
              )}
            </TabsContent>

            {/* Audit Log Tab */}
            <TabsContent value="events" className="space-y-3 pt-3">
              {events.length === 0 ? (
                <div className="text-center py-6 text-sm text-muted-foreground border rounded-lg bg-card/50">
                  No events recorded.
                </div>
              ) : (
                <div className="relative border-l border-muted-foreground/20 ml-3 space-y-4 py-2">
                  {events.map((evt) => (
                    <div key={evt.id} className="relative pl-6">
                      <div className="absolute -left-1.5 top-1.5 w-3 h-3 rounded-full bg-primary ring-4 ring-background" />
                      <div className="border rounded-lg p-3 bg-card/60">
                        <div className="flex items-center justify-between text-xs">
                          <span className="font-semibold text-foreground">{evt.eventType}</span>
                          <span className="text-muted-foreground">
                            {new Date(evt.createdAt).toLocaleTimeString()}
                          </span>
                        </div>
                        {evt.fromState && evt.toState && (
                          <div className="flex items-center gap-1.5 text-xs text-muted-foreground mt-1.5">
                            <Badge variant="outline" className="text-[10px]">
                              {evt.fromState}
                            </Badge>
                            <ArrowRight className="w-3 h-3" />
                            <Badge variant="default" className="text-[10px]">
                              {evt.toState}
                            </Badge>
                          </div>
                        )}
                        {evt.payload && (
                          <div className="text-[11px] font-mono text-muted-foreground mt-1.5 bg-muted/40 p-2 rounded">
                            {evt.payload}
                          </div>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </TabsContent>
          </Tabs>
        </div>
      </DialogContent>
    </Dialog>
  );
}

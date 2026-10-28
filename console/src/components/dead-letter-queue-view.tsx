'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, DeadLetterJob } from '@/lib/api';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  Skull,
  RotateCcw,
  CheckCircle2,
  AlertOctagon,
  Clock,
  RefreshCw,
  Hash,
  Terminal,
} from 'lucide-react';

export function DeadLetterQueueView() {
  const queryClient = useQueryClient();

  const {
    data: deadLetters = [],
    isLoading,
    refetch,
    isRefetching,
  } = useQuery<DeadLetterJob[]>({
    queryKey: ['dead-letters'],
    queryFn: () => api.queue.listDeadLetters(),
  });

  const redriveMutation = useMutation({
    mutationFn: (jobId: string) => api.queue.redriveJob(jobId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dead-letters'] });
      queryClient.invalidateQueries({ queryKey: ['dlq-stats'] });
      queryClient.invalidateQueries({ queryKey: ['tickets'] });
    },
  });

  const redriveAllMutation = useMutation({
    mutationFn: () => api.queue.redriveAll(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dead-letters'] });
      queryClient.invalidateQueries({ queryKey: ['dlq-stats'] });
      queryClient.invalidateQueries({ queryKey: ['tickets'] });
    },
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold tracking-tight flex items-center gap-2">
            <Skull className="w-5 h-5 text-destructive" />
            Dead-Letter Queue (DLQ) & Poison Pills
          </h2>
          <p className="text-sm text-muted-foreground mt-0.5">
            Monitor jobs that exceeded maximum retries or encountered unrecoverable exceptions. Inspect error traces and redrive safely.
          </p>
        </div>

        <div className="flex items-center gap-2">
          {deadLetters.length > 0 && (
            <Button
              size="sm"
              variant="default"
              className="gap-1.5 h-8 text-xs bg-amber-600 hover:bg-amber-700 text-white font-medium"
              onClick={() => {
                if (confirm(`Redrive all ${deadLetters.length} dead-lettered jobs back to PENDING?`)) {
                  redriveAllMutation.mutate();
                }
              }}
              disabled={redriveAllMutation.isPending}
            >
              <RotateCcw className={`w-3.5 h-3.5 ${redriveAllMutation.isPending ? 'animate-spin' : ''}`} />
              Redrive All ({deadLetters.length})
            </Button>
          )}

          <Button
            variant="outline"
            size="sm"
            onClick={() => refetch()}
            disabled={isRefetching}
            className="gap-1.5 h-8 text-xs"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isRefetching ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
        </div>
      </div>

      {isLoading ? (
        <div className="space-y-3">
          {[1, 2].map((i) => (
            <div key={i} className="h-32 rounded-lg border bg-card/40 animate-pulse" />
          ))}
        </div>
      ) : deadLetters.length === 0 ? (
        <Card className="border-dashed bg-card/40">
          <CardContent className="flex flex-col items-center justify-center py-12 text-center">
            <div className="w-12 h-12 rounded-full bg-emerald-500/10 flex items-center justify-center mb-3">
              <CheckCircle2 className="w-6 h-6 text-emerald-400" />
            </div>
            <h3 className="font-semibold text-lg">Dead-Letter Queue is Healthy</h3>
            <p className="text-sm text-muted-foreground max-w-md mt-1">
              Zero failed or poisoned background jobs. When workers exhaust retries on transient errors or encounter schema failures, they are safely staged here for inspection and redriving.
            </p>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-3">
          {deadLetters.map((job) => (
            <Card key={job.id} className="border-destructive/30 bg-card/70 shadow-xs">
              <CardHeader className="pb-2.5">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <AlertOctagon className="w-4 h-4 text-destructive" />
                      <CardTitle className="text-sm font-semibold font-mono">
                        Job: {job.id}
                      </CardTitle>
                      <Badge variant="destructive" className="text-[10px]">
                        DEAD_LETTER
                      </Badge>
                      <Badge variant="outline" className="font-mono text-[10px]">
                        Retries: {job.retryCount}/{job.maxRetries}
                      </Badge>
                    </div>

                    <CardDescription className="text-xs flex items-center gap-2 font-mono text-muted-foreground">
                      <Hash className="w-3 h-3" />
                      <span>Ticket: {job.ticketId || 'Unknown'}</span> &bull;
                      <Clock className="w-3 h-3 ml-1" />
                      <span>{new Date(job.updatedAt || job.createdAt).toLocaleString()}</span>
                    </CardDescription>
                  </div>

                  <Button
                    size="sm"
                    variant="outline"
                    className="gap-1.5 h-8 text-xs border-primary/40 hover:bg-primary/10 self-end sm:self-center"
                    onClick={() => redriveMutation.mutate(job.id)}
                    disabled={redriveMutation.isPending}
                  >
                    <RotateCcw className={`w-3.5 h-3.5 ${redriveMutation.isPending ? 'animate-spin' : ''}`} />
                    Redrive Job
                  </Button>
                </div>
              </CardHeader>

              <CardContent className="pt-0">
                {job.errorMessage && (
                  <div className="mt-1 p-2.5 rounded bg-destructive/10 border border-destructive/20 text-xs font-mono text-destructive flex items-start gap-2 overflow-x-auto">
                    <Terminal className="w-4 h-4 shrink-0 mt-0.5" />
                    <span className="leading-relaxed">{job.errorMessage}</span>
                  </div>
                )}
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}

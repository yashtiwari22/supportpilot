'use client';

import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Sparkles, Loader2, Play } from 'lucide-react';

interface SimulatorModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  tenantId?: string;
  onGenerated?: () => void;
}

export function SimulatorModal({ open, onOpenChange, tenantId, onGenerated }: SimulatorModalProps) {
  const [ticketCount, setTicketCount] = useState<number>(3);
  const queryClient = useQueryClient();

  const generateMutation = useMutation({
    mutationFn: () => api.demo.generateTickets(ticketCount, tenantId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tickets'] });
      onGenerated?.();
      onOpenChange(false);
    },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <div className="flex items-center gap-2 text-primary font-semibold">
            <Sparkles className="w-5 h-5 text-amber-400" />
            <span>Ticket Traffic Simulator</span>
          </div>
          <DialogTitle className="text-lg font-bold">
            Generate Demo Ingestion Tickets
          </DialogTitle>
          <DialogDescription className="text-sm text-muted-foreground">
            Inject realistic customer support tickets into the live event-driven queue to observe automated classification and live SSE streaming in real time.
          </DialogDescription>
        </DialogHeader>

        <div className="py-4 space-y-4">
          <div className="space-y-2">
            <label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              Number of Tickets to Generate
            </label>
            <div className="grid grid-cols-3 gap-2">
              {[1, 3, 5].map((count) => (
                <Button
                  key={count}
                  type="button"
                  variant={ticketCount === count ? 'default' : 'outline'}
                  onClick={() => setTicketCount(count)}
                  className="font-medium"
                >
                  {count} {count === 1 ? 'Ticket' : 'Tickets'}
                </Button>
              ))}
            </div>
          </div>

          <div className="p-3 rounded-lg border bg-muted/40 text-xs text-muted-foreground space-y-1">
            <p className="font-semibold text-foreground">Scenarios Included:</p>
            <ul className="list-disc list-inside space-y-0.5">
              <li>Order #ORD-1042 delivery delay & express tracking</li>
              <li>Damaged product refund request (Acme Headphones)</li>
              <li>Bluetooth pairing and disconnect bug</li>
              <li>Duplicate monthly billing invoice dispute</li>
              <li>Urgent angry customer cancellation request</li>
            </ul>
          </div>
        </div>

        <div className="flex items-center justify-end gap-2 border-t pt-4">
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={generateMutation.isPending}>
            Cancel
          </Button>
          <Button
            onClick={() => generateMutation.mutate()}
            disabled={generateMutation.isPending}
            className="gap-2"
          >
            {generateMutation.isPending ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                <span>Injecting Tickets...</span>
              </>
            ) : (
              <>
                <Play className="w-4 h-4 fill-current" />
                <span>Run Simulator</span>
              </>
            )}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

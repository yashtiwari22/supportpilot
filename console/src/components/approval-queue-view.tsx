'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApprovalDto } from '@/lib/api';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Textarea } from '@/components/ui/textarea';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import {
  ShieldAlert,
  CheckCircle,
  XCircle,
  Edit3,
  Clock,
  User,
  DollarSign,
  AlertTriangle,
  RefreshCw,
  Sparkles,
} from 'lucide-react';

export function ApprovalQueueView() {
  const queryClient = useQueryClient();
  const [editingApproval, setEditingApproval] = useState<ApprovalDto | null>(null);
  const [editedReply, setEditedReply] = useState('');
  const [reviewNotes, setReviewNotes] = useState('');

  const [rejectingApproval, setRejectingApproval] = useState<ApprovalDto | null>(null);
  const [rejectReason, setRejectReason] = useState('');

  const {
    data: approvals = [],
    isLoading,
    refetch,
    isRefetching,
  } = useQuery<ApprovalDto[]>({
    queryKey: ['pending-approvals'],
    queryFn: () => api.approvals.listPending(),
  });

  const approveMutation = useMutation({
    mutationFn: ({ id, notes }: { id: string; notes?: string }) =>
      api.approvals.approve(id, notes),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pending-approvals'] });
      queryClient.invalidateQueries({ queryKey: ['tickets'] });
    },
  });

  const editAndApproveMutation = useMutation({
    mutationFn: ({ id, editedReply, notes }: { id: string; editedReply: string; notes?: string }) =>
      api.approvals.editAndApprove(id, editedReply, notes),
    onSuccess: () => {
      setEditingApproval(null);
      setEditedReply('');
      setReviewNotes('');
      queryClient.invalidateQueries({ queryKey: ['pending-approvals'] });
      queryClient.invalidateQueries({ queryKey: ['tickets'] });
    },
  });

  const rejectMutation = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason?: string }) =>
      api.approvals.reject(id, reason),
    onSuccess: () => {
      setRejectingApproval(null);
      setRejectReason('');
      queryClient.invalidateQueries({ queryKey: ['pending-approvals'] });
      queryClient.invalidateQueries({ queryKey: ['tickets'] });
    },
  });

  const handleOpenEdit = (approval: ApprovalDto) => {
    setEditingApproval(approval);
    setEditedReply(approval.proposedReply);
    setReviewNotes('');
  };

  const handleOpenReject = (approval: ApprovalDto) => {
    setRejectingApproval(approval);
    setRejectReason('');
  };

  const parseAction = (actionStr?: string) => {
    if (!actionStr) return null;
    try {
      return JSON.parse(actionStr);
    } catch {
      return null;
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold tracking-tight flex items-center gap-2">
            <ShieldAlert className="w-5 h-5 text-amber-500" />
            Human-in-the-Loop Approval Queue
          </h2>
          <p className="text-sm text-muted-foreground mt-0.5">
            Interception gate for high-risk financial proposals and agent drafts with low confidence scores.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Badge variant="outline" className="font-mono text-xs">
            {approvals.length} {approvals.length === 1 ? 'Action Pending' : 'Actions Pending'}
          </Badge>
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
        <div className="space-y-4">
          {[1, 2].map((i) => (
            <div key={i} className="h-44 rounded-lg border bg-card/40 animate-pulse" />
          ))}
        </div>
      ) : approvals.length === 0 ? (
        <Card className="border-dashed bg-card/40">
          <CardContent className="flex flex-col items-center justify-center py-12 text-center">
            <div className="w-12 h-12 rounded-full bg-emerald-500/10 flex items-center justify-center mb-3">
              <CheckCircle className="w-6 h-6 text-emerald-400" />
            </div>
            <h3 className="font-semibold text-lg">All Caught Up!</h3>
            <p className="text-sm text-muted-foreground max-w-md mt-1">
              There are currently no drafts or financial actions awaiting human sign-off. The automated critic agent has verified and dispatched all eligible responses.
            </p>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-4">
          {approvals.map((approval) => {
            const proposedAction = parseAction(approval.proposedAction);

            return (
              <Card key={approval.id} className="border-amber-500/30 bg-card/60 shadow-md">
                <CardHeader className="pb-3 border-b border-border/50">
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                    <div>
                      <div className="flex items-center gap-2">
                        <CardTitle className="text-base font-semibold">
                          {approval.ticketSubject}
                        </CardTitle>
                        <Badge variant="outline" className="text-amber-400 border-amber-500/30 bg-amber-500/10 text-xs">
                          Pending Approval
                        </Badge>
                      </div>
                      <CardDescription className="text-xs mt-1 flex items-center gap-2">
                        <User className="w-3.5 h-3.5" />
                        <span>{approval.customerName}</span> &bull; <span>{approval.customerEmail}</span> &bull;
                        <Clock className="w-3.5 h-3.5 ml-1" />
                        <span>{new Date(approval.createdAt).toLocaleString()}</span>
                      </CardDescription>
                    </div>

                    <div className="flex items-center gap-2 self-end sm:self-center">
                      <Button
                        size="sm"
                        variant="default"
                        className="bg-emerald-600 hover:bg-emerald-700 text-white gap-1.5 h-8 text-xs"
                        onClick={() => approveMutation.mutate({ id: approval.id })}
                        disabled={approveMutation.isPending}
                      >
                        <CheckCircle className="w-3.5 h-3.5" />
                        Approve
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        className="gap-1.5 h-8 text-xs border-primary/40 hover:bg-primary/10"
                        onClick={() => handleOpenEdit(approval)}
                      >
                        <Edit3 className="w-3.5 h-3.5" />
                        Edit & Approve
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        className="gap-1.5 h-8 text-xs text-destructive border-destructive/30 hover:bg-destructive/10"
                        onClick={() => handleOpenReject(approval)}
                        disabled={rejectMutation.isPending}
                      >
                        <XCircle className="w-3.5 h-3.5" />
                        Reject
                      </Button>
                    </div>
                  </div>
                </CardHeader>

                <CardContent className="pt-4 space-y-4">
                  {/* Critic Notes & Financial Interception Banner */}
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs">
                    <div className="p-3 rounded-md border border-amber-500/30 bg-amber-500/5 space-y-1">
                      <div className="flex items-center gap-1.5 font-semibold text-amber-400">
                        <AlertTriangle className="w-4 h-4" />
                        <span>Critic Evaluation Reason</span>
                      </div>
                      <p className="text-muted-foreground leading-relaxed">
                        {approval.criticNotes || 'Triggered policy gate for review before dispatch.'}
                      </p>
                    </div>

                    {proposedAction && (
                      <div className="p-3 rounded-md border border-red-500/30 bg-red-500/5 space-y-1">
                        <div className="flex items-center justify-between">
                          <span className="flex items-center gap-1.5 font-semibold text-red-400">
                            <DollarSign className="w-4 h-4" />
                            Staged Financial Action: {proposedAction.actionType || 'REFUND'}
                          </span>
                          {proposedAction.amount && (
                            <Badge variant="destructive" className="font-mono text-[10px]">
                              ${Number(proposedAction.amount).toFixed(2)}
                            </Badge>
                          )}
                        </div>
                        <p className="text-muted-foreground">
                          Order Number: <span className="font-mono text-foreground">{proposedAction.orderNumber}</span>
                          {proposedAction.reason && ` &bull; Reason: ${proposedAction.reason}`}
                        </p>
                      </div>
                    )}
                  </div>

                  {/* Proposed Response */}
                  <div className="space-y-1.5">
                    <div className="flex items-center justify-between text-xs text-muted-foreground font-semibold">
                      <span className="flex items-center gap-1.5">
                        <Sparkles className="w-3.5 h-3.5 text-primary" />
                        Generated Response Draft
                      </span>
                    </div>
                    <div className="p-3.5 rounded-lg border bg-background/80 text-sm leading-relaxed whitespace-pre-wrap font-sans text-foreground/90">
                      {approval.proposedReply}
                    </div>
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}

      {/* Edit & Approve Modal */}
      <Dialog open={!!editingApproval} onOpenChange={(open) => !open && setEditingApproval(null)}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-lg">
              <Edit3 className="w-4 h-4 text-primary" />
              Edit & Approve Response
            </DialogTitle>
            <DialogDescription className="text-xs">
              Modify the AI draft directly. Once saved, this customized response will immediately be dispatched to the customer.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-2">
            <div>
              <label className="text-xs font-semibold text-muted-foreground block mb-1.5">
                Draft Reply
              </label>
              <Textarea
                rows={8}
                value={editedReply}
                onChange={(e) => setEditedReply(e.target.value)}
                className="font-sans text-sm leading-relaxed"
                placeholder="Compose reply..."
              />
            </div>

            <div>
              <label className="text-xs font-semibold text-muted-foreground block mb-1.5">
                Reviewer Notes (Internal Audit Log)
              </label>
              <Textarea
                rows={2}
                value={reviewNotes}
                onChange={(e) => setReviewNotes(e.target.value)}
                className="text-xs"
                placeholder="Optional explanation for audit trail (e.g. Adjusted tone, confirmed return policy)..."
              />
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" size="sm" onClick={() => setEditingApproval(null)}>
              Cancel
            </Button>
            <Button
              size="sm"
              className="bg-emerald-600 hover:bg-emerald-700 text-white gap-1.5"
              onClick={() => {
                if (editingApproval) {
                  editAndApproveMutation.mutate({
                    id: editingApproval.id,
                    editedReply,
                    notes: reviewNotes,
                  });
                }
              }}
              disabled={editAndApproveMutation.isPending || !editedReply.trim()}
            >
              <CheckCircle className="w-4 h-4" />
              Save & Approve
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Reject Modal */}
      <Dialog open={!!rejectingApproval} onOpenChange={(open) => !open && setRejectingApproval(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-lg text-destructive">
              <XCircle className="w-4 h-4" />
              Reject Response Draft
            </DialogTitle>
            <DialogDescription className="text-xs">
              Rejecting transitions this ticket to REJECTED. Please provide an audit reason for rejecting this automated resolution.
            </DialogDescription>
          </DialogHeader>

          <div className="py-2">
            <label className="text-xs font-semibold text-muted-foreground block mb-1.5">
              Rejection Reason
            </label>
            <Textarea
              rows={3}
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              className="text-xs"
              placeholder="e.g. Fraudulent order claim, outside warranty window..."
            />
          </div>

          <DialogFooter>
            <Button variant="outline" size="sm" onClick={() => setRejectingApproval(null)}>
              Cancel
            </Button>
            <Button
              size="sm"
              variant="destructive"
              className="gap-1.5"
              onClick={() => {
                if (rejectingApproval) {
                  rejectMutation.mutate({
                    id: rejectingApproval.id,
                    reason: rejectReason || 'Rejected by staff reviewer',
                  });
                }
              }}
              disabled={rejectMutation.isPending}
            >
              <XCircle className="w-4 h-4" />
              Confirm Rejection
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

'use client';

import { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api, Ticket, UserDto, WorkflowState, getStoredUser, ApprovalDto } from '@/lib/api';
import { useEventSource } from '@/hooks/useEventSource';
import { LoginCard } from '@/components/login-card';
import { SimulatorModal } from '@/components/simulator-modal';
import { TicketDetailDialog } from '@/components/ticket-detail-dialog';
import { ApprovalQueueView } from '@/components/approval-queue-view';
import { KnowledgeBaseView } from '@/components/knowledge-base-view';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent } from '@/components/ui/card';
import {
  Sparkles,
  Search,
  LogOut,
  RefreshCw,
  Clock,
  User,
  Inbox,
  CheckCircle2,
  Bot,
  AlertCircle,
  FileQuestion,
  Layers,
  ShieldAlert,
  BookOpen,
  LayoutGrid,
} from 'lucide-react';

export default function ConsoleDashboard() {
  const [mounted, setMounted] = useState(false);
  const [currentUser, setCurrentUser] = useState<UserDto | null>(null);
  const [activeTab, setActiveTab] = useState<'board' | 'approvals' | 'kb'>('board');
  const [selectedTicket, setSelectedTicket] = useState<Ticket | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [simulatorOpen, setSimulatorOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [categoryFilter, setCategoryFilter] = useState<string>('ALL');
  const [priorityFilter, setPriorityFilter] = useState<string>('ALL');

  useEffect(() => {
    setMounted(true);
    setCurrentUser(getStoredUser());
  }, []);

  // Real-time SSE connection with auto-reconnect and query invalidation
  const { status: sseStatus } = useEventSource({
    enabled: !!currentUser,
  });

  const { data: tickets = [], isLoading, refetch } = useQuery<Ticket[]>({
    queryKey: ['tickets', categoryFilter, priorityFilter, searchQuery],
    queryFn: () =>
      api.tickets.list({
        category: categoryFilter !== 'ALL' ? categoryFilter : undefined,
        priority: priorityFilter !== 'ALL' ? priorityFilter : undefined,
        search: searchQuery ? searchQuery : undefined,
      }),
    enabled: !!currentUser,
  });

  // Query pending approvals for badge count
  const { data: pendingApprovals = [] } = useQuery<ApprovalDto[]>({
    queryKey: ['pending-approvals'],
    queryFn: () => api.approvals.listPending(),
    enabled: !!currentUser,
  });

  if (!mounted) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <div className="flex items-center gap-2.5 text-muted-foreground text-xs font-medium">
          <div className="h-4 w-4 rounded-full border-2 border-primary border-t-transparent animate-spin" />
          <span>Loading SupportPilot Console...</span>
        </div>
      </div>
    );
  }

  if (!currentUser) {
    return <LoginCard onSuccess={(user) => setCurrentUser(user)} />;
  }

  const handleLogout = () => {
    api.auth.logout();
    setCurrentUser(null);
  };

  const getPriorityBadge = (priority?: string) => {
    switch (priority?.toUpperCase()) {
      case 'URGENT':
        return <Badge variant="destructive" className="animate-pulse text-[10px]">URGENT</Badge>;
      case 'HIGH':
        return <Badge className="bg-amber-600 hover:bg-amber-700 text-white text-[10px]">HIGH</Badge>;
      case 'MEDIUM':
        return <Badge variant="secondary" className="text-[10px]">MEDIUM</Badge>;
      case 'LOW':
        return <Badge variant="outline" className="text-[10px]">LOW</Badge>;
      default:
        return null;
    }
  };

  const getSentimentDot = (sentiment?: string) => {
    switch (sentiment?.toUpperCase()) {
      case 'ANGRY':
        return <span className="inline-block w-2 h-2 rounded-full bg-red-500" title="Sentiment: Angry" />;
      case 'NEGATIVE':
        return <span className="inline-block w-2 h-2 rounded-full bg-amber-500" title="Sentiment: Negative" />;
      case 'POSITIVE':
        return <span className="inline-block w-2 h-2 rounded-full bg-emerald-500" title="Sentiment: Positive" />;
      default:
        return <span className="inline-block w-2 h-2 rounded-full bg-slate-500" title="Sentiment: Neutral" />;
    }
  };

  // Group tickets into Kanban columns
  const columns: { title: string; states: WorkflowState[]; icon: React.ComponentType<{ className?: string }>; color: string }[] = [
    {
      title: 'Ingested',
      states: ['RECEIVED'],
      icon: Inbox,
      color: 'border-blue-500/30 text-blue-400',
    },
    {
      title: 'AI Triaged',
      states: ['TRIAGED'],
      icon: Bot,
      color: 'border-purple-500/30 text-purple-400',
    },
    {
      title: 'In Progress',
      states: ['PROCESSING', 'DRAFTED', 'CRITIQUED', 'REVISING'],
      icon: Layers,
      color: 'border-amber-500/30 text-amber-400',
    },
    {
      title: 'Awaiting Approval',
      states: ['AWAITING_APPROVAL'],
      icon: AlertCircle,
      color: 'border-orange-500/30 text-orange-400',
    },
    {
      title: 'Resolved & Sent',
      states: ['APPROVED', 'SENT'],
      icon: CheckCircle2,
      color: 'border-emerald-500/30 text-emerald-400',
    },
  ];

  return (
    <div className="flex flex-col min-h-screen">
      {/* Top Navigation Bar */}
      <header className="sticky top-0 z-40 border-b border-border/80 bg-background/95 backdrop-blur px-6 py-3.5">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary text-primary-foreground font-black text-lg">
              S
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-base font-bold tracking-tight">SupportPilot</h1>
                <Badge variant="outline" className="text-xs font-medium">
                  {currentUser.tenantName}
                </Badge>
              </div>
              <p className="text-xs text-muted-foreground">Autonomous Multi-Agent Platform</p>
            </div>
          </div>

          {/* Center Tabs Navigation */}
          <nav className="hidden md:flex items-center gap-1 bg-muted/50 p-1 rounded-lg border">
            <button
              onClick={() => setActiveTab('board')}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold transition-all ${
                activeTab === 'board'
                  ? 'bg-background text-foreground shadow-xs'
                  : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              <LayoutGrid className="w-3.5 h-3.5" />
              <span>Live Board</span>
            </button>

            <button
              onClick={() => setActiveTab('approvals')}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold transition-all relative ${
                activeTab === 'approvals'
                  ? 'bg-background text-foreground shadow-xs'
                  : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              <ShieldAlert className="w-3.5 h-3.5 text-amber-500" />
              <span>Approval Queue</span>
              {pendingApprovals.length > 0 && (
                <span className="ml-1 px-1.5 py-0.2 rounded-full text-[10px] bg-amber-500 text-black font-bold">
                  {pendingApprovals.length}
                </span>
              )}
            </button>

            <button
              onClick={() => setActiveTab('kb')}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold transition-all ${
                activeTab === 'kb'
                  ? 'bg-background text-foreground shadow-xs'
                  : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              <BookOpen className="w-3.5 h-3.5 text-primary" />
              <span>Knowledge Base</span>
            </button>
          </nav>

          <div className="flex items-center gap-3">
            {/* SSE Stream Status Indicator */}
            <div className="flex items-center gap-2 px-2.5 py-1 rounded-full border bg-card/60 text-xs">
              <span
                className={`w-2 h-2 rounded-full ${
                  sseStatus === 'connected'
                    ? 'bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.7)]'
                    : sseStatus === 'connecting'
                    ? 'bg-amber-500 animate-ping'
                    : 'bg-red-500'
                }`}
              />
              <span className="text-muted-foreground font-medium capitalize">
                SSE: {sseStatus}
              </span>
            </div>

            {/* Simulator Trigger */}
            <Button
              onClick={() => setSimulatorOpen(true)}
              size="sm"
              className="gap-2 bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white font-medium shadow-md shadow-indigo-500/20"
            >
              <Sparkles className="w-3.5 h-3.5 text-amber-300 fill-current" />
              <span>Simulate Tickets</span>
            </Button>

            {/* Refresh */}
            <Button variant="outline" size="icon" onClick={() => refetch()} title="Refresh Tickets">
              <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
            </Button>

            {/* User Profile & Logout */}
            <div className="flex items-center gap-2 pl-2 border-l">
              <div className="text-right hidden sm:block">
                <div className="text-xs font-semibold">{currentUser.fullName}</div>
                <div className="text-[10px] text-muted-foreground capitalize">{currentUser.role.toLowerCase()}</div>
              </div>
              <Button variant="ghost" size="icon" onClick={handleLogout} title="Log Out">
                <LogOut className="w-4 h-4 text-muted-foreground hover:text-foreground" />
              </Button>
            </div>
          </div>
        </div>

        {/* Mobile Navigation Tabs */}
        <div className="flex md:hidden items-center justify-around gap-1 mt-3 pt-3 border-t">
          <button
            onClick={() => setActiveTab('board')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold ${
              activeTab === 'board' ? 'bg-primary/10 text-primary' : 'text-muted-foreground'
            }`}
          >
            <LayoutGrid className="w-3.5 h-3.5" />
            <span>Board</span>
          </button>
          <button
            onClick={() => setActiveTab('approvals')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold relative ${
              activeTab === 'approvals' ? 'bg-primary/10 text-primary' : 'text-muted-foreground'
            }`}
          >
            <ShieldAlert className="w-3.5 h-3.5 text-amber-500" />
            <span>Approvals ({pendingApprovals.length})</span>
          </button>
          <button
            onClick={() => setActiveTab('kb')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold ${
              activeTab === 'kb' ? 'bg-primary/10 text-primary' : 'text-muted-foreground'
            }`}
          >
            <BookOpen className="w-3.5 h-3.5 text-primary" />
            <span>Knowledge Base</span>
          </button>
        </div>

        {/* Filter and Search Bar (Active only on Board tab) */}
        {activeTab === 'board' && (
          <div className="flex flex-wrap items-center justify-between gap-3 pt-3 mt-3 border-t">
            <div className="relative flex-1 max-w-sm">
              <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder="Search by subject, customer, email..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-8 h-9 text-xs"
              />
            </div>

            <div className="flex items-center gap-2">
              <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <span>Category:</span>
                <select
                  value={categoryFilter}
                  onChange={(e) => setCategoryFilter(e.target.value)}
                  className="bg-card border rounded-md text-xs px-2 py-1 text-foreground"
                >
                  <option value="ALL">All Categories</option>
                  <option value="BILLING">Billing</option>
                  <option value="ORDER_INQUIRY">Order Inquiry</option>
                  <option value="TECHNICAL">Technical</option>
                  <option value="GENERAL">General</option>
                </select>
              </div>

              <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <span>Priority:</span>
                <select
                  value={priorityFilter}
                  onChange={(e) => setPriorityFilter(e.target.value)}
                  className="bg-card border rounded-md text-xs px-2 py-1 text-foreground"
                >
                  <option value="ALL">All Priorities</option>
                  <option value="URGENT">Urgent</option>
                  <option value="HIGH">High</option>
                  <option value="MEDIUM">Medium</option>
                  <option value="LOW">Low</option>
                </select>
              </div>
            </div>
          </div>
        )}
      </header>

      {/* Main Content Area */}
      <main className="flex-1 p-6 overflow-x-auto">
        {activeTab === 'board' && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4 min-w-[1100px]">
            {columns.map((col) => {
              const colTickets = tickets.filter((t) => col.states.includes(t.status));
              const ColIcon = col.icon;

              return (
                <div key={col.title} className="flex flex-col rounded-xl border bg-card/40 p-3 shadow-xs">
                  {/* Column Header */}
                  <div className="flex items-center justify-between pb-3 mb-3 border-b">
                    <div className="flex items-center gap-2">
                      <div className={`p-1 rounded-md border ${col.color}`}>
                        <ColIcon className="w-3.5 h-3.5" />
                      </div>
                      <span className="font-semibold text-xs tracking-tight">{col.title}</span>
                    </div>
                    <Badge variant="secondary" className="font-mono text-[10px] px-1.5">
                      {colTickets.length}
                    </Badge>
                  </div>

                  {/* Ticket Cards */}
                  <div className="space-y-2.5 flex-1 overflow-y-auto max-h-[calc(100vh-230px)] pr-1">
                    {colTickets.length === 0 ? (
                      <div className="flex flex-col items-center justify-center py-10 text-center text-muted-foreground/60">
                        <FileQuestion className="w-6 h-6 stroke-1 mb-1.5" />
                        <span className="text-xs">No tickets</span>
                      </div>
                    ) : (
                      colTickets.map((ticket) => (
                        <Card
                          key={ticket.id}
                          onClick={() => {
                            setSelectedTicket(ticket);
                            setDetailOpen(true);
                          }}
                          className="cursor-pointer hover:border-primary/50 hover:shadow-md transition-all duration-150 bg-card border-border/60 group"
                        >
                          <CardContent className="p-3 space-y-2">
                            <div className="flex items-start justify-between gap-1.5">
                              <h3 className="font-semibold text-xs leading-snug line-clamp-2 group-hover:text-primary transition-colors">
                                {ticket.subject}
                              </h3>
                              {getSentimentDot(ticket.sentiment)}
                            </div>

                            <p className="text-[11px] text-muted-foreground line-clamp-2 leading-relaxed">
                              {ticket.body}
                            </p>

                            <div className="flex items-center justify-between pt-1 border-t border-border/40 text-[10px] text-muted-foreground">
                              <div className="flex items-center gap-1 truncate max-w-[110px]">
                                <User className="w-3 h-3 shrink-0" />
                                <span className="truncate">{ticket.customerName || ticket.customerEmail}</span>
                              </div>
                              <div className="flex items-center gap-1 shrink-0">
                                <Clock className="w-3 h-3" />
                                <span>{new Date(ticket.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                              </div>
                            </div>

                            <div className="flex items-center justify-between gap-1 pt-0.5">
                              {ticket.category ? (
                                <Badge variant="outline" className="text-[9px] px-1 py-0 uppercase">
                                  {ticket.category}
                                </Badge>
                              ) : <span />}
                              {getPriorityBadge(ticket.priority)}
                            </div>
                          </CardContent>
                        </Card>
                      ))
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {activeTab === 'approvals' && <ApprovalQueueView />}

        {activeTab === 'kb' && <KnowledgeBaseView />}
      </main>

      {/* Ticket Details & Audit Drawer */}
      <TicketDetailDialog
        ticket={selectedTicket}
        open={detailOpen}
        onOpenChange={setDetailOpen}
      />

      {/* Simulator Modal */}
      <SimulatorModal
        open={simulatorOpen}
        onOpenChange={setSimulatorOpen}
        tenantId={currentUser.tenantId}
        onGenerated={() => refetch()}
      />
    </div>
  );
}

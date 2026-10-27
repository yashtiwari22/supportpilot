'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api, Ticket, UserDto, WorkflowState, getStoredUser } from '@/lib/api';
import { useEventSource } from '@/hooks/useEventSource';
import { LoginCard } from '@/components/login-card';
import { SimulatorModal } from '@/components/simulator-modal';
import { TicketDetailDialog } from '@/components/ticket-detail-dialog';
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
} from 'lucide-react';

export default function ConsoleDashboard() {
  const [currentUser, setCurrentUser] = useState<UserDto | null>(() => getStoredUser());
  const [selectedTicket, setSelectedTicket] = useState<Ticket | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [simulatorOpen, setSimulatorOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [categoryFilter, setCategoryFilter] = useState<string>('ALL');
  const [priorityFilter, setPriorityFilter] = useState<string>('ALL');

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
              className="gap-2 bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white font-medium"
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

        {/* Filter and Search Bar */}
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
      </header>

      {/* Main Kanban Board Canvas */}
      <main className="flex-1 p-6 overflow-x-auto bg-muted/20">
        <div className="flex gap-4 min-w-[1200px] h-[calc(100vh-170px)]">
          {columns.map((col) => {
            const colTickets = tickets.filter((t) => col.states.includes(t.status));
            const ColIcon = col.icon;

            return (
              <div
                key={col.title}
                className="flex-1 flex flex-col rounded-xl border border-border/80 bg-card/60 backdrop-blur overflow-hidden"
              >
                {/* Column Header */}
                <div className="p-3.5 border-b flex items-center justify-between bg-muted/30">
                  <div className="flex items-center gap-2">
                    <ColIcon className={`w-4 h-4 ${col.color}`} />
                    <h2 className="text-xs font-bold uppercase tracking-wider text-foreground">
                      {col.title}
                    </h2>
                  </div>
                  <Badge variant="secondary" className="text-xs font-mono font-semibold px-2 py-0">
                    {colTickets.length}
                  </Badge>
                </div>

                {/* Column Body / Tickets List */}
                <div className="flex-1 p-3 overflow-y-auto space-y-3">
                  {colTickets.length === 0 ? (
                    <div className="flex flex-col items-center justify-center h-36 border border-dashed rounded-lg text-muted-foreground text-xs p-4 text-center">
                      <FileQuestion className="w-6 h-6 mb-1 opacity-50" />
                      <span>No tickets in this stage</span>
                    </div>
                  ) : (
                    colTickets.map((ticket) => (
                      <Card
                        key={ticket.id}
                        onClick={() => {
                          setSelectedTicket(ticket);
                          setDetailOpen(true);
                        }}
                        className="cursor-pointer border-border/70 hover:border-primary/60 transition-all hover:shadow-lg bg-card/90 group"
                      >
                        <CardContent className="p-3.5 space-y-2.5">
                          <div className="flex items-center justify-between gap-2">
                            <div className="flex items-center gap-1.5">
                              {getSentimentDot(ticket.sentiment)}
                              {ticket.category ? (
                                <span className="text-[10px] font-semibold tracking-wider uppercase text-muted-foreground">
                                  {ticket.category.replace('_', ' ')}
                                </span>
                              ) : (
                                <span className="text-[10px] text-muted-foreground italic">Triaging...</span>
                              )}
                            </div>
                            {getPriorityBadge(ticket.priority)}
                          </div>

                          <h3 className="text-xs font-semibold text-foreground line-clamp-2 leading-snug group-hover:text-primary transition-colors">
                            {ticket.subject}
                          </h3>

                          <p className="text-[11px] text-muted-foreground line-clamp-2 leading-relaxed">
                            {ticket.body}
                          </p>

                          <div className="flex items-center justify-between pt-2 border-t text-[10px] text-muted-foreground">
                            <span className="flex items-center gap-1 truncate max-w-[130px]">
                              <User className="w-3 h-3 flex-shrink-0" />
                              <span className="truncate">{ticket.customerName}</span>
                            </span>
                            <span className="flex items-center gap-1">
                              <Clock className="w-3 h-3" />
                              <span>{new Date(ticket.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                            </span>
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
      </main>

      {/* Ticket Detail Dialog */}
      <TicketDetailDialog
        ticket={selectedTicket}
        open={detailOpen}
        onOpenChange={setDetailOpen}
      />

      {/* Simulator Modal */}
      <SimulatorModal
        open={simulatorOpen}
        onOpenChange={setSimulatorOpen}
        tenantId={currentUser?.tenantId}
      />
    </div>
  );
}

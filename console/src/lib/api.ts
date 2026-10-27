/**
 * Typed REST API client for SupportPilot Console.
 */

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export interface UserDto {
  id: string;
  tenantId: string;
  tenantName: string;
  email: string;
  fullName: string;
  role: 'ADMIN' | 'AGENT';
}

export interface LoginResponse {
  token: string;
  user: UserDto;
}

export type WorkflowState =
  | 'RECEIVED'
  | 'TRIAGED'
  | 'PROCESSING'
  | 'DRAFTED'
  | 'CRITIQUED'
  | 'AWAITING_APPROVAL'
  | 'APPROVED'
  | 'SENT'
  | 'REVISING'
  | 'REJECTED'
  | 'FAILED'
  | 'DEAD_LETTER';

export interface Ticket {
  id: string;
  tenantId: string;
  customerEmail: string;
  customerName: string;
  subject: string;
  body: string;
  status: WorkflowState;
  category?: string;
  priority?: string;
  sentiment?: string;
  idempotencyKey?: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface TicketEvent {
  id: string;
  ticketId: string;
  tenantId: string;
  eventType: string;
  fromState?: WorkflowState;
  toState?: WorkflowState;
  payload?: string;
  createdAt: string;
}

export interface AgentRun {
  id: string;
  ticketId: string;
  tenantId: string;
  agentName: string;
  model: string;
  promptTokens: number;
  completionTokens: number;
  estimatedCostUsd: number;
  durationMs: number;
  status: 'SUCCESS' | 'FAILED' | 'TIMED_OUT';
  inputPayload?: string;
  outputPayload?: string;
  errorMessage?: string;
  createdAt: string;
}

export function getToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('sp_token');
}

export function setToken(token: string) {
  if (typeof window !== 'undefined') {
    localStorage.setItem('sp_token', token);
  }
}

export function clearToken() {
  if (typeof window !== 'undefined') {
    localStorage.removeItem('sp_token');
    localStorage.removeItem('sp_user');
  }
}

export function getStoredUser(): UserDto | null {
  if (typeof window === 'undefined') return null;
  const user = localStorage.getItem('sp_user');
  return user ? JSON.parse(user) : null;
}

export function setStoredUser(user: UserDto) {
  if (typeof window !== 'undefined') {
    localStorage.setItem('sp_user', JSON.stringify(user));
  }
}

async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const token = getToken();
  const headers = new Headers(options.headers || {});
  headers.set('Content-Type', 'application/json');

  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers,
  });

  if (!response.ok) {
    let errorMsg = `Request failed: ${response.statusText}`;
    try {
      const errJson = await response.json();
      errorMsg = errJson.message || errorMsg;
    } catch {
      // ignore
    }
    throw new Error(errorMsg);
  }

  return response.json();
}

export const api = {
  auth: {
    login: async (email: string, password: string): Promise<LoginResponse> => {
      const res = await request<LoginResponse>('/api/v1/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      });
      setToken(res.token);
      setStoredUser(res.user);
      return res;
    },
    me: async (): Promise<UserDto> => {
      return request<UserDto>('/api/v1/auth/me');
    },
    logout: () => {
      clearToken();
    },
  },

  tickets: {
    list: async (filters?: {
      status?: WorkflowState;
      priority?: string;
      category?: string;
      search?: string;
    }): Promise<Ticket[]> => {
      const params = new URLSearchParams();
      if (filters?.status) params.set('status', filters.status);
      if (filters?.priority) params.set('priority', filters.priority);
      if (filters?.category) params.set('category', filters.category);
      if (filters?.search) params.set('search', filters.search);

      const qs = params.toString() ? `?${params.toString()}` : '';
      return request<Ticket[]>(`/api/v1/tickets${qs}`);
    },

    get: async (id: string): Promise<Ticket> => {
      return request<Ticket>(`/api/v1/tickets/${id}`);
    },

    getEvents: async (id: string): Promise<TicketEvent[]> => {
      return request<TicketEvent[]>(`/api/v1/tickets/${id}/events`);
    },

    getAgentRuns: async (id: string): Promise<AgentRun[]> => {
      return request<AgentRun[]>(`/api/v1/tickets/${id}/agent-runs`);
    },
  },

  approvals: {
    listPending: async (): Promise<ApprovalDto[]> => {
      return request<ApprovalDto[]>('/api/v1/approvals');
    },

    approve: async (id: string, notes?: string): Promise<void> => {
      await request<void>(`/api/v1/approvals/${id}/approve`, {
        method: 'POST',
        body: JSON.stringify({ notes }),
      });
    },

    editAndApprove: async (id: string, editedReply: string, notes?: string): Promise<void> => {
      await request<void>(`/api/v1/approvals/${id}/edit-and-approve`, {
        method: 'POST',
        body: JSON.stringify({ editedReply, notes }),
      });
    },

    reject: async (id: string, reason?: string): Promise<void> => {
      await request<void>(`/api/v1/approvals/${id}/reject`, {
        method: 'POST',
        body: JSON.stringify({ reason }),
      });
    },
  },

  kb: {
    listArticles: async (): Promise<KbArticleSummary[]> => {
      return request<KbArticleSummary[]>('/api/v1/kb/articles');
    },

    createArticle: async (req: CreateArticleRequest): Promise<unknown> => {
      return request<unknown>('/api/v1/kb/articles', {
        method: 'POST',
        body: JSON.stringify(req),
      });
    },

    deleteArticle: async (id: string): Promise<void> => {
      await request<void>(`/api/v1/kb/articles/${id}`, {
        method: 'DELETE',
      });
    },
  },

  demo: {
    generateTickets: async (count: number = 3, tenantId?: string): Promise<Ticket[]> => {
      return request<Ticket[]>('/api/v1/demo/generate-tickets', {
        method: 'POST',
        body: JSON.stringify({ count, tenantId }),
      });
    },
  },
};

export interface ApprovalDto {
  id: string;
  ticketId: string;
  customerName: string;
  customerEmail: string;
  ticketSubject: string;
  proposedReply: string;
  proposedAction?: string;
  criticNotes?: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  createdAt: string;
}

export interface KbArticleSummary {
  id: string;
  title: string;
  slug: string;
  chunkCount: number;
  createdAt: string;
}

export interface CreateArticleRequest {
  title: string;
  content: string;
}

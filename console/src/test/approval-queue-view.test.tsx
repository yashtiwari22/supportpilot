import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { ApprovalQueueView } from '@/components/approval-queue-view';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { api, ApprovalDto } from '@/lib/api';
import React from 'react';

vi.mock('@/lib/api', () => ({
  api: {
    approvals: {
      listPending: vi.fn(),
      approve: vi.fn(),
      editAndApprove: vi.fn(),
      reject: vi.fn(),
    },
  },
}));

describe('ApprovalQueueView Component', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    });
    vi.clearAllMocks();
  });

  const renderWithProviders = (ui: React.ReactElement) => {
    return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
  };

  it('renders empty state when there are no pending approvals', async () => {
    vi.mocked(api.approvals.listPending).mockResolvedValue([]);

    renderWithProviders(<ApprovalQueueView />);

    await waitFor(() => {
      expect(screen.getByText('All Caught Up!')).toBeInTheDocument();
    });
  });

  it('renders pending approval cards with staged financial refund', async () => {
    const mockApprovals: ApprovalDto[] = [
      {
        id: 'app-1',
        ticketId: 'tick-1',
        customerName: 'Sarah Connor',
        customerEmail: 'sarah@example.com',
        ticketSubject: 'Requesting refund for order ORD-1042',
        proposedReply: 'We have processed your refund of $149.99 for order ORD-1042.',
        proposedAction: JSON.stringify({
          actionType: 'REFUND',
          orderNumber: 'ORD-1042',
          amount: 149.99,
          reason: 'Customer requested return within 30-day window',
        }),
        criticNotes: 'Requires human approval: proposed financial action REFUND',
        status: 'PENDING',
        createdAt: new Date().toISOString(),
      },
    ];

    vi.mocked(api.approvals.listPending).mockResolvedValue(mockApprovals);

    renderWithProviders(<ApprovalQueueView />);

    await waitFor(() => {
      expect(screen.getByText('Requesting refund for order ORD-1042')).toBeInTheDocument();
      expect(screen.getByText('Sarah Connor')).toBeInTheDocument();
      expect(screen.getByText('Staged Financial Action: REFUND')).toBeInTheDocument();
      expect(screen.getByText('$149.99')).toBeInTheDocument();
      expect(screen.getByText('Approve')).toBeInTheDocument();
      expect(screen.getByText('Edit & Approve')).toBeInTheDocument();
      expect(screen.getByText('Reject')).toBeInTheDocument();
    });
  });
});

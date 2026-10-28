import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { DeadLetterQueueView } from '@/components/dead-letter-queue-view';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { api, DeadLetterJob } from '@/lib/api';
import React from 'react';

vi.mock('@/lib/api', () => ({
  api: {
    queue: {
      listDeadLetters: vi.fn(),
      getStats: vi.fn(),
      redriveJob: vi.fn(),
      redriveAll: vi.fn(),
    },
  },
}));

describe('DeadLetterQueueView Component', () => {
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

  it('renders healthy empty state when DLQ is empty', async () => {
    vi.mocked(api.queue.listDeadLetters).mockResolvedValue([]);

    renderWithProviders(<DeadLetterQueueView />);

    await waitFor(() => {
      expect(screen.getByText('Dead-Letter Queue is Healthy')).toBeInTheDocument();
    });
  });

  it('renders dead-lettered job cards with error details and redrive button', async () => {
    const mockJobs: DeadLetterJob[] = [
      {
        id: 'job-dead-1234',
        queueName: 'tickets',
        ticketId: 'ticket-999',
        tenantId: 'tenant-111',
        retryCount: 3,
        maxRetries: 3,
        errorMessage: 'Connection timeout after 3 attempts: database locked',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      },
    ];

    vi.mocked(api.queue.listDeadLetters).mockResolvedValue(mockJobs);

    renderWithProviders(<DeadLetterQueueView />);

    await waitFor(() => {
      expect(screen.getByText(/job-dead-1234/i)).toBeInTheDocument();
      expect(screen.getByText('Retries: 3/3')).toBeInTheDocument();
      expect(screen.getByText(/database locked/i)).toBeInTheDocument();
      expect(screen.getByText('Redrive Job')).toBeInTheDocument();
      expect(screen.getByText('Redrive All (1)')).toBeInTheDocument();
    });
  });
});

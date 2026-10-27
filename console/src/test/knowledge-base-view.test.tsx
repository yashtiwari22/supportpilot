import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { KnowledgeBaseView } from '@/components/knowledge-base-view';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { api, KbArticleSummary } from '@/lib/api';
import React from 'react';

vi.mock('@/lib/api', () => ({
  api: {
    kb: {
      listArticles: vi.fn(),
      createArticle: vi.fn(),
      deleteArticle: vi.fn(),
    },
  },
}));

describe('KnowledgeBaseView Component', () => {
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

  it('renders articles with chunk counts', async () => {
    const mockArticles: KbArticleSummary[] = [
      {
        id: 'art-1',
        title: '30-Day Return & Refund Policy',
        slug: 'return-policy',
        chunkCount: 3,
        createdAt: new Date().toISOString(),
      },
      {
        id: 'art-2',
        title: 'Hardware Warranty & Repairs',
        slug: 'warranty-repairs',
        chunkCount: 5,
        createdAt: new Date().toISOString(),
      },
    ];

    vi.mocked(api.kb.listArticles).mockResolvedValue(mockArticles);

    renderWithProviders(<KnowledgeBaseView />);

    await waitFor(() => {
      expect(screen.getByText('30-Day Return & Refund Policy')).toBeInTheDocument();
      expect(screen.getByText('Hardware Warranty & Repairs')).toBeInTheDocument();
      expect(screen.getByText('3 chunks')).toBeInTheDocument();
      expect(screen.getByText('5 chunks')).toBeInTheDocument();
      expect(screen.getByText('New Policy Article')).toBeInTheDocument();
    });
  });
});

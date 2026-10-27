import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { LoginCard } from '@/components/login-card';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

describe('LoginCard Component', () => {
  const queryClient = new QueryClient();
  const renderWithProviders = (ui: React.ReactElement) => {
    return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
  };

  it('renders login inputs and demo buttons', () => {
    renderWithProviders(<LoginCard onSuccess={vi.fn()} />);

    expect(screen.getByPlaceholderText('agent@company.com')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('••••••••')).toBeInTheDocument();
    expect(screen.getByText('Acme Electronics Admin')).toBeInTheDocument();
  });

  it('clicking demo button autofills email and password', () => {
    renderWithProviders(<LoginCard onSuccess={vi.fn()} />);

    const demoButton = screen.getByText('Acme Electronics Admin');
    fireEvent.click(demoButton);

    const emailInput = screen.getByPlaceholderText('agent@company.com') as HTMLInputElement;
    const passwordInput = screen.getByPlaceholderText('••••••••') as HTMLInputElement;

    expect(emailInput.value).toBe('admin@acme.com');
    expect(passwordInput.value).toBe('password123');
  });
});

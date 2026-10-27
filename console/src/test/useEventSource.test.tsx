import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useEventSource } from '@/hooks/useEventSource';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

// Mock EventSource
class MockEventSource {
  static instances: MockEventSource[] = [];
  url: string;
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  onmessage: ((e: MessageEvent) => void) | null = null;
  listeners: Record<string, ((e: unknown) => void)[]> = {};

  constructor(url: string) {
    this.url = url;
    MockEventSource.instances.push(this);
  }

  addEventListener(event: string, callback: (e: unknown) => void) {
    if (!this.listeners[event]) this.listeners[event] = [];
    this.listeners[event].push(callback);
  }

  removeEventListener(event: string, callback: (e: unknown) => void) {
    if (this.listeners[event]) {
      this.listeners[event] = this.listeners[event].filter(cb => cb !== callback);
    }
  }

  close() {}
}

(globalThis as unknown as { EventSource: unknown }).EventSource = MockEventSource;

describe('useEventSource Hook', () => {
  const queryClient = new QueryClient();
  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  it('starts disconnected when no token is present', () => {
    localStorage.removeItem('sp_token');
    const { result } = renderHook(() => useEventSource(), { wrapper });
    expect(result.current.status).toBe('disconnected');
  });

  it('transitions to connecting and connected when token is available', () => {
    localStorage.setItem('sp_token', 'test-jwt-token');
    const { result } = renderHook(() => useEventSource(), { wrapper });

    expect(result.current.status).toBe('connecting');

    // Simulate open
    const instance = MockEventSource.instances[MockEventSource.instances.length - 1];
    act(() => {
      if (instance?.onopen) instance.onopen();
    });

    expect(result.current.status).toBe('connected');
  });
});

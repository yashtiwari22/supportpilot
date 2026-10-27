'use client';

import { useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { getToken } from '@/lib/api';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export type ConnectionStatus = 'connected' | 'connecting' | 'disconnected';

export interface UseEventSourceOptions {
  onEvent?: (event: MessageEvent) => void;
  enabled?: boolean;
}

/**
 * Custom hook providing a resilient Server-Sent Events (SSE) connection with automatic backoff reconnection.
 */
export function useEventSource({ onEvent, enabled = true }: UseEventSourceOptions = {}) {
  const [status, setStatus] = useState<ConnectionStatus>('disconnected');
  const [lastEvent, setLastEvent] = useState<{ type: string; data: unknown; timestamp: Date } | null>(null);
  const queryClient = useQueryClient();
  const eventSourceRef = useRef<EventSource | null>(null);
  const reconnectTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const reconnectAttemptsRef = useRef<number>(0);

  useEffect(() => {
    if (!enabled) {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }
      return;
    }

    const connect = () => {
      const token = getToken();
      if (!token) {
        setStatus('disconnected');
        return;
      }

      setStatus('connecting');
      const url = `${API_BASE_URL}/api/v1/stream?token=${encodeURIComponent(token)}`;
      const es = new EventSource(url);
      eventSourceRef.current = es;

      es.onopen = () => {
        setStatus('connected');
        reconnectAttemptsRef.current = 0;
      };

      es.addEventListener('connected', () => {
        setStatus('connected');
      });

      // Handle ticket creation event
      es.addEventListener('ticket_created', (e: MessageEvent) => {
        try {
          const data = JSON.parse(e.data);
          setLastEvent({ type: 'ticket_created', data, timestamp: new Date() });
          queryClient.invalidateQueries({ queryKey: ['tickets'] });
        } catch (err) {
          console.error('Failed to parse ticket_created event:', err);
        }
      });

      // Handle ticket state progression event
      es.addEventListener('ticket_state_changed', (e: MessageEvent) => {
        try {
          const data = JSON.parse(e.data);
          setLastEvent({ type: 'ticket_state_changed', data, timestamp: new Date() });
          queryClient.invalidateQueries({ queryKey: ['tickets'] });
          if (data.ticketId) {
            queryClient.invalidateQueries({ queryKey: ['ticket', data.ticketId] });
            queryClient.invalidateQueries({ queryKey: ['ticket-events', data.ticketId] });
          }
        } catch (err) {
          console.error('Failed to parse ticket_state_changed event:', err);
        }
      });

      // Handle agent step completion event
      es.addEventListener('agent_step_completed', (e: MessageEvent) => {
        try {
          const data = JSON.parse(e.data);
          setLastEvent({ type: 'agent_step_completed', data, timestamp: new Date() });
          if (data.ticketId) {
            queryClient.invalidateQueries({ queryKey: ['agent-runs', data.ticketId] });
          }
        } catch (err) {
          console.error('Failed to parse agent_step_completed event:', err);
        }
      });

      es.onerror = () => {
        es.close();
        eventSourceRef.current = null;
        setStatus('connecting');

        // Exponential backoff reconnect: 1s, 2s, 4s, up to 10s
        const delay = Math.min(1000 * Math.pow(2, reconnectAttemptsRef.current), 10000);
        reconnectAttemptsRef.current += 1;

        if (reconnectTimeoutRef.current) {
          clearTimeout(reconnectTimeoutRef.current);
        }
        reconnectTimeoutRef.current = setTimeout(connect, delay);
      };

      if (onEvent) {
        es.onmessage = onEvent;
      }
    };

    connect();

    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
    };
  }, [enabled, queryClient, onEvent]);

  return { status, lastEvent };
}

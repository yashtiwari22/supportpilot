'use client';

import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { api, UserDto } from '@/lib/api';
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Shield, Sparkles, Loader2, KeyRound } from 'lucide-react';

interface LoginCardProps {
  onSuccess: (user: UserDto) => void;
}

export function LoginCard({ onSuccess }: LoginCardProps) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loginMutation = useMutation({
    mutationFn: () => api.auth.login(email, password),
    onSuccess: (data) => {
      setErrorMessage(null);
      onSuccess(data.user);
    },
    onError: (err: Error) => {
      setErrorMessage(err.message || 'Login failed. Please verify credentials.');
    },
  });

  const handleFillDemo = (demoEmail: string) => {
    setEmail(demoEmail);
    setPassword('password123');
    setErrorMessage(null);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !password) {
      setErrorMessage('Please enter both email and password.');
      return;
    }
    loginMutation.mutate();
  };

  return (
    <div className="flex min-h-[80vh] items-center justify-center p-4">
      <Card className="w-full max-w-md shadow-2xl border-border/60">
        <CardHeader className="space-y-1.5 text-center">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10 text-primary mb-2">
            <Shield className="h-6 w-6" />
          </div>
          <CardTitle className="text-2xl font-bold tracking-tight">SupportPilot</CardTitle>
          <CardDescription className="text-sm">
            Sign in to access your tenant&apos;s live AI support console
          </CardDescription>
        </CardHeader>

        <form onSubmit={handleSubmit}>
          <CardContent className="space-y-4 pt-2">
            {errorMessage && (
              <div className="rounded-md border border-destructive/30 bg-destructive/10 p-3 text-xs text-destructive">
                {errorMessage}
              </div>
            )}

            <div className="space-y-2">
              <label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                Email Address
              </label>
              <Input
                type="email"
                placeholder="agent@company.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                disabled={loginMutation.isPending}
                required
              />
            </div>

            <div className="space-y-2">
              <label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                Password
              </label>
              <Input
                type="password"
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={loginMutation.isPending}
                required
              />
            </div>

            {/* Demo Quick-Fill Section */}
            <div className="rounded-lg border bg-muted/40 p-3.5 space-y-2 mt-4">
              <div className="flex items-center gap-1.5 text-xs font-semibold text-foreground">
                <Sparkles className="w-3.5 h-3.5 text-amber-400" />
                <span>Demo Staff Credentials (Click to Autofill)</span>
              </div>
              <div className="grid grid-cols-1 gap-1.5 pt-1">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="justify-between text-xs h-8 font-normal"
                  onClick={() => handleFillDemo('admin@acme.com')}
                >
                  <span className="font-medium text-foreground">Acme Electronics Admin</span>
                  <span className="text-muted-foreground text-[11px]">admin@acme.com</span>
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="justify-between text-xs h-8 font-normal"
                  onClick={() => handleFillDemo('agent@acme.com')}
                >
                  <span className="font-medium text-foreground">Acme Support Agent</span>
                  <span className="text-muted-foreground text-[11px]">agent@acme.com</span>
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="justify-between text-xs h-8 font-normal"
                  onClick={() => handleFillDemo('admin@saasflow.com')}
                >
                  <span className="font-medium text-foreground">SaaSFlow Cloud Admin</span>
                  <span className="text-muted-foreground text-[11px]">admin@saasflow.com</span>
                </Button>
              </div>
            </div>
          </CardContent>

          <CardFooter className="pt-2">
            <Button type="submit" className="w-full gap-2" disabled={loginMutation.isPending}>
              {loginMutation.isPending ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  <span>Authenticating...</span>
                </>
              ) : (
                <>
                  <KeyRound className="w-4 h-4" />
                  <span>Sign In</span>
                </>
              )}
            </Button>
          </CardFooter>
        </form>
      </Card>
    </div>
  );
}

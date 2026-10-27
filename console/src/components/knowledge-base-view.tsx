'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, KbArticleSummary } from '@/lib/api';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Textarea } from '@/components/ui/textarea';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import {
  BookOpen,
  Plus,
  Trash2,
  Layers,
  Clock,
  Sparkles,
  FileText,
  Search,
  CheckCircle2,
  RefreshCw,
} from 'lucide-react';

export function KnowledgeBaseView() {
  const queryClient = useQueryClient();
  const [createOpen, setCreateOpen] = useState(false);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [searchQuery, setSearchQuery] = useState('');

  const {
    data: articles = [],
    isLoading,
    refetch,
    isRefetching,
  } = useQuery<KbArticleSummary[]>({
    queryKey: ['kb-articles'],
    queryFn: () => api.kb.listArticles(),
  });

  const createMutation = useMutation({
    mutationFn: (req: { title: string; content: string }) => api.kb.createArticle(req),
    onSuccess: () => {
      setCreateOpen(false);
      setTitle('');
      setContent('');
      queryClient.invalidateQueries({ queryKey: ['kb-articles'] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.kb.deleteArticle(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['kb-articles'] });
    },
  });

  const filteredArticles = articles.filter((a) =>
    a.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
    a.slug.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold tracking-tight flex items-center gap-2">
            <BookOpen className="w-5 h-5 text-primary" />
            Knowledge Base & pgvector Index
          </h2>
          <p className="text-sm text-muted-foreground mt-0.5">
            Manage company policies and guides. Uploaded markdown is automatically chunked and embedded for RAG hybrid search.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => refetch()}
            disabled={isRefetching}
            className="gap-1.5 h-8 text-xs"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isRefetching ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
          <Button
            size="sm"
            onClick={() => setCreateOpen(true)}
            className="gap-1.5 h-8 text-xs bg-primary hover:bg-primary/90"
          >
            <Plus className="w-3.5 h-3.5" />
            New Policy Article
          </Button>
        </div>
      </div>

      {/* Search and Summary Bar */}
      <div className="flex flex-col sm:flex-row items-center justify-between gap-3">
        <div className="relative w-full sm:w-72">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search articles..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="pl-8 h-9 text-xs"
          />
        </div>

        <div className="text-xs text-muted-foreground flex items-center gap-3 self-start sm:self-center">
          <span>{articles.length} total articles</span>
          <span>&bull;</span>
          <span>{articles.reduce((acc, a) => acc + (a.chunkCount || 0), 0)} vector chunks indexed</span>
        </div>
      </div>

      {isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-36 rounded-lg border bg-card/40 animate-pulse" />
          ))}
        </div>
      ) : filteredArticles.length === 0 ? (
        <Card className="border-dashed bg-card/40">
          <CardContent className="flex flex-col items-center justify-center py-12 text-center">
            <div className="w-12 h-12 rounded-full bg-primary/10 flex items-center justify-center mb-3">
              <FileText className="w-6 h-6 text-primary" />
            </div>
            <h3 className="font-semibold text-lg">No Knowledge Base Articles</h3>
            <p className="text-sm text-muted-foreground max-w-md mt-1 mb-4">
              {searchQuery
                ? `No articles found matching "${searchQuery}".`
                : 'Upload documentation, return policies, or FAQs to power autonomous resolution agents.'}
            </p>
            {!searchQuery && (
              <Button size="sm" onClick={() => setCreateOpen(true)} className="gap-1.5">
                <Plus className="w-3.5 h-3.5" />
                Add First Article
              </Button>
            )}
          </CardContent>
        </Card>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredArticles.map((article) => (
            <Card key={article.id} className="bg-card/60 hover:border-primary/40 transition-colors flex flex-col justify-between">
              <CardHeader className="pb-3">
                <div className="flex items-start justify-between gap-2">
                  <div className="space-y-1">
                    <CardTitle className="text-sm font-semibold line-clamp-1">
                      {article.title}
                    </CardTitle>
                    <CardDescription className="text-[11px] font-mono text-muted-foreground">
                      /{article.slug}
                    </CardDescription>
                  </div>

                  <Badge variant="secondary" className="gap-1 font-mono text-[10px] shrink-0">
                    <Layers className="w-3 h-3 text-primary" />
                    {article.chunkCount} chunks
                  </Badge>
                </div>
              </CardHeader>

              <CardContent className="pt-0">
                <div className="flex items-center justify-between text-xs text-muted-foreground border-t pt-3">
                  <span className="flex items-center gap-1 text-[11px]">
                    <Clock className="w-3 h-3" />
                    {new Date(article.createdAt).toLocaleDateString()}
                  </span>

                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-7 px-2 text-destructive hover:bg-destructive/10 hover:text-destructive"
                    onClick={() => {
                      if (confirm(`Delete "${article.title}" and remove its vector embeddings?`)) {
                        deleteMutation.mutate(article.id);
                      }
                    }}
                    disabled={deleteMutation.isPending}
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {/* New Article Dialog */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-lg">
              <Sparkles className="w-4 h-4 text-primary" />
              Add Knowledge Base Article
            </DialogTitle>
            <DialogDescription className="text-xs">
              Content is instantly chunked and encoded into 768-dim embeddings in pgvector for semantic retrieval.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-2">
            <div>
              <label className="text-xs font-semibold text-muted-foreground block mb-1.5">
                Article Title
              </label>
              <Input
                placeholder="e.g. 30-Day Money-Back Guarantee & Return Instructions"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                className="text-sm"
              />
            </div>

            <div>
              <label className="text-xs font-semibold text-muted-foreground block mb-1.5">
                Article Body (Markdown supported)
              </label>
              <Textarea
                rows={10}
                placeholder="Write or paste your policy documentation here in markdown..."
                value={content}
                onChange={(e) => setContent(e.target.value)}
                className="font-mono text-xs leading-relaxed"
              />
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" size="sm" onClick={() => setCreateOpen(false)}>
              Cancel
            </Button>
            <Button
              size="sm"
              className="gap-1.5"
              onClick={() => {
                if (title.trim() && content.trim()) {
                  createMutation.mutate({ title, content });
                }
              }}
              disabled={createMutation.isPending || !title.trim() || !content.trim()}
            >
              <CheckCircle2 className="w-4 h-4" />
              Index Article
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

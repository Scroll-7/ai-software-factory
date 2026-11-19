import { Component, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

interface PullRequestInfo {
  number: number;
  url: string;
  state: string;
}

interface ExecutionState {
  status: string;
  pullRequest?: PullRequestInfo;
  error?: string;
}

interface PipelineEvent {
  stage: string;
  status: string;
  message: string;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnDestroy {
  readonly apiBase = 'http://127.0.0.1:8081';

  token = '';
  connected = false;
  loginMessage = '';

  title = 'Support bilingue FR/EN';
  businessText = "Ajoute le support bilingue francais/anglais a Fake-exposer. Exigences : 1. Les textes de l'interface viennent de public/i18n/fr.json et public/i18n/en.json. 2. La langue par defaut est le francais ; un bouton permet de basculer en anglais. 3. Le choix de langue est memorise dans localStorage. 4. Aucun appel reseau externe, aucune cle API, aucune donnee reelle. 5. Ajoute un test node:test qui verifie que les deux fichiers ont exactement les memes cles. Reponds en francais. Code avec des identifiants anglais.";
  targetApp = 'fake-exposer';
  targetRepo = 'Scroll-7/Fake-exposer';
  locale = 'both';
  requesterId = '11111111-1111-1111-1111-111111111111';

  runId = '';
  execution: ExecutionState | null = null;
  events: PipelineEvent[] = [];
  busy = false;
  errorMessage = '';

  decision = 'APPROVE';
  approvalComment = '';
  approvalMessage = '';

  private timer: number | undefined;

  async login(): Promise<void> {
    this.loginMessage = '';
    try {
      if (!this.token.trim()) {
        const data = await this.api('/api/v1/auth/dev-token', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ subject: 'requester' })
        });
        this.token = String(data['token'] ?? '');
      }
      this.connected = this.token.trim().length > 0;
      this.loginMessage = this.connected
        ? 'Connecté en local. Aucun secret externe utilisé.'
        : 'Impossible de obtenir un jeton local.';
    } catch (error) {
      this.loginMessage = this.describe(error);
    }
  }

  async submit(): Promise<void> {
    if (!this.connected) {
      this.errorMessage = 'Connecte-toi d’abord.';
      return;
    }
    this.busy = true;
    this.errorMessage = '';
    this.execution = null;
    this.events = [];
    this.runId = '';
    try {
      const created = await this.api('/api/v1/change-requests', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: this.title,
          businessText: this.businessText,
          targetApp: this.targetApp,
          targetRepo: this.targetRepo,
          locale: this.locale,
          requesterId: this.requesterId
        })
      });
      this.runId = String(created['runId'] ?? '');
      await this.api(`/api/v1/runs/${this.runId}/execute`, { method: 'POST' });
      this.startPolling();
    } catch (error) {
      this.errorMessage = this.describe(error);
    } finally {
      this.busy = false;
    }
  }

  async approve(): Promise<void> {
    if (!this.runId) return;
    this.approvalMessage = '';
    try {
      const data = await this.api(`/api/v1/runs/${this.runId}/approve`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          decision: this.decision,
          comment: this.approvalComment,
          approverId: this.requesterId
        })
      });
      this.approvalMessage = String(data['nextStep'] ?? 'Décision enregistrée.');
    } catch (error) {
      this.approvalMessage = this.describe(error);
    }
  }

  ngOnDestroy(): void {
    this.stopPolling();
  }

  private startPolling(): void {
    this.stopPolling();
    void this.poll();
    this.timer = window.setInterval(() => void this.poll(), 5000);
  }

  private stopPolling(): void {
    if (this.timer !== undefined) {
      window.clearInterval(this.timer);
      this.timer = undefined;
    }
  }

  private async poll(): Promise<void> {
    if (!this.runId) return;
    try {
      this.execution = await this.api<ExecutionState>(`/api/v1/runs/${this.runId}/execution`);
      this.events = await this.api<PipelineEvent[]>(`/api/v1/runs/${this.runId}/events`);
      if (this.execution.status !== 'RUNNING') this.stopPolling();
    } catch (error) {
      this.errorMessage = this.describe(error);
      this.stopPolling();
    }
  }

  private async api<T = Record<string, unknown>>(path: string, options: RequestInit = {}): Promise<T> {
    const headers: Record<string, string> = { ...(options.headers as Record<string, string> | undefined) };
    if (this.token) headers['Authorization'] = `Bearer ${this.token}`;
    const response = await fetch(this.apiBase + path, { ...options, headers });
    const text = await response.text();
    let data: unknown = {};
    try {
      data = text ? JSON.parse(text) : {};
    } catch {
      data = { message: text };
    }
    if (!response.ok) {
      const obj = data as Record<string, unknown>;
      const detail = obj['error'] ?? obj['message'] ?? `HTTP ${response.status}`;
      throw new Error(String(detail));
    }
    return data as T;
  }

  private describe(error: unknown): string {
    return error instanceof Error ? error.message : String(error);
  }
}

import { Component } from 'react';
import { reportClientError } from '@/lib/reportClientError';

export class ErrorBoundary extends Component {
  state = { error: null };

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, info) {
    reportClientError(error, { componentStack: info.componentStack, scope: this.props.scope });
  }

  componentDidUpdate(previous) {
    if (this.state.error && previous.resetKey !== this.props.resetKey) this.setState({ error: null });
  }

  render() {
    if (!this.state.error) return this.props.children;
    return this.props.fallback({ error: this.state.error, reset: () => this.setState({ error: null }) });
  }
}

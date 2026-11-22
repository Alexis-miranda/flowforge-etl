export type RunStatus = 'Succeeded' | 'Running' | 'Failed';

export type ExecutionFilter = 'All runs' | RunStatus;

export type Run = {
  id: string;
  pipeline: string;
  source: string;
  started: string;
  duration: string;
  status: RunStatus;
  rows: string;
};

import type { RunStatus } from '../features/executions/types';

type StatusProps = {
  status: RunStatus;
};

export function Status({ status }: StatusProps) {
  return (
    <span className={'status ' + status.toLowerCase()}>
      <i />
      {status}
    </span>
  );
}


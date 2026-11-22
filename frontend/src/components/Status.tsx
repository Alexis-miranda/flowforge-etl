type RunStatus = 'Succeeded' | 'Running' | 'Failed';

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

export type { RunStatus };

import type { RunStatus } from '../features/executions/types';
import { useApp } from '../app/AppProvider';

type StatusProps = { status: RunStatus };

export function Status({ status }: StatusProps) {
  const { t } = useApp();
  const label = status === 'Succeeded' ? t('succeeded') : status === 'Running' ? t('running') : t('statusFailed');
  return <span className={'status ' + status.toLowerCase()}><i />{label}</span>;
}
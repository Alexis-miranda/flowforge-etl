import { AlertCircle, CheckCircle2, Clock3, X } from 'lucide-react';
import { Status } from '../../components/Status';
import type { Run } from './types';
import { useApp } from '../../app/AppProvider';

type ExecutionDetailsProps = { run: Run; onClose: () => void };

export function ExecutionDetails({ run, onClose }: ExecutionDetailsProps) {
  const { t } = useApp();
  return <aside className="detail-drawer" aria-label={t('closeDetails')}>
    <button className="close-detail" onClick={onClose} aria-label={t('close')}><X size={16}/></button>
    <div className="eyebrow">{t('executionDetail')}</div>
    <h2>{run.id}</h2>
    <Status status={run.status}/>
    <div className="detail-source"><small>{t('pipeline')}</small><b>{run.pipeline}</b></div>
    <div className="detail-source"><small>{t('inputFile')}</small><b>{run.source}</b></div>
    <div className="detail-source"><small>{t('started')}</small><b>{run.started}</b></div>
    <div className="detail-title">Estado de ejecución</div>
    <div className="detail-info">
      {run.status === 'Succeeded' ? <CheckCircle2 size={16}/> : run.status === 'Failed' ? <AlertCircle size={16}/> : <Clock3 size={16}/>}
      {run.errorMessage || (run.status === 'Running' ? 'La ejecución está pendiente o en proceso.' : run.status === 'Succeeded' ? 'El pipeline ha terminado correctamente.' : 'La ejecución ha fallado.')}
    </div>
  </aside>;
}

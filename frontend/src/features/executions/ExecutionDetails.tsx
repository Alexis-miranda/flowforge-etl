import { ShieldCheck, X } from 'lucide-react';
import { Status } from '../../components/Status';
import type { Run } from './types';
import { useApp } from '../../app/AppProvider';

type ExecutionDetailsProps = { run: Run; onClose: () => void };

export function ExecutionDetails({ run, onClose }: ExecutionDetailsProps) {
  const { t } = useApp();
  const steps = [
    { name: t('extract'), detail: t('extractDetail'), time: '00:12' },
    { name: t('validate'), detail: t('validateDetail'), time: '00:18' },
    { name: t('prepare'), detail: t('prepareDetail'), time: '00:36' },
    { name: t('persist'), detail: t('persistDetail'), time: '00:42' },
    { name: t('publish'), detail: t('publishDetail'), time: '00:16' },
  ];
  return <aside className="detail-drawer" aria-label={t('closeDetails')}>
    <button className="close-detail" onClick={onClose} aria-label={t('close')}><X size={16}/></button>
    <div className="eyebrow">{t('executionDetail')}</div><h2>{run.id}</h2><Status status={run.status}/>
    <div className="detail-source"><small>{t('inputFile')}</small><b>{run.source}</b></div>
    <div className="detail-title">{t('pipelineSteps')}</div>
    {steps.map((step, index) => <div className="detail-step" key={step.name}>
      <div className={'step-check ' + (run.status === 'Failed' && index === 2 ? 'fail' : '')}>{run.status === 'Failed' && index === 2 ? '!' : '✓'}</div>
      <div><b>{step.name}</b><small>{step.detail}</small></div><span>{run.status === 'Running' && index === 4 ? '…' : step.time}</span>
    </div>)}
    <div className="detail-info"><ShieldCheck size={15}/>{t('sampleDetails')}</div>
  </aside>;
}
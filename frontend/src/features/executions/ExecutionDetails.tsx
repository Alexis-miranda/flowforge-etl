import { ShieldCheck, X } from 'lucide-react';
import { Status } from '../../components/Status';
import type { Run } from './types';

type ExecutionDetailsProps = {
  run: Run;
  onClose: () => void;
};

const steps = [
  { name: 'Extract source file', detail: 'Read input and detect format', time: '00:12' },
  { name: 'Validate records', detail: 'Schema, required fields and amount rules', time: '00:18' },
  { name: 'Prepare cash data', detail: 'Normalize values and map fields', time: '00:36' },
  { name: 'Persist to database', detail: 'SQL Server transactional write', time: '00:42' },
  { name: 'Publish output', detail: 'Write output artifact', time: '00:16' },
];

export function ExecutionDetails({ run, onClose }: ExecutionDetailsProps) {
  return (
    <aside className="detail-drawer">
      <button className="close-detail" onClick={onClose} aria-label="Close execution details">
        <X size={16} />
      </button>
      <div className="eyebrow">EXECUTION DETAIL</div>
      <h2>{run.id}</h2>
      <Status status={run.status} />
      <div className="detail-source">
        <small>INPUT FILE</small>
        <b>{run.source}</b>
      </div>
      <div className="detail-title">Pipeline steps</div>
      {steps.map((step, index) => (
        <div className="detail-step" key={step.name}>
          <div className={'step-check ' + (run.status === 'Failed' && index === 2 ? 'fail' : '')}>
            {run.status === 'Failed' && index === 2 ? '!' : '✓'}
          </div>
          <div><b>{step.name}</b><small>{step.detail}</small></div>
          <span>{run.status === 'Running' && index === 4 ? '…' : step.time}</span>
        </div>
      ))}
      <div className="detail-info">
        <ShieldCheck size={15} />
        Execution details are sample data until connected to the REST API.
      </div>
    </aside>
  );
}

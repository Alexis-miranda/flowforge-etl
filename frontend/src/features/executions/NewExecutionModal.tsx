import { useState } from 'react';
import { Play, Workflow, X } from 'lucide-react';
import { useApp } from '../../app/AppProvider';

export type NewExecutionInput = { source: string; backOffice: string; outputDirectory: string };
type NewExecutionModalProps = { open: boolean; notice: string; onClose: () => void; onCreate: (input: NewExecutionInput) => void };

export function NewExecutionModal({ open, notice, onClose, onCreate }: NewExecutionModalProps) {
  const { t } = useApp();
  const [source, setSource] = useState('');
  const [backOffice, setBackOffice] = useState('BNYM');
  const [outputDirectory, setOutputDirectory] = useState('/data/output');
  const [validationMessage, setValidationMessage] = useState('');
  if (!open) return null;
  const handleSubmit = () => {
    if (!source.trim()) { setValidationMessage(t('chooseInput')); return; }
    setValidationMessage('');
    onCreate({ source, backOffice, outputDirectory });
    setSource('');
  };
  return <div className="modal-backdrop" onClick={onClose}>
    <div className="modal" role="dialog" aria-modal="true" aria-labelledby="new-execution-title" onClick={event => event.stopPropagation()}>
      <div className="modal-head"><div><h2 id="new-execution-title">{t('newRunTitle')}</h2><p>{t('configureRun')}</p></div>
        <button className="icon-btn" onClick={onClose} aria-label={t('close')}><X size={17}/></button></div>
      <div className="field-label">{t('pipeline')}</div><div className="pipeline-select"><Workflow size={16}/>{t('cashReconciliation')}<span>{t('active')}</span></div>
      <label htmlFor="execution-source">{t('inputPath')}</label><input id="execution-source" className="form-input" value={source} onChange={e => setSource(e.target.value)} placeholder="/shared/input/cash-file.csv"/>
      <label htmlFor="execution-back-office">{t('backOffice')}</label><input id="execution-back-office" className="form-input" value={backOffice} onChange={e => setBackOffice(e.target.value)} placeholder="BNYM"/>
      <label htmlFor="execution-output-directory">{t('outputDirectory')}</label><input id="execution-output-directory" className="form-input" value={outputDirectory} onChange={e => setOutputDirectory(e.target.value)}/>
      <p className="modal-note">{t('demoOnly')}</p>
      {validationMessage && <div className="notice" role="alert">{validationMessage}</div>}
      {notice && <div className="notice">{notice}</div>}
      <div className="modal-actions"><button className="secondary" onClick={onClose}>{t('cancel')}</button><button className="primary" onClick={handleSubmit}><Play size={15}/>{t('startDemo')}</button></div>
    </div>
  </div>;
}
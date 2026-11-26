import { useState } from 'react';
import { FileUp, Workflow, X } from 'lucide-react';
import { useApp } from '../../app/AppProvider';

export type NewExecutionInput = { file: File };
type NewExecutionModalProps = { open: boolean; notice: string; onClose: () => void; onCreate: (input: NewExecutionInput) => Promise<void> };

export function NewExecutionModal({ open, notice, onClose, onCreate }: NewExecutionModalProps) {
  const { t } = useApp();
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [validationMessage, setValidationMessage] = useState('');
  if (!open) return null;
  const handleSubmit = async () => {
    if (!file) { setValidationMessage('Selecciona un archivo CSV.'); return; }
    if (!file.name.toLowerCase().endsWith('.csv')) { setValidationMessage('Solo se admiten archivos .csv.'); return; }
    if (file.size === 0) { setValidationMessage('El archivo está vacío.'); return; }
    if (file.size > 25 * 1024 * 1024) { setValidationMessage('El tamaño máximo es 25 MiB.'); return; }
    setValidationMessage('');
    setBusy(true);
    try { await onCreate({ file }); setFile(null); }
    finally { setBusy(false); }
  };
  return <div className="modal-backdrop" onClick={busy ? undefined : onClose}>
    <div className="modal" role="dialog" aria-modal="true" aria-labelledby="new-execution-title" onClick={event => event.stopPropagation()}>
      <div className="modal-head"><div><h2 id="new-execution-title">Nueva ejecución Cash</h2><p>Sube un CSV para enviarlo al pipeline real.</p></div>
        <button className="icon-btn" onClick={onClose} aria-label={t('close')} disabled={busy}><X size={17}/></button></div>
      <div className="field-label">{t('pipeline')}</div><div className="pipeline-select"><Workflow size={16}/>{t('cashReconciliation')}<span>API</span></div>
      <label htmlFor="execution-file">Archivo CSV</label>
      <input id="execution-file" className="form-input" type="file" accept=".csv,text/csv" onChange={e => setFile(e.target.files?.[0] ?? null)} disabled={busy}/>
      {file && <p className="modal-note">{file.name} · {(file.size / 1024).toFixed(1)} KB</p>}
      <p className="modal-note">Máximo 25 MiB. El archivo se guarda en el servidor y se encola para el worker Cash.</p>
      {validationMessage && <div className="notice" role="alert">{validationMessage}</div>}
      {notice && <div className="notice">{notice}</div>}
      <div className="modal-actions"><button className="secondary" onClick={onClose} disabled={busy}>{t('cancel')}</button><button className="primary" onClick={handleSubmit} disabled={busy}><FileUp size={15}/>{busy ? 'Enviando…' : 'Subir y ejecutar'}</button></div>
    </div>
  </div>;
}

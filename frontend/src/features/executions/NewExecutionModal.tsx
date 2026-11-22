import { useState } from 'react';
import { Play, Workflow, X } from 'lucide-react';

export type NewExecutionInput = {
  source: string;
  backOffice: string;
  outputDirectory: string;
};

type NewExecutionModalProps = {
  open: boolean;
  notice: string;
  onClose: () => void;
  onCreate: (input: NewExecutionInput) => void;
};

export function NewExecutionModal({
  open,
  notice,
  onClose,
  onCreate,
}: NewExecutionModalProps) {
  const [source, setSource] = useState('');
  const [backOffice, setBackOffice] = useState('BNYM');
  const [outputDirectory, setOutputDirectory] = useState('/data/output');
  const [validationMessage, setValidationMessage] = useState('');

  if (!open) return null;

  const handleSubmit = () => {
    if (!source.trim()) {
      setValidationMessage('Choose an input file path to continue.');
      return;
    }

    setValidationMessage('');
    onCreate({ source, backOffice, outputDirectory });
    setSource('');
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" role="dialog" aria-modal="true" aria-labelledby="new-execution-title" onClick={(event) => event.stopPropagation()}>
        <div className="modal-head">
          <div>
            <h2 id="new-execution-title">New pipeline run</h2>
            <p>Configure and start a Cash reconciliation run.</p>
          </div>
          <button className="icon-btn" onClick={onClose} aria-label="Close new pipeline run">
            <X size={17} />
          </button>
        </div>
        <div className="field-label">Pipeline</div>
        <div className="pipeline-select"><Workflow size={16} /> Cash reconciliation <span>Active</span></div>
        <label htmlFor="execution-source">Input file path</label>
        <input
          id="execution-source"
          className="form-input"
          value={source}
          onChange={(event) => setSource(event.target.value)}
          placeholder="/shared/input/cash-file.csv"
        />
        <label htmlFor="execution-back-office">Back office code</label>
        <input
          id="execution-back-office"
          className="form-input"
          value={backOffice}
          onChange={(event) => setBackOffice(event.target.value)}
          placeholder="BNYM"
        />
        <label htmlFor="execution-output-directory">Output directory</label>
        <input
          id="execution-output-directory"
          className="form-input"
          value={outputDirectory}
          onChange={(event) => setOutputDirectory(event.target.value)}
        />
        <p className="modal-note">Demo only — this form creates a local sample execution. It does not submit to the backend yet.</p>
        {validationMessage && <div className="notice" role="alert">{validationMessage}</div>}
        {notice && <div className="notice">{notice}</div>}
        <div className="modal-actions">
          <button className="secondary" onClick={onClose}>Cancel</button>
          <button className="primary" onClick={handleSubmit}><Play size={15} /> Start demo run</button>
        </div>
      </div>
    </div>
  );
}

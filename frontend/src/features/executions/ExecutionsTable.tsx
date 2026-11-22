import { FileClock, Filter, Search } from 'lucide-react';
import type { RunStatus } from '../../components/Status';

export type Run = {
  id: string;
  pipeline: string;
  source: string;
  started: string;
  duration: string;
  status: RunStatus;
  rows: string;
};

type ExecutionsTableProps = {
  runs: Run[];
  filter: string;
  query: string;
  selectedRunId: string | null;
  onFilterChange: (filter: string) => void;
  onQueryChange: (query: string) => void;
  onSelectRun: (run: Run) => void;
};

export function ExecutionsTable({
  runs,
  filter,
  query,
  selectedRunId,
  onFilterChange,
  onQueryChange,
  onSelectRun,
}: ExecutionsTableProps) {
  return (
    <section className="panel runs-panel">
      <div className="panel-head">
        <div><h2>Recent executions</h2><p>Monitor your latest pipeline activity</p></div>
        <button className="text-button">View all runs <span aria-hidden="true">↗</span></button>
      </div>
      <div className="run-toolbar">
        <div className="tabs">
          {['All runs', 'Succeeded', 'Running', 'Failed'].map((tab) => (
            <button key={tab} className={filter === tab ? 'chosen' : ''} onClick={() => onFilterChange(tab)}>
              {tab}
            </button>
          ))}
        </div>
        <div className="search">
          <Search size={15} />
          <input value={query} onChange={(event) => onQueryChange(event.target.value)} placeholder="Search executions..." />
        </div>
        <button className="filter-btn"><Filter size={15} /> Filters</button>
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>EXECUTION</th><th>PIPELINE</th><th>INPUT SOURCE</th><th>STARTED</th>
              <th>DURATION</th><th>RECORDS</th><th>STATUS</th>
            </tr>
          </thead>
          <tbody>
            {runs.map((run) => (
              <tr
                key={run.id}
                onClick={() => onSelectRun(run)}
                className={selectedRunId === run.id ? 'selected-row' : ''}
              >
                <td><b className="run-id">{run.id}</b></td>
                <td>{run.pipeline}</td>
                <td><span className="file-cell"><FileClock size={14} />{run.source}</span></td>
                <td>{run.started}</td>
                <td>{run.duration}</td>
                <td>{run.rows}</td>
                <td><span className={`status ${run.status.toLowerCase()}`}><i />{run.status}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
        {runs.length === 0 && <div className="empty">No executions match this filter.</div>}
      </div>
    </section>
  );
}

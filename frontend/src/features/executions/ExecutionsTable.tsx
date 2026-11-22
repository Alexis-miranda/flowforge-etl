import { ArrowUpRight, FileClock, Filter, Search } from 'lucide-react';
import { Status } from '../../components/Status';
import type { ExecutionFilter, Run } from './types';
import { useApp } from '../../app/AppProvider';

type ExecutionsTableProps = {
  runs: Run[]; filter: ExecutionFilter; query: string; selectedRunId: string | null;
  onFilterChange: (filter: ExecutionFilter) => void; onQueryChange: (query: string) => void;
  onSelectRun: (run: Run) => void;
};

export function ExecutionsTable({ runs, filter, query, selectedRunId, onFilterChange, onQueryChange, onSelectRun }: ExecutionsTableProps) {
  const { t } = useApp();
  const tabs: { value: ExecutionFilter; label: string }[] = [
    { value: 'All runs', label: t('allRuns') }, { value: 'Succeeded', label: t('succeeded') },
    { value: 'Running', label: t('running') }, { value: 'Failed', label: t('statusFailed') },
  ];
  return <section className="panel runs-panel">
    <div className="panel-head"><div><h2>{t('recentExecutions')}</h2><p>{t('monitorActivity')}</p></div><button className="text-button">{t('viewAllRuns')} <ArrowUpRight size={15}/></button></div>
    <div className="run-toolbar">
      <div className="tabs">{tabs.map(tab => <button key={tab.value} className={filter === tab.value ? 'chosen' : ''} aria-pressed={filter === tab.value} onClick={() => onFilterChange(tab.value)}>{tab.label}</button>)}</div>
      <div className="search"><Search size={15}/><input aria-label={t('searchExecutions')} value={query} onChange={e => onQueryChange(e.target.value)} placeholder={t('searchExecutions')}/></div>
      <button className="filter-btn"><Filter size={15}/>{t('filters')}</button>
    </div>
    <div className="table-wrap"><table><thead><tr>
      <th>{t('execution')}</th><th>{t('pipeline')}</th><th>{t('inputSource')}</th><th>{t('started')}</th>
      <th>{t('duration')}</th><th>{t('records')}</th><th>{t('status')}</th>
    </tr></thead><tbody>{runs.map(run => <tr key={run.id} onClick={() => onSelectRun(run)} className={selectedRunId === run.id ? 'selected-row' : ''}>
      <td><b className="run-id">{run.id}</b></td><td>{run.pipeline}</td><td><span className="file-cell"><FileClock size={14}/>{run.source}</span></td>
      <td>{run.started}</td><td>{run.duration}</td><td>{run.rows}</td><td><Status status={run.status}/></td>
    </tr>)}</tbody></table>{runs.length === 0 && <div className="empty">{t('noExecutions')}</div>}</div>
  </section>;
}
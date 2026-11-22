import type { ReactNode } from 'react';
import { ArrowDownRight, ArrowUpRight } from 'lucide-react';

type MetricProps = {
  label: string;
  value: string;
  change: string;
  up: boolean;
  icon: ReactNode;
  caption: string;
};

export function Metric({ label, value, change, up, icon, caption }: MetricProps) {
  return (
    <div className="metric">
      <div className="metric-top">
        <span>{label}</span>
        <div className="metric-icon">{icon}</div>
      </div>
      <div className="metric-value">{value}</div>
      <div className="metric-bottom">
        <span className={'change ' + (up ? 'up' : 'down')}>
          {up ? <ArrowUpRight size={13} /> : <ArrowDownRight size={13} />} {change}
        </span>
        <span>{caption}</span>
      </div>
    </div>
  );
}

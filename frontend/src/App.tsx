import Dashboard from './pages/Dashboard';
import { AppProvider } from './app/AppProvider';

export default function App() {
  return <AppProvider><Dashboard /></AppProvider>;
}

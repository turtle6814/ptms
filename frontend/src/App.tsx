import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { TournamentSetup } from './pages/TournamentSetup';
import { AdminDashboard } from './pages/AdminDashboard';
import { ViewerPage } from './pages/ViewerPage';
import './index.css';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Default redirect to admin dashboard */}
        <Route path="/" element={<Navigate to="/admin" replace />} />

        {/* Admin routes */}
        <Route path="/admin" element={<AdminDashboard />} />
        <Route path="/setup" element={<TournamentSetup />} />

        {/* Public viewer route - no auth required */}
        <Route path="/view/:tournamentId" element={<ViewerPage />} />

        {/* Fallback */}
        <Route path="*" element={<Navigate to="/admin" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;

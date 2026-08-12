import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ProtectedRoute } from './components/ProtectedRoute';
import { LandingPage } from './pages/LandingPage';
import { EventSetup } from './pages/EventSetup';
import { AdminDashboard } from './pages/AdminDashboard';
import { TournamentViewerPage } from './pages/TournamentViewerPage';
import { LoginPage } from './pages/LoginPage';
import { SignupPage } from './pages/SignupPage';
import { TournamentsPage } from './pages/TournamentsPage';
import { TournamentDetailPage } from './pages/TournamentDetailPage';
import './index.css';

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* Public landing page - redirects to admin if logged in */}
          <Route path="/" element={<LandingPage />} />

          {/* Auth routes */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/signup" element={<SignupPage />} />

          {/* Protected admin routes */}
          <Route path="/admin" element={
            <ProtectedRoute>
              <AdminDashboard />
            </ProtectedRoute>
          } />
          <Route path="/setup" element={
            <ProtectedRoute>
              <EventSetup />
            </ProtectedRoute>
          } />

          {/* Tournaments routes */}
          <Route path="/tournaments" element={
            <ProtectedRoute>
              <TournamentsPage />
            </ProtectedRoute>
          } />
          <Route path="/tournaments/:tournamentId" element={
            <ProtectedRoute>
              <TournamentDetailPage />
            </ProtectedRoute>
          } />

          {/* Public tournament viewer route - no auth required */}
          <Route path="/view/tournament/:tournamentId" element={<TournamentViewerPage />} />

          {/* Fallback to landing */}
          <Route path="*" element={<LandingPage />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;

import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ProtectedRoute } from './components/ProtectedRoute';
import { LandingPage } from './pages/LandingPage';
import { EventSetup } from './pages/EventSetup';
import { AdminDashboard } from './pages/AdminDashboard';
import { RefereeDashboard } from './pages/RefereeDashboard';
import { UserManagementPage } from './pages/UserManagementPage';
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

          {/* Organizer/admin routes */}
          <Route path="/admin" element={
            <ProtectedRoute allowedRoles={['ORGANIZER', 'ADMIN']}>
              <AdminDashboard />
            </ProtectedRoute>
          } />
          <Route path="/setup" element={
            <ProtectedRoute allowedRoles={['ORGANIZER', 'ADMIN']}>
              <EventSetup />
            </ProtectedRoute>
          } />

          {/* Tournaments routes */}
          <Route path="/tournaments" element={
            <ProtectedRoute allowedRoles={['ORGANIZER', 'ADMIN']}>
              <TournamentsPage />
            </ProtectedRoute>
          } />
          <Route path="/tournaments/:tournamentId" element={
            <ProtectedRoute allowedRoles={['ORGANIZER', 'ADMIN']}>
              <TournamentDetailPage />
            </ProtectedRoute>
          } />

          {/* Referee routes */}
          <Route path="/referee" element={
            <ProtectedRoute allowedRoles={['REFEREE', 'ADMIN']}>
              <RefereeDashboard />
            </ProtectedRoute>
          } />

          {/* Admin-only routes */}
          <Route path="/admin/users" element={
            <ProtectedRoute allowedRoles={['ADMIN']}>
              <UserManagementPage />
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

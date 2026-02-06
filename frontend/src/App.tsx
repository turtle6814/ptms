import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ProtectedRoute } from './components/ProtectedRoute';
import { LandingPage } from './pages/LandingPage';
import { TournamentSetup } from './pages/TournamentSetup';
import { AdminDashboard } from './pages/AdminDashboard';
import { ViewerPage } from './pages/ViewerPage';
import { EventViewerPage } from './pages/EventViewerPage';
import { LoginPage } from './pages/LoginPage';
import { SignupPage } from './pages/SignupPage';
import { EventsPage } from './pages/EventsPage';
import { EventDetailPage } from './pages/EventDetailPage';
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
              <TournamentSetup />
            </ProtectedRoute>
          } />

          {/* Events routes */}
          <Route path="/events" element={
            <ProtectedRoute>
              <EventsPage />
            </ProtectedRoute>
          } />
          <Route path="/events/:eventId" element={
            <ProtectedRoute>
              <EventDetailPage />
            </ProtectedRoute>
          } />

          {/* Public viewer routes - no auth required */}
          <Route path="/view/:tournamentId" element={<ViewerPage />} />
          <Route path="/view/event/:eventId" element={<EventViewerPage />} />

          {/* Fallback to landing */}
          <Route path="*" element={<LandingPage />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;


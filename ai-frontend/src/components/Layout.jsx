import React, { useMemo, useState } from 'react';
import '../assets/icons.css';
import { useAuth } from '../AuthContext';

const Layout = ({ initials, view, setView, children, appName }) => {
  const { user, loading, login, logout } = useAuth();

  // Memoize avatar URL to avoid unnecessary re-renders / re-fetches
  const avatarUrl = useMemo(() => user?.picture || null, [user]);

  // Track image load failure (React-safe alternative to DOM mutation)
  const [imgError, setImgError] = useState(false);

  const showAvatarImage = avatarUrl && !imgError;

  return (
    <div className="app-layout">
      <nav className="top-navbar">
        <div className="nav-content">
          
          <div className="nav-links">
            <div className="brand">
              <span className="app-name">{appName}</span>
            </div>

            {user && (
              <>
                <button
                  className={`btn btn-pill nav-item-btn ${view === 'chat' ? 'is-active' : ''}`}
                  onClick={() => setView('chat')}
                  title="Search Knowledge Base"
                >
                  <i className="lni lni-search"></i>
                  Search KB
                </button>

                <button
                  className={`btn btn-pill nav-item-btn ${view === 'admin' ? 'is-active' : ''}`}
                  onClick={() => setView('admin')}
                  title="Manage Knowledge Base"
                >
                  <i className="lni lni-indent-increase"></i>
                  Manage KB
                </button>
              </>
            )}
          </div>

          <div className="user-info">
            {loading ? (
              <div className="user-loader">...</div>
            ) : user ? (
              <>
                {showAvatarImage ? (
                  <img
                    src={avatarUrl}
                    alt="Avatar"
                    className="user-avatar-img"
                    referrerPolicy="no-referrer"
                    loading="lazy"
                    title={`Logged in as ${user.name}`}
                    onError={() => setImgError(true)}
                  />
                ) : (
                  <div
                    className="user-avatar"
                    title={`Logged in as ${user?.name || 'User'}`}
                  >
                    {initials}
                  </div>
                )}

                <button
                  className="btn btn-circle"
                  onClick={logout}
                  title="Logout"
                >
                  <i className="lni lni-exit"></i>
                </button>
              </>
            ) : (
              <button
                className="btn btn-pill"
                onClick={login}
                title="Sign in"
              >
                <i className="lni lni-google"></i>
                Sign in with Google
              </button>
            )}
          </div>
        </div>
      </nav>

      <main className={view === 'chat' ? 'main-wrapper-ask-ai' : 'main-wrapper'}>
        <div
          className="content-box"
          style={{ maxWidth: '1280px', margin: '0 auto' }}
        >
          {children}
        </div>
      </main>
    </div>
  );
};

// Prevent unnecessary re-renders
export default React.memo(Layout);
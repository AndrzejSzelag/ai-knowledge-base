import React, { useState } from 'react';
import Layout from './components/Layout';
import KnowledgeIngest from './components/KnowledgeIngest';
import KnowledgeQuery from './components/KnowledgeQuery.jsx';
import { AuthProvider, useAuth } from './AuthContext';
import { ToastProvider } from './hooks/useToast';
import './App.css';

const APP_NAME = "AI Knowledge Base";
const API_BASE_URL = "/api/ai";

const AppContent = () => {
    const [view, setView] = useState('chat');
    const { user, loading, logout, login } = useAuth();

    const initials = user ? `${user.givenName?.[0] ?? ''}${user.familyName?.[0] ?? ''}`.toUpperCase() : '?';

    if (loading) return <div className="loading-screen"><i className="lni lni-spinner lni-is-spinning"></i></div>;

    return (
        <Layout
            initials={initials}
            view={view}
            setView={setView}
            appName={APP_NAME}
        >
            {!user || view === 'chat' ? (
                <KnowledgeQuery apiUrl={API_BASE_URL} />
            ) : (
                <KnowledgeIngest apiUrl={API_BASE_URL} />
            )}
        </Layout>
    );
};

const App = () => (
    <AuthProvider>
        <ToastProvider>
            <AppContent />
        </ToastProvider>
    </AuthProvider>
);

export default App;
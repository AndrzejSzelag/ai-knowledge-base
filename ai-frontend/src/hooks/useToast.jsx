import { createContext, useContext, useState, useCallback } from 'react';

const ToastContext = createContext(null);

const icons = { success: '✓', error: '✕', loading: '⏳' };

const ToastList = ({ toasts, onRemove }) => (
    <div className="toast-container"> 
        {/* Usunęliśmy style inline, teraz steruje tym klasa .toast-container z CSS */}
        {toasts.map(t => (
            <div 
                key={t.id} 
                className={`toast toast-${t.type} ${t.hiding ? 'hide' : 'show'}`} 
                style={{ pointerEvents: 'auto' }}
            >
                <span className="toast-icon">{icons[t.type]}</span>
                <div className="toast-body">
                    <div className="toast-title">{t.title}</div>
                    {t.msg && <div className="toast-msg">{t.msg}</div>}
                </div>
                <button className="toast-close" onClick={() => onRemove(t.id)}>✕</button>
                <div
                    className="toast-progress"
                    style={{
                        animation: t.hiding ? 'none' : `toast-shrink ${t.duration}ms linear forwards`
                    }}
                />
            </div>
        ))}
    </div>
);

export const ToastProvider = ({ children }) => {
    const [toasts, setToasts] = useState([]);

    // Internal helper for removing/hiding toast
    const removeToast = useCallback((id) => {
        setToasts(prev => prev.map(t => t.id === id ? { ...t, hiding: true } : t));
        setTimeout(() => {
            setToasts(prev => prev.filter(t => t.id !== id));
        }, 300);
    }, []);

    const showToast = useCallback((type = 'loading', title = '', msg = '', duration = 3000) => {
        const id = Date.now();
        setToasts(prev => [...prev, { id, type, title, msg, duration, hiding: false }]);

        if (duration > 0) {
            setTimeout(() => removeToast(id), duration);
        }
        return id; // CRITICAL: Returns ID so we can update it later
    }, [removeToast]);

    const updateToast = useCallback((id, { type, title, msg, duration = 3000 }) => {
        setToasts(prev => prev.map(t =>
            t.id === id ? { ...t, type, title, msg, duration, hiding: false } : t
        ));

        if (duration > 0) {
            setTimeout(() => removeToast(id), duration);
        }
    }, [removeToast]);

    return (
        <ToastContext.Provider value={{ showToast, updateToast }}>
            {children}
            <ToastList toasts={toasts} onRemove={removeToast} />
        </ToastContext.Provider>
    );
};

export const useToast = () => {
    const ctx = useContext(ToastContext);
    if (!ctx) throw new Error('useToast must be used inside ToastProvider');
    // Now returns the object with both methods
    return ctx;
};
import React, { useState } from 'react';
import { useAuth } from '../AuthContext';
import ReactMarkdown from 'react-markdown';

const KnowledgeQuery = ({ apiUrl = "/api/ai" }) => {
    const { user, loading, login } = useAuth();
    const [question, setQuestion] = useState("");
    const [chatResponse, setChatResponse] = useState("");
    const [relatedDocs, setRelatedDocs] = useState([]);
    const [loadingAI, setLoadingAI] = useState(false);
    const [copied, setCopied] = useState(false);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState(false);
    const [isComplete, setIsComplete] = useState(false);

    // Send question to the AI endpoint and display the answer with references
    const handleAsk = async () => {
        const trimmedQuestion = question.trim();
        if (!trimmedQuestion) return;

        setLoadingAI(true);
        setIsLoading(true);
        setError(false);
        setIsComplete(false);
        setChatResponse("");
        setRelatedDocs([]);
        setCopied(false);

        try {
            const response = await fetch(`${apiUrl}/ask-streaming`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ question: trimmedQuestion }),
            });

            if (!response.ok) throw new Error(`Server error: ${response.status}`);

            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = "";

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, { stream: true });
                let lines = buffer.split("\n");
                buffer = lines.pop() || "";

                for (const line of lines) {
                    const trimmedLine = line.trim();
                    if (!trimmedLine.startsWith("data:")) continue;

                    const raw = line.substring(line.indexOf("data:") + 5);

                    // ── JSON.parse żeby odzyskać spacje ──
                    const parsed = (() => {
                        try {
                            return JSON.parse(raw);
                        } catch {
                            return raw;
                        }
                    })();
                    if (!parsed || parsed === "null") continue;

                    // 1. Obsługa dokumentów źródłowych (JSON)
                    if (Array.isArray(parsed)) {
                        setRelatedDocs(parsed);
                    }
                    // 2. Obsługa tokenów tekstowych AI
                    else {
                        setIsLoading(false);
                        setLoadingAI(false);
                        setChatResponse((prev) => prev + parsed);
                    }
                }
            }
            setIsComplete(true);
        } catch (error) {
            setIsComplete(true);
            console.error("Streaming error:", error);
            setError(true)
            setChatResponse("Service unavailable. Please try again later.");
        } finally {
            setLoadingAI(false);
            setIsLoading(false);
        }
    };

    const handleCopy = () => {
        if (chatResponse) {
            navigator.clipboard.writeText(chatResponse);
            setCopied(true);
            setTimeout(() => setCopied(false), 2000);
        }
    };

    if (loading) return <div className="loading-screen">Authenticating...</div>;

    return (
        <div className="ai-container">
            <div className="ai-title">
                {user ? `Welcome, ${user.name}!` : "Welcome to AI Knowledge Base!"}
            </div>

            <p className="ai-subtitle">
                Query vector database for instant insights and documentation analysis.
            </p>

            {!user && (
                <div className="signin-banner" onClick={login} title="Sign in with Google">
                    <i className="lni lni-google"></i>
                    <span>Sign in with Google to add entries to the knowledge base</span>
                    <i className="lni lni-arrow-right"></i>
                </div>
            )}

            <div className="ai-card">
                <div className="ai-card-header">
                    <div className="ai-card-title">Ask Your Knowledge Base</div>
                </div>

                <div className="chat-input-wrapper">

                    <input
                        className="chat-input"
                        type="text"
                        value={question}
                        onChange={(e) => setQuestion(e.target.value)}
                        placeholder="Ask a technical question about your knowledge base..."
                        onKeyDown={(e) => e.key === 'Enter' && handleAsk()}
                        disabled={loadingAI || isLoading} // disabled during searching
                    />

                    <button
                        // add class 'is-ready' if question is not empty
                        className={`btn btn-circle btn-send ${question.trim().length > 0 ? 'is-ready' : ''}`}
                        onClick={handleAsk}
                        title="Ask AI"
                        disabled={loadingAI || isLoading || (!isComplete && chatResponse !== "") || question.trim().length === 0}
                    >
                        <div className='btn-spinner'>
                            {(loadingAI || isLoading || (chatResponse && !isComplete))
                                ? <i className="lni lni-spinner lni-is-spinning"></i>
                                : <i className="lni lni-telegram-original"></i>
                            }
                        </div>
                    </button>
                </div>

                {/* Loader */}
                {isLoading && (
                    <div className="pulse-loader">
                        <span className="pulse-dot"></span>
                        <span className="pulse-dot"></span>
                        <span className="pulse-dot"></span>
                        <span className="pulse-label">Analyzing knowledge base...</span>
                    </div>
                )}

                {/* AI ANSWER */}
                {chatResponse && (
                    <div className={`response-box-answer ${error ? "error" : ""}`}>
                        <div className="response-header-wrapper">
                            <div className="response-header-answer">
                                {error ? "Error" : "Answer"}
                            </div>

                            {!error && (
                                <button
                                    className={`btn-icon-copy ${copied ? 'copied' : ''}`}
                                    onClick={handleCopy}
                                    title={copied ? "Copied!" : "Copy to clipboard"}
                                >
                                    {copied ? (
                                        <i className="lni lni-checkmark"></i>
                                    ) : (
                                        <i className="lni lni-clipboard"></i>
                                    )}
                                </button>
                            )}
                        </div>

                        <hr className="gradient-center-light" />

                        <div className="response-content-relative">
                            <ReactMarkdown className="markdown-content">
                                {isComplete ? chatResponse : chatResponse + " ▊"}
                            </ReactMarkdown>
                        </div>
                    </div>
                )}

                {/* REFERENCES */}
                {relatedDocs.length > 0 && (
                    <div className="response-box-references">
                        <div className="response-header-references">
                            References ({relatedDocs.length}):
                        </div>
                        <div className="references-scroll">
                            {relatedDocs.map((doc, index) => (
                                <details
                                    key={index}
                                    className="reference-item"
                                    style={{ animationDelay: `${index * 0.1}s` }}
                                >
                                    <summary className="reference-summary">
                                        <div className="reference-summary-content">
                                            <span className="ref-badge">
                                                #{index + 1}
                                            </span>
                                            <code className="ref-id">
                                                {doc.documentId?.substring(0, 8) || doc.id?.substring(0, 8)}...
                                            </code>
                                            <span className="ref-preview">
                                                {(doc.content || doc.text)?.substring(0, 110)}...
                                            </span>
                                        </div>
                                        <i className="lni lni-chevron-down ref-chevron"></i>
                                    </summary>
                                    <div className="reference-text">
                                        <div className="ref-full-content">
                                            {doc.content || doc.text}
                                        </div>
                                    </div>
                                </details>
                            ))}
                        </div>
                    </div>
                )}

            </div>
        </div>
    );
};

export default KnowledgeQuery;
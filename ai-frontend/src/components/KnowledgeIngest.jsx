import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import ConfirmationModal from './ConfirmationModal';
import { useToast } from '../hooks/useToast';

const KnowledgeIngest = ({ apiUrl = "/api/ai" }) => {
    const [documents, setDocuments] = useState([]);
    const [textToIngest, setTextToIngest] = useState("");

    // Destructure both methods from the updated useToast hook
    const { showToast, updateToast } = useToast();

    const [loading, setLoading] = useState(false);
    const [docCount, setDocCount] = useState(0);
    const [tableLoading, setTableLoading] = useState(true);

    // Pagination state
    const [currentPage, setCurrentPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const pageSize = 5;

    // Modal and edit state
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [documentToDelete, setDocumentToDelete] = useState(null);
    const [editingId, setEditingId] = useState(null);

    // Fetch paginated documents and total count in parallel
    const loadData = useCallback(async () => {
        setTableLoading(true);

        try {
            const [docsRes, countRes] = await Promise.all([
                axios.get(`${apiUrl}/documents`, {
                    params: { page: currentPage, size: pageSize }
                }),
                axios.get(`${apiUrl}/documents/count`)
            ]);

            // DATA
            const docs = docsRes.data.content ?? [];
            setDocuments(docs);

            // PAGINATION
            setTotalPages(docsRes.data.page?.totalPages ?? 0);

            // COUNT
            // Guard against non-JSON responses (e.g. HTML error page from dev server)
            // If the backend is down, the proxy may return an HTML page instead of a number.
            const count = countRes.data;
            setDocCount(typeof count === 'number' ? count : 0);

        } catch (err) {
            console.error("Knowledge base fetch error:", err);
            setDocuments([]);
            setTotalPages(0);
            setDocCount(0);
        } finally {
            setTableLoading(false);
        }

    }, [apiUrl, currentPage]);

    useEffect(() => {
        loadData();
    }, [loadData, currentPage]);

    // Handle both Create (POST) and Update (PUT) with dynamic toast updates
    const handleIngestOrUpdate = async () => {
        if (!textToIngest.trim()) return;

        // Initialize toast with 'loading' type and duration 0 to keep it visible
        const toastId = showToast(
            'loading',
            editingId ? "Updating..." : "Saving...",
            "Processing your request...",
            0
        );

        try {
            setLoading(true);

            if (editingId) {
                await axios.put(`${apiUrl}/ingest/${editingId}`, { text: textToIngest.trim() });

                // Transition the same toast to success state
                updateToast(toastId, {
                    type: 'success',
                    title: 'Updated',
                    msg: 'The entry has been successfully modified.',
                    duration: 3000
                });
            } else {
                await axios.post(`${apiUrl}/ingest`, { text: textToIngest.trim() });

                // Transition the same toast to success state
                updateToast(toastId, {
                    type: 'success',
                    title: 'Saved',
                    msg: 'New entry has been added to the knowledge base.',
                    duration: 3000
                });
            }

            // Reset form and reload table
            setTextToIngest("");
            setEditingId(null);
            await loadData();
        } catch (err) {
            console.error("Action error:", err);

            // Transition the same toast to error state
            updateToast(toastId, {
                type: 'error',
                title: 'Error',
                msg: 'Something went wrong. Please try again.',
                duration: 5000
            });
        } finally {
            setLoading(false);
        }
    };

    const startEdit = (doc) => {
        if (!doc) return;
        setEditingId(doc.documentId || null);
        setTextToIngest(doc.content || "");
        window.scrollTo({ top: 0, behavior: 'smooth' });
    };

    const confirmDelete = (id) => {
        setDocumentToDelete(id);
        setIsModalOpen(true);
    };

    // Execute delete with dynamic toast updates
    const handleConfirmDelete = async () => {
        if (!documentToDelete) return;

        const toastId = showToast('loading', 'Deleting...', 'Removing entry from database...', 0);

        try {
            setLoading(true);
            await axios.delete(`${apiUrl}/ingest/${documentToDelete}`);
            await loadData();

            updateToast(toastId, {
                type: 'success',
                title: 'Deleted',
                msg: 'The entry has been successfully removed.',
                duration: 3000
            });
        } catch (err) {
            console.error("Delete error:", err);

            updateToast(toastId, {
                type: 'error',
                title: 'Delete Failed',
                msg: 'Could not remove the entry. Please try again.',
                duration: 5000
            });
        } finally {
            setLoading(false);
            setIsModalOpen(false);
            setDocumentToDelete(null);
        }
    };

    return (
        <div className="ai-container">
            <div className="ai-title">Manage Knowledge Base</div>
            <p className="ai-subtitle">
                Add, edit, or remove entries from your knowledge base.
            </p>

            {/* ADDING / UPDATING */}
            <div className="ai-card">
                <div className="ai-card-header">
                    <div className="ai-card-title">
                        {editingId ? " Edit Entry" : " Add to Knowledge Base"}
                    </div>
                </div>

                <textarea
                    rows="4"
                    value={textToIngest}
                    onChange={(e) => setTextToIngest(e.target.value)}
                    placeholder="Paste or type text to index..."
                />
                <div className="save-buttons">
                    {editingId && (
                        <button
                            className="btn btn-pill"
                            onClick={() => {
                                setEditingId(null);
                                setTextToIngest("");
                            }}
                            title="Cancel this operation"
                        >
                            <i className="lni lni-circle-minus nav-item-btn-icon"></i>
                            Cancel
                        </button>
                    )}

                    <button
                        className="btn btn-pill"
                        onClick={handleIngestOrUpdate}
                        title={editingId ? "Save changes to this entry" : "Add new entry to the Knowledge Base"}
                        disabled={loading || !textToIngest || !textToIngest.trim()}
                    >
                        <i className="lni lni-circle-plus nav-item-btn-icon"></i>
                        {loading ? 'Processing...' : (editingId ? 'Save Changes' : 'Save Entry')}
                    </button>
                </div>

                <hr className="gradient-center-light" />

                {/* TABLE */}
                <div className="table-wrapper">
                    {tableLoading ? (
                        <div className="table-skeleton">
                            <div className="skeleton-header-row">
                                <div className="skeleton-cell" style={{ width: '15%' }}></div>
                                <div className="skeleton-cell" style={{ width: '68%' }}></div>
                                <div className="skeleton-cell" style={{ width: '10%' }}></div>
                            </div>
                            {[...Array(5)].map((_, i) => (
                                <div key={i} className="skeleton-row">
                                    <div className="skeleton-cell" style={{ width: '15%' }}></div>
                                    <div className="skeleton-cell" style={{ width: '68%' }}></div>
                                    <div className="skeleton-cell" style={{ width: '10%' }}></div>
                                </div>
                            ))}
                        </div>
                    ) : (
                        <table className="table">
                            <thead>
                                <tr>
                                    <th style={{ width: '140px' }}>ID</th>
                                    <th>Content</th>
                                    <th style={{ width: '80px' }}>Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                {documents.length === 0 ? (
                                    <tr>
                                        <td colSpan="3" className="empty-state">
                                            No entries found. Add your first entry above.
                                        </td>
                                    </tr>
                                ) : (
                                    documents.map(doc => (
                                        <tr key={doc.documentId}>
                                            <td>
                                                {doc.documentId?.substring(0, 8) || "N/A"}...
                                            </td>
                                            <td>
                                                <div className="cell-content">{doc.content}</div>
                                            </td>
                                            <td>
                                                <div className="actions-icons">
                                                    <button
                                                        className="btn btn-circle"
                                                        onClick={() => startEdit(doc)}
                                                        title="Edit this entry"
                                                        disabled={!!editingId}
                                                    >
                                                        <i className="lni lni-pencil"></i>
                                                    </button>

                                                    <button
                                                        className="btn btn-circle"
                                                        onClick={() => confirmDelete(doc.documentId)}
                                                        title="Delete this entry"
                                                        disabled={!!editingId}
                                                    >
                                                        <i className="lni lni-trash-can"></i>
                                                    </button>
                                                </div>
                                            </td>
                                        </tr>
                                    ))
                                )}
                            </tbody>
                        </table>
                    )}
                </div>

                {/* PAGINTION */}
                <div className="pagination-bar">
                    <span>Total entries: <strong>{docCount}</strong></span>
                    <div className="pagination-content">
                        <button
                            className="btn btn-circle"
                            disabled={currentPage === 0 || !!editingId}
                            onClick={() => setCurrentPage(p => p - 1)}
                            title="Previous page"
                        >
                            <i className="lni lni-chevron-left"></i>
                        </button>

                        <span>
                            {currentPage + 1} <span>/</span> {totalPages}
                        </span>

                        <button
                            className="btn btn-circle"
                            disabled={currentPage >= totalPages - 1 || !!editingId}
                            onClick={() => setCurrentPage(p => p + 1)}
                            title="Next page"
                        >
                            <i className="lni lni-chevron-right"></i>
                        </button>
                    </div>
                </div>

            </div>

            <ConfirmationModal
                isOpen={isModalOpen}
                title="Confirm Deletion"
                message="Are you sure you want to remove this entry from the knowledge base?"
                onConfirm={handleConfirmDelete}
                onCancel={() => setIsModalOpen(false)}
            />
        </div>
    );
};

export default KnowledgeIngest;
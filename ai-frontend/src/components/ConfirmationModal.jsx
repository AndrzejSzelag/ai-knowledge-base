import React from 'react';
import '../App.css';

// Generic reusable confirmation modal — used for destructive actions like deletion
const ConfirmationModal = ({ isOpen, title, message, onConfirm, onCancel }) => {

    // Render nothing when modal is closed
    if (!isOpen) return null;

    return (
        <div className="modal-overlay">
            <div className="modal-box">
                <div className="modal-title">{title}</div>
                <div className="modal-message">{message}</div>
                <div className="modal-actions">
                    {/* Cancel: closes modal without taking action */}
                    <span><button
                        onClick={onCancel}
                        className="btn btn-pill">Cancel</button>
                    </span>
                    {/* Confirm: proceeds with the destructive action */}
                    <span><button
                        onClick={onConfirm}
                        className="btn btn-pill btn-confirm">Confirm</button></span>
                </div>
            </div>
        </div>
    );
};

export default ConfirmationModal;
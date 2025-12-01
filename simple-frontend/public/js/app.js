// API Base URL - points to your API Gateway
const API_BASE = 'http://localhost:8080';

// Tab switching
document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
        document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
        document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
        btn.classList.add('active');
        document.getElementById(btn.dataset.tab).classList.add('active');
    });
});

// Show status message
function showStatus(message, type = 'info') {
    const statusEl = document.getElementById('status-message');
    statusEl.textContent = message;
    statusEl.className = `status-message ${type} show`;
    setTimeout(() => statusEl.classList.remove('show'), 3000);
}

// ========== USER PREFERENCES ==========

// Load user preferences by userId
async function loadPreferences() {
    const userId = document.getElementById('pref-userId').value.trim();
    if (!userId) {
        showStatus('Please enter a user ID', 'error');
        return;
    }
    
    try {
        showStatus('Loading preferences...', 'info');
        const response = await fetch(`${API_BASE}/api/preferences/${userId}`);
        
        if (response.ok) {
            const data = await response.json();
            document.getElementById('pref-email').value = data.email || '';
            document.getElementById('pref-phone').value = data.phoneNumber || '';
            
            // Load channels
            const channels = data.enabledChannels || [];
            document.getElementById('channel-email').checked = channels.includes('EMAIL');
            document.getElementById('channel-whatsapp').checked = channels.includes('WHATSAPP');
            document.getElementById('channel-sms').checked = channels.includes('SMS');
            document.getElementById('channel-app').checked = channels.includes('APP');
            
            // Load preferences (as array)
            const preferences = data.preferences || [];
            document.getElementById('cat-sports').checked = preferences.includes('SPORTS');
            document.getElementById('cat-news').checked = preferences.includes('NEWS');
            document.getElementById('cat-weather').checked = preferences.includes('WEATHER');
            document.getElementById('cat-shopping').checked = preferences.includes('SHOPPING');
            document.getElementById('cat-finance').checked = preferences.includes('FINANCE');
            document.getElementById('cat-entertainment').checked = preferences.includes('ENTERTAINMENT');
            document.getElementById('cat-health').checked = preferences.includes('HEALTH');
            document.getElementById('cat-technology').checked = preferences.includes('TECHNOLOGY');
            document.getElementById('cat-travel').checked = preferences.includes('TRAVEL');
            document.getElementById('cat-social').checked = preferences.includes('SOCIAL');
            document.getElementById('cat-education').checked = preferences.includes('EDUCATION');
            document.getElementById('cat-promotions').checked = preferences.includes('PROMOTIONS');
            
            showStatus('Preferences loaded successfully', 'success');
        } else if (response.status === 404) {
            showStatus('User not found. You can create new preferences.', 'info');
            clearPreferenceForm();
        } else {
            throw new Error('Failed to load preferences');
        }
    } catch (error) {
        console.error('Error:', error);
        showStatus('Failed to connect to API. Is the backend running?', 'error');
    }
}

// Load all preferences
async function loadAllPreferences() {
    try {
        showStatus('Loading all preferences...', 'info');
        const response = await fetch(`${API_BASE}/api/preferences`);
        
        if (response.ok) {
            const preferences = await response.json();
            const listEl = document.getElementById('all-preferences-list');
            
            if (preferences.length === 0) {
                listEl.innerHTML = '<p class="empty-state">No preferences found</p>';
            } else {
                listEl.innerHTML = preferences.map(p => {
                    // Get preferences array
                    const userPreferences = p.preferences || [];
                    return `
                    <div class="list-item" onclick="selectPreference('${p.userId}')">
                        <div class="item-header">
                            <strong>${escapeHtml(p.userId)}</strong>
                            <span class="badge">${(p.enabledChannels || []).join(', ') || 'No channels'}</span>
                        </div>
                        <div class="item-details">
                            <span>📧 ${escapeHtml(p.email || 'N/A')}</span>
                            <span>📱 ${escapeHtml(p.phoneNumber || 'N/A')}</span>
                        </div>
                        ${userPreferences.length > 0 ? `
                        <div class="item-categories">
                            ${userPreferences.map(cat => `<span class="category-tag">${getCategoryEmoji(cat)} ${cat}</span>`).join('')}
                        </div>
                        ` : '<div class="item-categories"><span class="category-tag">No preferences selected</span></div>'}
                    </div>
                `}).join('');
            }
            showStatus(`Loaded ${preferences.length} preferences`, 'success');
        } else {
            throw new Error('Failed to load preferences');
        }
    } catch (error) {
        console.error('Error:', error);
        showStatus('Failed to load preferences', 'error');
    }
}

// Get emoji for category
function getCategoryEmoji(category) {
    const emojis = {
        'SPORTS': '⚽',
        'NEWS': '📰',
        'WEATHER': '🌤️',
        'SHOPPING': '🛒',
        'FINANCE': '💰',
        'ENTERTAINMENT': '🎬',
        'HEALTH': '🏥',
        'TECHNOLOGY': '💻',
        'TRAVEL': '✈️',
        'SOCIAL': '👥',
        'EDUCATION': '📚',
        'PROMOTIONS': '🎁'
    };
    return emojis[category] || '📌';
}

// Select a preference from the list
function selectPreference(userId) {
    document.getElementById('pref-userId').value = userId;
    loadPreferences();
}

// Clear preference form
function clearPreferenceForm() {
    document.getElementById('pref-email').value = '';
    document.getElementById('pref-phone').value = '';
    // Clear channels
    document.getElementById('channel-email').checked = false;
    document.getElementById('channel-whatsapp').checked = false;
    document.getElementById('channel-sms').checked = false;
    document.getElementById('channel-app').checked = false;
    // Clear preferences (categories)
    document.getElementById('cat-sports').checked = false;
    document.getElementById('cat-news').checked = false;
    document.getElementById('cat-weather').checked = false;
    document.getElementById('cat-shopping').checked = false;
    document.getElementById('cat-finance').checked = false;
    document.getElementById('cat-entertainment').checked = false;
    document.getElementById('cat-health').checked = false;
    document.getElementById('cat-technology').checked = false;
    document.getElementById('cat-travel').checked = false;
    document.getElementById('cat-social').checked = false;
    document.getElementById('cat-education').checked = false;
    document.getElementById('cat-promotions').checked = false;
}

// Get enabled channels from checkboxes
function getEnabledChannels() {
    const channels = [];
    if (document.getElementById('channel-email').checked) channels.push('EMAIL');
    if (document.getElementById('channel-whatsapp').checked) channels.push('WHATSAPP');
    if (document.getElementById('channel-sms').checked) channels.push('SMS');
    if (document.getElementById('channel-app').checked) channels.push('APP');
    return channels;
}

// Get selected categories from checkboxes
function getSelectedCategories() {
    const categories = [];
    if (document.getElementById('cat-sports').checked) categories.push('SPORTS');
    if (document.getElementById('cat-news').checked) categories.push('NEWS');
    if (document.getElementById('cat-weather').checked) categories.push('WEATHER');
    if (document.getElementById('cat-shopping').checked) categories.push('SHOPPING');
    if (document.getElementById('cat-finance').checked) categories.push('FINANCE');
    if (document.getElementById('cat-entertainment').checked) categories.push('ENTERTAINMENT');
    if (document.getElementById('cat-health').checked) categories.push('HEALTH');
    if (document.getElementById('cat-technology').checked) categories.push('TECHNOLOGY');
    if (document.getElementById('cat-travel').checked) categories.push('TRAVEL');
    if (document.getElementById('cat-social').checked) categories.push('SOCIAL');
    if (document.getElementById('cat-education').checked) categories.push('EDUCATION');
    if (document.getElementById('cat-promotions').checked) categories.push('PROMOTIONS');
    return categories;
}

// Create preference (POST)
document.getElementById('preference-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    const userId = document.getElementById('pref-userId').value.trim();
    if (!userId) {
        showStatus('Please enter a user ID', 'error');
        return;
    }
    
    const selectedPreferences = getSelectedCategories();
    if (selectedPreferences.length === 0) {
        showStatus('Please select at least one preference category', 'error');
        return;
    }
    
    const data = {
        userId: userId,
        email: document.getElementById('pref-email').value,
        phoneNumber: document.getElementById('pref-phone').value,
        preferences: selectedPreferences,
        enabledChannels: getEnabledChannels()
    };
    
    try {
        showStatus('Creating preference...', 'info');
        const response = await fetch(`${API_BASE}/api/preferences`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });
        
        if (response.ok || response.status === 201) {
            showStatus('Preference created successfully!', 'success');
            loadAllPreferences();
        } else {
            const error = await response.text();
            throw new Error(error || 'Failed to create preference');
        }
    } catch (error) {
        console.error('Error:', error);
        showStatus('Failed to create preference: ' + error.message, 'error');
    }
});

// Update preference (PUT)
async function updatePreference() {
    const userId = document.getElementById('pref-userId').value.trim();
    if (!userId) {
        showStatus('Please enter a user ID', 'error');
        return;
    }
    
    const selectedPreferences = getSelectedCategories();
    if (selectedPreferences.length === 0) {
        showStatus('Please select at least one preference category', 'error');
        return;
    }
    
    const data = {
        userId: userId,
        email: document.getElementById('pref-email').value,
        phoneNumber: document.getElementById('pref-phone').value,
        preferences: selectedPreferences,
        enabledChannels: getEnabledChannels()
    };
    
    try {
        showStatus('Updating preference...', 'info');
        const response = await fetch(`${API_BASE}/api/preferences/${userId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });
        
        if (response.ok) {
            showStatus('Preference updated successfully!', 'success');
            loadAllPreferences();
        } else {
            throw new Error('Failed to update preference');
        }
    } catch (error) {
        console.error('Error:', error);
        showStatus('Failed to update preference', 'error');
    }
}

// Delete preference
async function deletePreference() {
    const userId = document.getElementById('pref-userId').value.trim();
    if (!userId) {
        showStatus('Please enter a user ID', 'error');
        return;
    }
    
    if (!confirm(`Are you sure you want to delete preferences for user "${userId}"?`)) {
        return;
    }
    
    try {
        showStatus('Deleting preference...', 'info');
        const response = await fetch(`${API_BASE}/api/preferences/${userId}`, {
            method: 'DELETE'
        });
        
        if (response.ok || response.status === 204) {
            showStatus('Preference deleted successfully!', 'success');
            clearPreferenceForm();
            document.getElementById('pref-userId').value = '';
            loadAllPreferences();
        } else {
            throw new Error('Failed to delete preference');
        }
    } catch (error) {
        console.error('Error:', error);
        showStatus('Failed to delete preference', 'error');
    }
}

// ========== NOTIFICATIONS ==========

// Get notification channels from checkboxes
function getNotificationChannels() {
    const channels = [];
    if (document.getElementById('notif-channel-email').checked) channels.push('EMAIL');
    if (document.getElementById('notif-channel-whatsapp').checked) channels.push('WHATSAPP');
    if (document.getElementById('notif-channel-app').checked) channels.push('APP');
    return channels;
}

// Send notification (POST)
document.getElementById('notification-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    const channels = getNotificationChannels();
    if (channels.length === 0) {
        showStatus('Please select at least one channel', 'error');
        return;
    }
    
    const data = {
        userId: document.getElementById('notif-userId').value,
        subject: document.getElementById('notif-subject').value,
        message: document.getElementById('notif-message').value,
        channels: channels
    };
    
    try {
        showStatus('Sending notification...', 'info');
        const response = await fetch(`${API_BASE}/api/notifications`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });
        
        if (response.ok || response.status === 201) {
            const result = await response.json();
            showStatus(`Notification sent! Status: ${result.status}`, 'success');
            document.getElementById('notif-subject').value = '';
            document.getElementById('notif-message').value = '';
        } else {
            const error = await response.text();
            throw new Error(error || 'Failed to send notification');
        }
    } catch (error) {
        console.error('Error:', error);
        showStatus('Failed to send notification: ' + error.message, 'error');
    }
});

// ========== NOTIFICATION HISTORY ==========

// Load notifications by userId
async function loadHistory() {
    const userId = document.getElementById('history-userId').value.trim();
    if (!userId) {
        showStatus('Please enter a user ID or click "Load All"', 'error');
        return;
    }
    
    try {
        showStatus('Loading history...', 'info');
        const response = await fetch(`${API_BASE}/api/notifications/user/${userId}`);
        
        if (response.ok) {
            const notifications = await response.json();
            renderNotificationHistory(notifications);
            showStatus(`Loaded ${notifications.length} notifications`, 'success');
        } else if (response.status === 404) {
            document.getElementById('history-list').innerHTML = 
                '<p class="empty-state">No notifications found for this user</p>';
            showStatus('No notifications found', 'info');
        } else {
            throw new Error('Failed to load history');
        }
    } catch (error) {
        console.error('Error:', error);
        showStatus('Failed to load history', 'error');
    }
}

// Load all notifications
async function loadAllNotifications() {
    try {
        showStatus('Loading all notifications...', 'info');
        const response = await fetch(`${API_BASE}/api/notifications`);
        
        if (response.ok) {
            const notifications = await response.json();
            renderNotificationHistory(notifications);
            showStatus(`Loaded ${notifications.length} notifications`, 'success');
        } else {
            throw new Error('Failed to load notifications');
        }
    } catch (error) {
        console.error('Error:', error);
        showStatus('Failed to load notifications', 'error');
    }
}

// Render notification history list
function renderNotificationHistory(notifications) {
    const historyList = document.getElementById('history-list');
    
    if (notifications.length === 0) {
        historyList.innerHTML = '<p class="empty-state">No notifications found</p>';
        return;
    }
    
    historyList.innerHTML = notifications.map(n => `
        <div class="history-item ${n.status ? n.status.toLowerCase() : ''}">
            <div class="history-header">
                <span class="title">${escapeHtml(n.subject || 'No Subject')}</span>
                <span class="status-badge ${n.status ? n.status.toLowerCase() : ''}">${n.status || 'N/A'}</span>
            </div>
            <div class="message">${escapeHtml(n.message || '')}</div>
            <div class="meta">
                <span>👤 ${escapeHtml(n.userId || 'N/A')}</span>
                <span>📡 ${(n.channels || []).join(', ') || 'N/A'}</span>
                <span>🕐 ${formatDate(n.createdAt)}</span>
                ${n.sentAt ? `<span>✅ Sent: ${formatDate(n.sentAt)}</span>` : ''}
            </div>
            <button class="btn btn-small btn-danger" onclick="deleteNotification('${n.id}')">Delete</button>
        </div>
    `).join('');
}

// Delete notification
async function deleteNotification(id) {
    if (!confirm('Are you sure you want to delete this notification?')) {
        return;
    }
    
    try {
        const response = await fetch(`${API_BASE}/api/notifications/${id}`, {
            method: 'DELETE'
        });
        
        if (response.ok || response.status === 204) {
            showStatus('Notification deleted', 'success');
            // Reload the history
            const userId = document.getElementById('history-userId').value.trim();
            if (userId) {
                loadHistory();
            } else {
                loadAllNotifications();
            }
        } else {
            throw new Error('Failed to delete notification');
        }
    } catch (error) {
        console.error('Error:', error);
        showStatus('Failed to delete notification', 'error');
    }
}

// ========== HELPERS ==========

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function formatDate(dateString) {
    if (!dateString) return 'N/A';
    try {
        return new Date(dateString).toLocaleString();
    } catch {
        return dateString;
    }
}

// Check backend connectivity on load
async function checkBackend() {
    try {
        const response = await fetch(`${API_BASE}/api/preferences`, { 
            method: 'GET',
            mode: 'cors'
        });
        if (response.ok) {
            showStatus('Connected to backend services ✓', 'success');
        }
    } catch (error) {
        showStatus('⚠️ Backend not reachable. Make sure services are running.', 'error');
    }
}

document.addEventListener('DOMContentLoaded', () => {
    checkBackend();
});

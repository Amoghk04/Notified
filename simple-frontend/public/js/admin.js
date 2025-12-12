// Admin Dashboard JavaScript

// Chart instances
let dailyChart = null;
let categoryChart = null;
let frequencyChart = null;
let channelChart = null;
let statusChart = null;

// Data storage
let usersData = [];

// Initialize dashboard
document.addEventListener('DOMContentLoaded', () => {
    initNavigation();
    refreshAllData();
    
    // Auto-refresh every 60 seconds
    setInterval(refreshAllData, 60000);
});

// Navigation
function initNavigation() {
    const navItems = document.querySelectorAll('.nav-item');
    
    navItems.forEach(item => {
        item.addEventListener('click', (e) => {
            e.preventDefault();
            
            // Update active nav item
            navItems.forEach(nav => nav.classList.remove('active'));
            item.classList.add('active');
            
            // Show corresponding section
            const sectionId = item.dataset.section;
            showSection(sectionId);
        });
    });
}

function showSection(sectionId) {
    // Hide all sections
    document.querySelectorAll('.content-section').forEach(section => {
        section.classList.remove('active');
    });
    
    // Show selected section
    const section = document.getElementById(`${sectionId}-section`);
    if (section) {
        section.classList.add('active');
    }
    
    // Update header
    const titles = {
        'overview': 'Overview',
        'users': 'User Management',
        'notifications': 'Notifications',
        'scraper': 'News Scraper',
        'activity': 'Activity Log'
    };
    
    document.getElementById('section-title').textContent = titles[sectionId] || 'Dashboard';
}

// Refresh all data
async function refreshAllData() {
    updateLastUpdated();
    await Promise.all([
        loadUserStats(),
        loadNotificationStats(),
        loadScraperStats(),
        loadRecentActivity(),
        loadUsersList()
    ]);
    checkSystemStatus();
}

function updateLastUpdated() {
    const now = new Date();
    document.getElementById('last-updated').textContent = now.toLocaleTimeString();
}

// Check system status
async function checkSystemStatus() {
    const statusEl = document.getElementById('system-status');
    
    try {
        const response = await fetch('/api/scraper/health');
        if (response.ok) {
            statusEl.innerHTML = '<span class="status-dot"></span><span>All Systems Online</span>';
            statusEl.className = 'status-indicator online';
        } else {
            throw new Error('Service unavailable');
        }
    } catch (error) {
        statusEl.innerHTML = '<span class="status-dot"></span><span>Some Services Offline</span>';
        statusEl.className = 'status-indicator offline';
    }
}

// Load User Stats
async function loadUserStats() {
    try {
        const response = await fetch('/api/admin/users/stats/users');
        if (!response.ok) throw new Error('Failed to load user stats');
        
        const data = await response.json();
        
        // Update overview cards
        document.getElementById('total-users').textContent = data.totalUsers || 0;
        document.getElementById('telegram-users').textContent = data.telegramUsers || 0;
        
        // Update category chart
        if (data.categoryDistribution) {
            updateCategoryChart(data.categoryDistribution);
        }
        
        // Update frequency chart
        if (data.frequencyDistribution) {
            updateFrequencyChart(data.frequencyDistribution);
        }
        
    } catch (error) {
        console.error('Error loading user stats:', error);
        document.getElementById('total-users').textContent = '-';
        document.getElementById('telegram-users').textContent = '-';
    }
}

// Load Notification Stats
async function loadNotificationStats() {
    try {
        const response = await fetch('/api/admin/notifications/stats/notifications');
        if (!response.ok) throw new Error('Failed to load notification stats');
        
        const data = await response.json();
        
        // Update overview cards
        document.getElementById('total-notifications').textContent = data.totalNotifications || 0;
        document.getElementById('sent-24h').textContent = data.sentLast24Hours || 0;
        
        // Update reactions
        if (data.reactions) {
            document.getElementById('likes-count').textContent = data.reactions.likes || 0;
            document.getElementById('dislikes-count').textContent = data.reactions.dislikes || 0;
        }
        
        // Update notifications section stats
        document.getElementById('notif-total').textContent = data.totalNotifications || 0;
        document.getElementById('notif-success').textContent = data.byStatus?.SENT || 0;
        document.getElementById('notif-pending').textContent = data.byStatus?.PENDING || 0;
        document.getElementById('notif-failed').textContent = data.byStatus?.FAILED || 0;
        
        // Update daily chart
        if (data.dailyBreakdown) {
            updateDailyChart(data.dailyBreakdown);
        }
        
        // Update channel chart
        if (data.byChannel) {
            updateChannelChart(data.byChannel);
        }
        
        // Update status chart
        if (data.byStatus) {
            updateStatusChart(data.byStatus);
        }
        
    } catch (error) {
        console.error('Error loading notification stats:', error);
        document.getElementById('total-notifications').textContent = '-';
        document.getElementById('sent-24h').textContent = '-';
    }
}

// Load Scraper Stats
async function loadScraperStats() {
    try {
        // Get categories
        const categoriesRes = await fetch('/api/scraper/categories');
        if (categoriesRes.ok) {
            const categories = await categoriesRes.json();
            document.getElementById('scraper-categories').textContent = categories.length + ' categories';
            
            // Load article counts per category
            const countsContainer = document.getElementById('category-counts');
            countsContainer.innerHTML = '';
            
            for (const category of categories) {
                try {
                    const countRes = await fetch(`/api/scraper/articles/${category}/count`);
                    if (countRes.ok) {
                        const countData = await countRes.json();
                        
                        const item = document.createElement('div');
                        item.className = 'category-count-item';
                        item.innerHTML = `
                            <div class="count">${countData.count || 0}</div>
                            <div class="name">${category}</div>
                        `;
                        countsContainer.appendChild(item);
                    }
                } catch (e) {
                    console.warn(`Failed to get count for ${category}`);
                }
            }
        }
    } catch (error) {
        console.error('Error loading scraper stats:', error);
    }
}

// Load Recent Activity
async function loadRecentActivity() {
    try {
        const response = await fetch('/api/admin/notifications/stats/notifications/recent?limit=30');
        if (!response.ok) throw new Error('Failed to load activity');
        
        const activities = await response.json();
        const feedContainer = document.getElementById('activity-feed');
        
        if (activities.length === 0) {
            feedContainer.innerHTML = '<p class="loading">No recent activity</p>';
            return;
        }
        
        feedContainer.innerHTML = activities.map(activity => `
            <div class="activity-item">
                <div class="activity-icon">${getActivityIcon(activity.status)}</div>
                <div class="activity-content">
                    <div class="activity-title">${escapeHtml(activity.subject || 'Notification')}</div>
                    <div class="activity-meta">
                        To: ${activity.userId} • 
                        ${activity.channels?.join(', ') || 'Unknown'} • 
                        ${formatTime(activity.sentAt)}
                        ${activity.reaction ? ` • ${activity.reaction === 'like' ? '👍' : '👎'}` : ''}
                    </div>
                </div>
                <span class="activity-status ${activity.status?.toLowerCase() || ''}">${activity.status}</span>
            </div>
        `).join('');
        
    } catch (error) {
        console.error('Error loading activity:', error);
        document.getElementById('activity-feed').innerHTML = '<p class="loading">Failed to load activity</p>';
    }
}

// Load Users List
async function loadUsersList() {
    try {
        const response = await fetch('/api/admin/users/stats/users/list');
        if (!response.ok) throw new Error('Failed to load users');
        
        usersData = await response.json();
        renderUsersTable(usersData);
        
    } catch (error) {
        console.error('Error loading users:', error);
        document.getElementById('users-tbody').innerHTML = 
            '<tr><td colspan="6" class="loading">Failed to load users</td></tr>';
    }
}

function renderUsersTable(users) {
    const tbody = document.getElementById('users-tbody');
    
    if (users.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="loading">No users found</td></tr>';
        return;
    }
    
    tbody.innerHTML = users.map(user => `
        <tr>
            <td><strong>${escapeHtml(user.userId)}</strong></td>
            <td>${escapeHtml(user.email || '-')}</td>
            <td>
                ${user.hasTelegram ? 
                    `<span class="telegram-badge">📱 ${escapeHtml(user.telegramUsername || 'Connected')}</span>` : 
                    '-'}
            </td>
            <td>
                ${(user.categories || []).map(cat => 
                    `<span class="category-tag">${cat}</span>`
                ).join('') || '-'}
            </td>
            <td>${user.frequencyLabel || '-'}</td>
            <td>${user.lastNotificationSent ? formatTime(user.lastNotificationSent) : 'Never'}</td>
        </tr>
    `).join('');
}

function filterUsers() {
    const search = document.getElementById('user-search').value.toLowerCase();
    
    const filtered = usersData.filter(user => 
        user.userId?.toLowerCase().includes(search) ||
        user.email?.toLowerCase().includes(search) ||
        user.telegramUsername?.toLowerCase().includes(search)
    );
    
    renderUsersTable(filtered);
}

// Trigger Scraping
async function triggerScraping() {
    try {
        const btn = event.target;
        btn.disabled = true;
        btn.textContent = '⏳ Scraping...';
        
        const response = await fetch('/api/scraper/scrape', { method: 'POST' });
        
        if (response.ok) {
            alert('Scraping triggered successfully! Check logs for progress.');
        } else {
            alert('Failed to trigger scraping');
        }
    } catch (error) {
        console.error('Error triggering scraping:', error);
        alert('Error: ' + error.message);
    } finally {
        event.target.disabled = false;
        event.target.textContent = '▶️ Trigger Scraping';
    }
}

// Chart Updates
function updateDailyChart(data) {
    const ctx = document.getElementById('dailyChart').getContext('2d');
    
    const labels = Object.keys(data).map(date => {
        const d = new Date(date);
        return d.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
    });
    const values = Object.values(data);
    
    if (dailyChart) dailyChart.destroy();
    
    dailyChart = new Chart(ctx, {
        type: 'line',
        data: {
            labels: labels,
            datasets: [{
                label: 'Notifications Sent',
                data: values,
                borderColor: '#6366f1',
                backgroundColor: 'rgba(99, 102, 241, 0.1)',
                fill: true,
                tension: 0.4
            }]
        },
        options: {
            responsive: true,
            plugins: {
                legend: { display: false }
            },
            scales: {
                y: { beginAtZero: true }
            }
        }
    });
}

function updateCategoryChart(data) {
    const ctx = document.getElementById('categoryChart').getContext('2d');
    
    const labels = Object.keys(data);
    const values = Object.values(data);
    const colors = generateColors(labels.length);
    
    if (categoryChart) categoryChart.destroy();
    
    categoryChart = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: labels,
            datasets: [{
                data: values,
                backgroundColor: colors
            }]
        },
        options: {
            responsive: true,
            plugins: {
                legend: {
                    position: 'right',
                    labels: { boxWidth: 12 }
                }
            }
        }
    });
}

function updateFrequencyChart(data) {
    const ctx = document.getElementById('frequencyChart').getContext('2d');
    
    const labels = Object.keys(data);
    const values = Object.values(data);
    
    if (frequencyChart) frequencyChart.destroy();
    
    frequencyChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [{
                data: values,
                backgroundColor: '#6366f1'
            }]
        },
        options: {
            responsive: true,
            plugins: { legend: { display: false } },
            scales: {
                y: { beginAtZero: true }
            }
        }
    });
}

function updateChannelChart(data) {
    const ctx = document.getElementById('channelChart').getContext('2d');
    
    const labels = Object.keys(data);
    const values = Object.values(data);
    const colors = ['#0088cc', '#22c55e', '#f59e0b'];
    
    if (channelChart) channelChart.destroy();
    
    channelChart = new Chart(ctx, {
        type: 'pie',
        data: {
            labels: labels,
            datasets: [{
                data: values,
                backgroundColor: colors.slice(0, labels.length)
            }]
        },
        options: {
            responsive: true,
            plugins: {
                legend: { position: 'bottom' }
            }
        }
    });
}

function updateStatusChart(data) {
    const ctx = document.getElementById('statusChart').getContext('2d');
    
    const labels = Object.keys(data);
    const values = Object.values(data);
    const colors = {
        'SENT': '#22c55e',
        'PENDING': '#f59e0b',
        'FAILED': '#ef4444'
    };
    
    if (statusChart) statusChart.destroy();
    
    statusChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [{
                data: values,
                backgroundColor: labels.map(l => colors[l] || '#6366f1')
            }]
        },
        options: {
            responsive: true,
            plugins: { legend: { display: false } },
            scales: {
                y: { beginAtZero: true }
            }
        }
    });
}

// Utility functions
function generateColors(count) {
    const baseColors = [
        '#6366f1', '#22c55e', '#f59e0b', '#ef4444', '#3b82f6',
        '#8b5cf6', '#ec4899', '#14b8a6', '#f97316', '#64748b',
        '#84cc16', '#06b6d4'
    ];
    return baseColors.slice(0, count);
}

function formatTime(dateStr) {
    if (!dateStr) return '-';
    const date = new Date(dateStr);
    const now = new Date();
    const diffMs = now - date;
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMins / 60);
    const diffDays = Math.floor(diffHours / 24);
    
    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;
    
    return date.toLocaleDateString();
}

function getActivityIcon(status) {
    switch (status) {
        case 'SENT': return '✅';
        case 'PENDING': return '⏳';
        case 'FAILED': return '❌';
        default: return '📨';
    }
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

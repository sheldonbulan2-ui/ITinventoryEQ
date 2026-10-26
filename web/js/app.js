/**
 * IT Equipment Inventory System - Modern Web Admin Console Logic
 * Unified Design Tokens: #1E40AF (Primary), #0EA5E9 (Accent), #F8FAFC (Surface)
 * Features: View Switching, Instant Real-Time SSE Sync, QR Studio, Pagination, CSV Export
 */

// State
let allEquipment = [];
let allScanLogs = [];
let currentFilter = {
  search: '',
  category: 'All',
  status: 'All'
};
let currentUser = null;
let sseConnection = null;
let currentQrInstance = null;
let studioQrInstance = null;
let activeView = 'dashboard';
let soundEnabled = true;

// Pagination
let currentPage = 1;
const pageSize = 8;

// Initialize on DOM Ready
document.addEventListener('DOMContentLoaded', () => {
  initSoundPreference();
  checkAuth();
  initRealtimeSync();
  loadDashboardData();
  initKeyboardShortcuts();
});

// =============================================================================
// VIEW SWITCHING & NAVIGATION
// =============================================================================

function switchView(viewName) {
  activeView = viewName;

  // Hide all view panels
  document.querySelectorAll('.view-panel').forEach(el => el.classList.add('hidden'));

  // Show target panel
  const target = document.getElementById(`view-${viewName}`);
  if (target) {
    target.classList.remove('hidden');
  }

  // Update navigation link highlights
  document.querySelectorAll('.nav-link').forEach(el => el.classList.remove('active'));
  const activeNav = document.getElementById(`nav-${viewName}`);
  if (activeNav) {
    activeNav.classList.add('active');
  }

  // Update breadcrumb title
  const titleEl = document.getElementById('viewHeaderTitle');
  if (titleEl) {
    switch (viewName) {
      case 'dashboard':
        titleEl.textContent = 'Dashboard Overview';
        break;
      case 'inventory':
        titleEl.textContent = 'Master Equipment Catalog';
        break;
      case 'logs':
        titleEl.textContent = 'Full System Audit Trail';
        break;
      case 'qr-studio':
        titleEl.textContent = 'QR Asset Tag Studio';
        initStudioDropdown();
        break;
    }
  }

  // Close mobile sidebar if open
  const sidebar = document.getElementById('mainSidebar');
  if (window.innerWidth < 1024 && sidebar && !sidebar.classList.contains('-translate-x-full')) {
    toggleSidebar();
  }
}

function filterByStatusQuick(status) {
  document.getElementById('statusFilter').value = status;
  currentFilter.status = status;
  currentPage = 1;
  switchView('inventory');
  applyFilters();
}

function toggleSidebar() {
  const sidebar = document.getElementById('mainSidebar');
  sidebar.classList.toggle('hidden');
}

function initKeyboardShortcuts() {
  window.addEventListener('keydown', (e) => {
    // Ctrl + K or Cmd + K: Focus search
    if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
      e.preventDefault();
      const topSearch = document.getElementById('globalTopSearch');
      if (topSearch) topSearch.focus();
    }
    // Escape: Close modals
    if (e.key === 'Escape') {
      closeModal('equipmentModal');
      closeModal('qrModal');
      closeModal('changePasswordModal');
    }
  });
}

function handleGlobalSearch(e) {
  const query = e.target.value;
  document.getElementById('inventorySearchInput').value = query;
  currentFilter.search = query.toLowerCase().trim();
  currentPage = 1;
  if (activeView !== 'inventory') {
    switchView('inventory');
  }
  renderInventoryTable();
}

// =============================================================================
// SOUND PREFERENCE
// =============================================================================

function initSoundPreference() {
  const stored = localStorage.getItem('it_inventory_sound_enabled');
  if (stored !== null) {
    soundEnabled = stored === 'true';
  }
  updateSoundIcon();
}

function toggleAudioChime() {
  soundEnabled = !soundEnabled;
  localStorage.setItem('it_inventory_sound_enabled', String(soundEnabled));
  updateSoundIcon();
  showToast(soundEnabled ? 'Live scan audio chime enabled' : 'Audio chime muted');
}

function updateSoundIcon() {
  const btn = document.getElementById('btnSoundToggle');
  if (!btn) return;
  if (soundEnabled) {
    btn.innerHTML = '<i class="fa-solid fa-volume-high text-[#0EA5E9]"></i>';
  } else {
    btn.innerHTML = '<i class="fa-solid fa-volume-xmark text-slate-300"></i>';
  }
}

function playChime() {
  if (!soundEnabled) return;
  try {
    const audio = document.getElementById('scanChime');
    if (audio) {
      audio.currentTime = 0;
      audio.play().catch(() => {});
    }
  } catch (e) {}
}

// =============================================================================
// AUTHENTICATION
// =============================================================================

function checkAuth() {
  const stored = localStorage.getItem('it_inventory_admin_user');
  if (stored) {
    try {
      currentUser = JSON.parse(stored);
      document.getElementById('loginOverlay').classList.add('hidden');
      document.getElementById('userFullName').textContent = currentUser.full_name || 'IT Administrator';
      document.getElementById('userRole').textContent = (currentUser.role || 'Admin').toUpperCase();
    } catch (e) {
      showLoginModal();
    }
  } else {
    showLoginModal();
  }
}

function showLoginModal() {
  document.getElementById('loginOverlay').classList.remove('hidden');
}

async function handleLogin(e) {
  e.preventDefault();
  const email = document.getElementById('loginEmail').value.trim();
  const password = document.getElementById('loginPassword').value.trim();
  const errDiv = document.getElementById('loginError');
  errDiv.classList.add('hidden');

  try {
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password })
    });
    const data = await res.json();

    if (data.success && data.user) {
      currentUser = data.user;
      localStorage.setItem('it_inventory_admin_user', JSON.stringify(currentUser));
      document.getElementById('loginOverlay').classList.add('hidden');
      document.getElementById('userFullName').textContent = currentUser.full_name || 'System Administrator';
      document.getElementById('userRole').textContent = (currentUser.role || 'Admin').toUpperCase();
      loadDashboardData();
    } else {
      errDiv.textContent = data.message || 'Invalid email or password';
      errDiv.classList.remove('hidden');
    }
  } catch (err) {
    errDiv.textContent = 'Connection error: ' + err.message;
    errDiv.classList.remove('hidden');
  }
}

function handleLogout() {
  localStorage.removeItem('it_inventory_admin_user');
  currentUser = null;
  showLoginModal();
}

// =============================================================================
// ACCOUNT SETTINGS & PASSWORD CHANGE
// =============================================================================

function openChangePasswordModal() {
  const modal = document.getElementById('changePasswordModal');
  if (!modal) return;
  document.getElementById('changePasswordForm').reset();
  const errDiv = document.getElementById('pwdError');
  const succDiv = document.getElementById('pwdSuccess');
  if (errDiv) errDiv.classList.add('hidden');
  if (succDiv) succDiv.classList.add('hidden');

  const emailEl = document.getElementById('settingsUserEmail');
  if (emailEl && currentUser) {
    emailEl.textContent = currentUser.email || 'admin@itinventory.ph';
  }
  modal.classList.remove('hidden');
}

async function handleChangePassword(e) {
  e.preventDefault();
  const currentPassword = document.getElementById('pwdCurrent').value;
  const newPassword = document.getElementById('pwdNew').value;
  const confirmPassword = document.getElementById('pwdConfirm').value;
  const errDiv = document.getElementById('pwdError');
  const succDiv = document.getElementById('pwdSuccess');
  const submitBtn = document.getElementById('btnSubmitPassword');

  errDiv.classList.add('hidden');
  succDiv.classList.add('hidden');

  if (newPassword !== confirmPassword) {
    errDiv.textContent = 'New password and confirmation do not match.';
    errDiv.classList.remove('hidden');
    return;
  }

  if (newPassword.length < 6) {
    errDiv.textContent = 'New password must be at least 6 characters long.';
    errDiv.classList.remove('hidden');
    return;
  }

  submitBtn.disabled = true;
  submitBtn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Updating...';

  try {
    const res = await fetch('/api/auth/change-password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        user_id: currentUser ? currentUser.user_id : null,
        email: currentUser ? currentUser.email : 'admin@itinventory.ph',
        current_password: currentPassword,
        new_password: newPassword
      })
    });

    const data = await res.json();
    if (data.success) {
      succDiv.textContent = 'Password successfully updated in TiDB Cloud!';
      succDiv.classList.remove('hidden');
      document.getElementById('changePasswordForm').reset();
      showToast('Password updated successfully');
      setTimeout(() => {
        closeModal('changePasswordModal');
      }, 1500);
    } else {
      errDiv.textContent = data.message || 'Failed to update password.';
      errDiv.classList.remove('hidden');
    }
  } catch (err) {
    errDiv.textContent = 'Network error: ' + err.message;
    errDiv.classList.remove('hidden');
  } finally {
    submitBtn.disabled = false;
    submitBtn.innerHTML = '<i class="fa-solid fa-floppy-disk"></i> Save New Password';
  }
}

// =============================================================================
// REAL-TIME SYNCHRONIZATION (Server-Sent Events)
// =============================================================================

function initRealtimeSync() {
  if (sseConnection) {
    sseConnection.close();
  }

  try {
    sseConnection = new EventSource('/api/live-stream');

    sseConnection.onopen = () => {
      document.getElementById('sidebarSyncTitle').textContent = 'Live Sync Active';
    };

    // Real-time scan incoming from Android app
    sseConnection.addEventListener('scan_update', (e) => {
      const data = JSON.parse(e.data);
      console.log('[SSE] Live scan event:', data);
      handleIncomingRealtimeScan(data);
    });

    // Equipment item updated
    sseConnection.addEventListener('item_updated', (e) => {
      const data = JSON.parse(e.data);
      updateEquipmentInState(data.item);
      if (data.stats) updateStatsCards(data.stats);
    });

    // Equipment item deleted
    sseConnection.addEventListener('item_deleted', (e) => {
      const data = JSON.parse(e.data);
      allEquipment = allEquipment.filter(i => i.item_id !== data.item_id && i.asset_tag !== data.asset_tag);
      renderInventoryTable();
      renderCategoryDistribution();
      if (data.stats) updateStatsCards(data.stats);
    });

    sseConnection.onerror = () => {
      document.getElementById('sidebarSyncTitle').textContent = 'Reconnecting...';
    };
  } catch (err) {
    console.warn('[SSE] EventSource init failed', err);
  }
}

function handleIncomingRealtimeScan(data) {
  const { item, log, stats } = data;

  // 1. Play audio chime
  playChime();

  // 2. Prepend scan log to live tables
  if (log) {
    allScanLogs.unshift(log);
    renderLiveScanTables(true);
  }

  // 3. Update or add equipment in master catalog
  if (item) {
    updateEquipmentInState(item);
  }

  // 4. Update stats cards
  if (stats) {
    updateStatsCards(stats);
  } else {
    refreshStats();
  }

  // 5. Toast notification
  showToast(`Mobile Scan: ${log ? log.asset_tag : item.asset_tag} (${log ? log.action_type : 'Synced'})`);
}

function updateEquipmentInState(item) {
  const index = allEquipment.findIndex(i => i.item_id === item.item_id || i.asset_tag === item.asset_tag);
  if (index !== -1) {
    allEquipment[index] = item;
  } else {
    allEquipment.unshift(item);
  }
  renderInventoryTable();
  renderCategoryDistribution();
}

// =============================================================================
// DATA FETCHING
// =============================================================================

async function loadDashboardData() {
  await Promise.all([
    fetchStats(),
    fetchEquipment(),
    fetchScanLogs()
  ]);
}

async function refreshData() {
  await loadDashboardData();
  showToast('Dashboard synchronized with database');
}

async function fetchStats() {
  try {
    const res = await fetch('/api/stats');
    const data = await res.json();
    if (data.success && data.stats) {
      updateStatsCards(data.stats);
    }
  } catch (e) {
    console.error('Error fetching stats:', e);
  }
}

function updateStatsCards(stats) {
  // Dashboard view cards
  document.getElementById('dashStatTotal').textContent = stats.total ?? 0;
  document.getElementById('dashStatAvailable').textContent = stats.available ?? 0;
  document.getElementById('dashStatAssigned').textContent = stats.assigned ?? 0;
  document.getElementById('dashStatMaintenance').textContent = stats.maintenance ?? 0;
  document.getElementById('dashStatDefective').textContent = stats.defective ?? 0;
  document.getElementById('dashStatScannedToday').textContent = stats.scanned_today ?? 0;

  // Sidebar badge & quick counts
  document.getElementById('sidebarInventoryBadge').textContent = stats.total ?? 0;
  document.getElementById('quickCountAvailable').textContent = stats.available ?? 0;
  document.getElementById('quickCountAssigned').textContent = stats.assigned ?? 0;
  document.getElementById('quickCountMaintenance').textContent = stats.maintenance ?? 0;
  document.getElementById('quickCountDefective').textContent = stats.defective ?? 0;

  // Header quick badge
  document.getElementById('topScanCount').textContent = `${stats.scanned_today ?? 0} scans`;
}

function refreshStats() {
  const today = new Date().toDateString();
  const scannedToday = allScanLogs.filter(l => new Date(l.timestamp).toDateString() === today).length;

  updateStatsCards({
    total: allEquipment.length,
    available: allEquipment.filter(i => i.status === 'Available').length,
    assigned: allEquipment.filter(i => i.status === 'Assigned').length,
    maintenance: allEquipment.filter(i => i.status === 'Maintenance').length,
    defective: allEquipment.filter(i => i.status === 'Defective').length,
    scanned_today: scannedToday
  });
}

async function fetchEquipment() {
  try {
    const res = await fetch('/api/equipment');
    const data = await res.json();
    if (data.success && data.items) {
      allEquipment = data.items;
      renderInventoryTable();
      renderCategoryDistribution();
      initStudioDropdown();
      refreshStats();
    }
  } catch (e) {
    console.error('Error fetching equipment:', e);
  }
}

async function fetchScanLogs() {
  try {
    const res = await fetch('/api/scan-logs?limit=50');
    const data = await res.json();
    if (data.success && data.logs) {
      allScanLogs = data.logs;
      renderLiveScanTables();
    }
  } catch (e) {
    console.error('Error fetching scan logs:', e);
  }
}

// =============================================================================
// CATEGORY HEALTH BREAKDOWN
// =============================================================================

function renderCategoryDistribution() {
  const container = document.getElementById('categoryDistributionList');
  if (!container) return;

  const categories = ['Laptop', 'Desktop', 'Monitor', 'Network', 'Peripheral'];
  const total = allEquipment.length || 1;

  const icons = {
    'Laptop': 'fa-solid fa-laptop text-blue-600',
    'Desktop': 'fa-solid fa-desktop text-indigo-600',
    'Monitor': 'fa-solid fa-display text-sky-600',
    'Network': 'fa-solid fa-network-wired text-purple-600',
    'Peripheral': 'fa-solid fa-keyboard text-slate-600'
  };

  const colors = {
    'Laptop': 'bg-blue-600',
    'Desktop': 'bg-indigo-600',
    'Monitor': 'bg-sky-500',
    'Network': 'bg-purple-600',
    'Peripheral': 'bg-slate-500'
  };

  container.innerHTML = categories.map(cat => {
    const count = allEquipment.filter(i => i.category === cat).length;
    const pct = Math.round((count / total) * 100);

    return `
      <div>
        <div class="flex items-center justify-between mb-1 text-xs">
          <span class="font-bold text-slate-700 flex items-center gap-1.5">
            <i class="${icons[cat]} w-4 text-center"></i> ${cat}s
          </span>
          <span class="text-slate-500 font-mono text-[11px]">
            <strong class="text-slate-800">${count}</strong> (${pct}%)
          </span>
        </div>
        <div class="w-full bg-slate-100 rounded-full h-1.5 overflow-hidden">
          <div class="${colors[cat]} h-1.5 rounded-full transition-all duration-500" style="width: ${pct}%"></div>
        </div>
      </div>
    `;
  }).join('');
}

// =============================================================================
// LIVE SCAN FEED & AUDIT TRAIL RENDERING
// =============================================================================

function renderLiveScanTables(highlightFirst = false) {
  renderDashboardLiveFeed(highlightFirst);
  renderFullLogsTable(highlightFirst);
}

function renderDashboardLiveFeed(highlightFirst = false) {
  const tbody = document.getElementById('dashLiveScanTableBody');
  const countBadge = document.getElementById('dashLiveCountBadge');
  if (!tbody) return;

  countBadge.textContent = `${allScanLogs.length} scans`;

  if (!allScanLogs || allScanLogs.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="6" class="py-8 text-center text-slate-400">
          No mobile scans recorded yet. Scan an equipment tag using the Android App!
        </td>
      </tr>
    `;
    return;
  }

  // Show top 8 on dashboard
  const displayLogs = allScanLogs.slice(0, 8);

  tbody.innerHTML = displayLogs.map((log, index) => {
    const isNew = highlightFirst && index === 0;
    const timeFormatted = formatTime(log.timestamp);
    const actionBadge = getActionBadge(log.action_type);

    return `
      <tr class="hover:bg-slate-50 transition border-b border-slate-100 ${isNew ? 'live-pulse-highlight' : ''}">
        <td class="py-2.5 px-4 font-mono text-slate-400 whitespace-nowrap text-[11px]">${timeFormatted}</td>
        <td class="py-2.5 px-4 whitespace-nowrap">
          <span class="asset-tag-pill font-mono">
            <i class="fa-solid fa-qrcode text-[#0EA5E9] text-[10px]"></i>
            ${escapeHtml(log.asset_tag)}
          </span>
        </td>
        <td class="py-2.5 px-4 font-semibold text-slate-800 max-w-[180px] truncate">${escapeHtml(log.item_name || 'Equipment')}</td>
        <td class="py-2.5 px-4 whitespace-nowrap">${actionBadge}</td>
        <td class="py-2.5 px-4 text-slate-600 whitespace-nowrap text-[11px]">
          <i class="fa-solid fa-location-dot text-slate-400 mr-1"></i>${escapeHtml(log.location || 'IT Storage')}
        </td>
        <td class="py-2.5 px-4 text-slate-700 whitespace-nowrap font-medium text-[11px]">
          <i class="fa-solid fa-user-circle text-slate-400 mr-1"></i>${escapeHtml(log.scanned_by || 'Staff')}
        </td>
      </tr>
    `;
  }).join('');
}

function renderFullLogsTable(highlightFirst = false) {
  const tbody = document.getElementById('fullAuditLogsTableBody');
  const actionFilter = document.getElementById('logActionFilter')?.value || 'All';
  if (!tbody) return;

  let filtered = allScanLogs;
  if (actionFilter !== 'All') {
    filtered = filtered.filter(l => l.action_type === actionFilter);
  }

  if (filtered.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="7" class="py-12 text-center text-slate-400">
          No audit logs recorded for the selected filter.
        </td>
      </tr>
    `;
    return;
  }

  tbody.innerHTML = filtered.map((log, index) => {
    const isNew = highlightFirst && index === 0;
    const timeFormatted = formatTime(log.timestamp);
    const actionBadge = getActionBadge(log.action_type);

    return `
      <tr class="hover:bg-slate-50 transition border-b border-slate-100 ${isNew ? 'live-pulse-highlight' : ''}">
        <td class="py-3 px-4 font-mono text-slate-500 whitespace-nowrap">${timeFormatted}</td>
        <td class="py-3 px-4 whitespace-nowrap">
          <span class="asset-tag-pill font-mono font-bold">
            <i class="fa-solid fa-barcode text-slate-400 text-[10px]"></i>
            ${escapeHtml(log.asset_tag)}
          </span>
        </td>
        <td class="py-3 px-4 font-bold text-slate-800">${escapeHtml(log.item_name || 'Equipment')}</td>
        <td class="py-3 px-4 whitespace-nowrap">${actionBadge}</td>
        <td class="py-3 px-4 text-slate-700 font-medium whitespace-nowrap">
          <i class="fa-solid fa-user text-slate-400 mr-1"></i>${escapeHtml(log.scanned_by || 'Staff')}
        </td>
        <td class="py-3 px-4 text-slate-600 whitespace-nowrap">
          <i class="fa-solid fa-location-dot text-slate-400 mr-1"></i>${escapeHtml(log.location || 'N/A')}
        </td>
        <td class="py-3 px-4 text-slate-500 max-w-xs truncate" title="${escapeHtml(log.remarks || '')}">
          ${escapeHtml(log.remarks || '-')}
        </td>
      </tr>
    `;
  }).join('');
}

function getActionBadge(action) {
  const act = action || 'Audit Verify';
  if (act.includes('Defective')) {
    return `<span class="badge-pill badge-defective"><i class="fa-solid fa-triangle-exclamation"></i>${act}</span>`;
  }
  if (act.includes('Check-Out') || act.includes('Assign')) {
    return `<span class="badge-pill badge-assigned"><i class="fa-solid fa-arrow-right-from-bracket"></i>${act}</span>`;
  }
  if (act.includes('Check-In')) {
    return `<span class="badge-pill badge-available"><i class="fa-solid fa-arrow-right-to-bracket"></i>${act}</span>`;
  }
  if (act.includes('New Registration')) {
    return `<span class="badge-pill badge-available"><i class="fa-solid fa-plus"></i>${act}</span>`;
  }
  return `<span class="badge-pill badge-available"><i class="fa-solid fa-clipboard-check"></i>${act}</span>`;
}

// =============================================================================
// MASTER EQUIPMENT INVENTORY TABLE & PAGINATION
// =============================================================================

function applyFilters() {
  currentFilter.search = (document.getElementById('inventorySearchInput').value || '').toLowerCase().trim();
  currentFilter.category = document.getElementById('categoryFilter').value;
  currentFilter.status = document.getElementById('statusFilter').value;
  currentPage = 1;
  renderInventoryTable();
}

function renderInventoryTable() {
  const tbody = document.getElementById('inventoryTableBody');
  if (!tbody) return;

  // Filter dataset
  let filtered = allEquipment.filter(item => {
    if (currentFilter.search) {
      const q = currentFilter.search;
      const match = (
        (item.asset_tag && item.asset_tag.toLowerCase().includes(q)) ||
        (item.item_name && item.item_name.toLowerCase().includes(q)) ||
        (item.serial_number && item.serial_number.toLowerCase().includes(q)) ||
        (item.assigned_to && item.assigned_to.toLowerCase().includes(q)) ||
        (item.location && item.location.toLowerCase().includes(q))
      );
      if (!match) return false;
    }
    if (currentFilter.category !== 'All' && item.category !== currentFilter.category) {
      return false;
    }
    if (currentFilter.status !== 'All' && item.status !== currentFilter.status) {
      return false;
    }
    return true;
  });

  const totalCount = filtered.length;
  const totalPages = Math.ceil(totalCount / pageSize) || 1;

  if (currentPage > totalPages) currentPage = totalPages;
  if (currentPage < 1) currentPage = 1;

  const startIdx = (currentPage - 1) * pageSize;
  const pageItems = filtered.slice(startIdx, startIdx + pageSize);

  // Update pagination indicators
  document.getElementById('showingStart').textContent = totalCount === 0 ? 0 : startIdx + 1;
  document.getElementById('showingEnd').textContent = Math.min(startIdx + pageSize, totalCount);
  document.getElementById('showingTotal').textContent = totalCount;
  document.getElementById('pageIndicator').textContent = `Page ${currentPage} of ${totalPages}`;

  document.getElementById('btnPrevPage').disabled = currentPage <= 1;
  document.getElementById('btnNextPage').disabled = currentPage >= totalPages;

  if (pageItems.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="9" class="py-12 text-center text-slate-400">
          <i class="fa-solid fa-magnifying-glass text-2xl text-slate-300 mb-2 block"></i>
          No equipment items match the specified search or filter criteria.
        </td>
      </tr>
    `;
    return;
  }

  tbody.innerHTML = pageItems.map(item => {
    const statusBadge = getStatusBadge(item.status);
    const categoryIcon = getCategoryIcon(item.category);
    const lastScanFormatted = formatTime(item.last_scanned_at);

    return `
      <tr class="hover:bg-slate-50 transition border-b border-slate-100">
        <td class="py-3 px-4 whitespace-nowrap">
          <span class="asset-tag-pill font-mono">
            <i class="fa-solid fa-qrcode text-[#0EA5E9] text-[11px]"></i>
            ${escapeHtml(item.asset_tag)}
          </span>
        </td>
        <td class="py-3 px-4 font-bold text-slate-900">
          ${escapeHtml(item.item_name)}
        </td>
        <td class="py-3 px-4 whitespace-nowrap">
          <span class="badge-category">
            <i class="${categoryIcon} text-slate-500"></i> ${escapeHtml(item.category)}
          </span>
        </td>
        <td class="py-3 px-4 font-mono text-slate-500 whitespace-nowrap text-[11px]">
          ${escapeHtml(item.serial_number || 'N/A')}
        </td>
        <td class="py-3 px-4 whitespace-nowrap">
          ${statusBadge}
        </td>
        <td class="py-3 px-4 text-slate-700 whitespace-nowrap">
          ${item.assigned_to 
            ? `<span class="inline-flex items-center gap-1.5 font-medium"><i class="fa-solid fa-circle-user text-slate-400"></i>${escapeHtml(item.assigned_to)}</span>` 
            : '<span class="text-slate-300 italic">Unassigned</span>'}
        </td>
        <td class="py-3 px-4 text-slate-600 whitespace-nowrap">
          <i class="fa-solid fa-location-dot text-slate-400 mr-1"></i>${escapeHtml(item.location || 'IT Storage')}
        </td>
        <td class="py-3 px-4 text-slate-500 whitespace-nowrap text-[11px]">
          <div class="font-medium">${lastScanFormatted}</div>
          <div class="text-[10px] text-slate-400">${escapeHtml(item.last_scanned_by || 'System')}</div>
        </td>
        <td class="py-3 px-4 text-center whitespace-nowrap">
          <div class="inline-flex items-center gap-1.5">
            <button onclick="openQrModal('${item.item_id}')" title="Print QR Sticker" 
              class="w-7 h-7 rounded-lg bg-sky-50 text-[#0EA5E9] hover:bg-[#0EA5E9] hover:text-white transition flex items-center justify-center">
              <i class="fa-solid fa-qrcode text-xs"></i>
            </button>
            <button onclick="openEditModal('${item.item_id}')" title="Edit Equipment" 
              class="w-7 h-7 rounded-lg bg-slate-100 text-slate-600 hover:bg-[#1E40AF] hover:text-white transition flex items-center justify-center">
              <i class="fa-solid fa-pen text-xs"></i>
            </button>
            <button onclick="deleteEquipment('${item.item_id}', '${escapeHtml(item.asset_tag)}')" title="Delete Equipment" 
              class="w-7 h-7 rounded-lg bg-red-50 text-red-600 hover:bg-red-600 hover:text-white transition flex items-center justify-center">
              <i class="fa-solid fa-trash-can text-xs"></i>
            </button>
          </div>
        </td>
      </tr>
    `;
  }).join('');
}

function changePage(delta) {
  currentPage += delta;
  renderInventoryTable();
}

function getStatusBadge(status) {
  const s = status || 'Available';
  switch (s) {
    case 'Available':
    case 'Working':
      return `<span class="badge-pill badge-available">Available</span>`;
    case 'Assigned':
    case 'In Use':
      return `<span class="badge-pill badge-assigned">Assigned</span>`;
    case 'Maintenance':
      return `<span class="badge-pill badge-maintenance">Maintenance</span>`;
    case 'Defective':
    case 'Disposed':
      return `<span class="badge-pill badge-defective">Defective</span>`;
    default:
      return `<span class="badge-pill badge-available">${escapeHtml(s)}</span>`;
  }
}

function getCategoryIcon(cat) {
  switch (cat) {
    case 'Laptop': return 'fa-solid fa-laptop';
    case 'Desktop': return 'fa-solid fa-desktop';
    case 'Monitor': return 'fa-solid fa-display';
    case 'Network': return 'fa-solid fa-network-wired';
    case 'Peripheral': return 'fa-solid fa-keyboard';
    default: return 'fa-solid fa-microchip';
  }
}

// =============================================================================
// MODALS: ADD / EDIT / DELETE
// =============================================================================

function openAddModal() {
  document.getElementById('modalTitle').textContent = 'Add New IT Equipment';
  document.getElementById('equipmentForm').reset();
  document.getElementById('formItemId').value = '';

  const nextNum = String(allEquipment.length + 1).padStart(3, '0');
  document.getElementById('formAssetTag').value = `IT-LAP-${nextNum}`;
  document.getElementById('formLocation').value = 'IT Storage';
  document.getElementById('formStatus').value = 'Available';

  document.getElementById('equipmentModal').classList.remove('hidden');
}

function openEditModal(itemId) {
  const item = allEquipment.find(i => i.item_id === itemId);
  if (!item) return;

  document.getElementById('modalTitle').textContent = `Edit Equipment (${item.asset_tag})`;
  document.getElementById('formItemId').value = item.item_id;
  document.getElementById('formAssetTag').value = item.asset_tag;
  document.getElementById('formItemName').value = item.item_name;
  document.getElementById('formCategory').value = item.category || 'Laptop';
  document.getElementById('formSerialNumber').value = item.serial_number || '';
  document.getElementById('formStatus').value = item.status || 'Available';
  document.getElementById('formAssignedTo').value = item.assigned_to || '';
  document.getElementById('formLocation').value = item.location || '';

  document.getElementById('equipmentModal').classList.remove('hidden');
}

function closeModal(modalId) {
  document.getElementById(modalId).classList.add('hidden');
}

async function handleSaveEquipment(e) {
  e.preventDefault();
  const itemId = document.getElementById('formItemId').value;
  const isEdit = !!itemId;

  const payload = {
    item_id: itemId || `EQ-${Date.now().toString(36).toUpperCase()}`,
    asset_tag: document.getElementById('formAssetTag').value.trim(),
    item_name: document.getElementById('formItemName').value.trim(),
    category: document.getElementById('formCategory').value,
    serial_number: document.getElementById('formSerialNumber').value.trim() || 'N/A',
    status: document.getElementById('formStatus').value,
    assigned_to: document.getElementById('formAssignedTo').value.trim(),
    location: document.getElementById('formLocation').value.trim(),
    last_scanned_by: currentUser ? currentUser.full_name : 'Admin'
  };

  try {
    const url = isEdit ? `/api/equipment/${itemId}` : '/api/equipment';
    const method = isEdit ? 'PUT' : 'POST';

    const res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    const data = await res.json();

    if (data.success) {
      closeModal('equipmentModal');
      showToast(isEdit ? 'Equipment updated successfully' : 'New equipment added');
      await loadDashboardData();
    } else {
      alert('Error: ' + data.message);
    }
  } catch (err) {
    alert('Failed to save equipment: ' + err.message);
  }
}

async function deleteEquipment(itemId, assetTag) {
  if (!confirm(`Are you sure you want to permanently delete equipment item [${assetTag}]?`)) {
    return;
  }

  try {
    const res = await fetch(`/api/equipment/${itemId}`, { method: 'DELETE' });
    const data = await res.json();
    if (data.success) {
      showToast(`Deleted ${assetTag}`);
      allEquipment = allEquipment.filter(i => i.item_id !== itemId);
      renderInventoryTable();
      renderCategoryDistribution();
      refreshStats();
    } else {
      alert('Error: ' + data.message);
    }
  } catch (err) {
    alert('Failed to delete item: ' + err.message);
  }
}

// =============================================================================
// QR CODE STICKER MODAL & PRINTING (WITH LABORATORY NAME & ITEM NAME)
// =============================================================================

function openQrModal(itemId) {
  const item = allEquipment.find(i => i.item_id === itemId);
  if (!item) return;

  let labName = 'Computer Laboratory 1';
  let deskLoc = item.location || 'Station 01';
  if (item.location) {
    if (item.location.toLowerCase().includes('lab') || 
        item.location.toLowerCase().includes('room') || 
        item.location.toLowerCase().includes('vault')) {
      labName = item.location;
    }
  }

  const labInput = document.getElementById('qrModalInputLab');
  const nameInput = document.getElementById('qrModalInputName');
  if (labInput) labInput.value = labName;
  if (nameInput) nameInput.value = item.item_name;

  document.getElementById('stickerAssetTag').textContent = item.asset_tag;
  document.getElementById('stickerLabName').textContent = labName.toUpperCase();
  document.getElementById('stickerItemName').textContent = item.item_name;
  document.getElementById('stickerCategory').textContent = (item.category || 'EQUIPMENT').toUpperCase();
  document.getElementById('stickerSerial').textContent = item.serial_number || 'N/A';
  document.getElementById('stickerLocation').textContent = deskLoc;

  const qrContainer = document.getElementById('stickerQrContainer');
  qrContainer.innerHTML = '';

  if (typeof QRCode !== 'undefined') {
    currentQrInstance = new QRCode(qrContainer, {
      text: item.asset_tag,
      width: 88,
      height: 88,
      colorDark: '#0F172A',
      colorLight: '#FFFFFF',
      correctLevel: QRCode.CorrectLevel.H
    });
  }

  document.getElementById('qrModal').classList.remove('hidden');
}

function handleModalQuickUpdate() {
  const labVal = document.getElementById('qrModalInputLab')?.value || 'COMPUTER LABORATORY 1';
  const nameVal = document.getElementById('qrModalInputName')?.value || 'IT Equipment Item';

  const stickerLabEl = document.getElementById('stickerLabName');
  const stickerNameEl = document.getElementById('stickerItemName');

  if (stickerLabEl) stickerLabEl.textContent = labVal.toUpperCase();
  if (stickerNameEl) stickerNameEl.textContent = nameVal;
}

function printSticker() {
  window.print();
}

// =============================================================================
// QR TAG STUDIO (MANUAL INPUT + PRE-FILL + LABORATORY & ITEM NAME)
// =============================================================================

function initStudioDropdown() {
  const select = document.getElementById('studioSelectEquipment');
  if (!select) return;

  const options = allEquipment.map(i => {
    return `<option value="${i.item_id}">${i.asset_tag} - ${i.item_name} (${i.category})</option>`;
  }).join('');

  select.innerHTML = '<option value="">-- Or choose an existing item to pre-fill --</option>' + options;

  // Initial trigger from current manual fields
  handleStudioManualUpdate();
}

function handleStudioSelectChange(e) {
  const itemId = e.target.value;
  if (!itemId) return;

  const item = allEquipment.find(i => i.item_id === itemId);
  if (item) {
    document.getElementById('studioManualTag').value = item.asset_tag;
    document.getElementById('studioManualName').value = item.item_name;
    document.getElementById('studioManualCategory').value = item.category || 'Laptop';
    document.getElementById('studioManualSerial').value = item.serial_number || '';
    
    let labName = 'Computer Laboratory 1';
    let deskLoc = item.location || 'Station 01';
    if (item.location) {
      if (item.location.toLowerCase().includes('lab') || 
          item.location.toLowerCase().includes('room') || 
          item.location.toLowerCase().includes('vault')) {
        labName = item.location;
      }
    }
    const labInput = document.getElementById('studioManualLab');
    if (labInput) labInput.value = labName;
    document.getElementById('studioManualLoc').value = deskLoc;
    
    handleStudioManualUpdate();
  }
}

function handleStudioManualUpdate() {
  const assetTag = (document.getElementById('studioManualTag')?.value || 'IT-LAP-001').trim();
  const labName = (document.getElementById('studioManualLab')?.value || 'Computer Laboratory 1').trim();
  const itemName = (document.getElementById('studioManualName')?.value || 'Dell Latitude 5420').trim();
  const category = (document.getElementById('studioManualCategory')?.value || 'Laptop');
  const serial = (document.getElementById('studioManualSerial')?.value || 'DL5420-9941X').trim();
  const location = (document.getElementById('studioManualLoc')?.value || 'Station 01').trim();

  renderStudioPreview({
    asset_tag: assetTag || 'IT-TAG-001',
    laboratory_name: labName || 'Computer Laboratory 1',
    item_name: itemName || 'IT Equipment Item',
    category: category,
    serial_number: serial || 'N/A',
    location: location || 'Station 01'
  });
}

function renderStudioPreview(item) {
  const tagEl = document.getElementById('studioPreviewTag');
  const labEl = document.getElementById('studioPreviewLab');
  const nameEl = document.getElementById('studioPreviewName');
  const catEl = document.getElementById('studioPreviewCategory');
  const serialEl = document.getElementById('studioPreviewSerial');
  const deskEl = document.getElementById('studioPreviewDeskLoc');
  const container = document.getElementById('studioQrContainer');

  if (!tagEl || !container) return;

  tagEl.textContent = item.asset_tag;
  if (labEl) labEl.textContent = (item.laboratory_name || 'COMPUTER LABORATORY 1').toUpperCase();
  if (nameEl) nameEl.textContent = item.item_name;
  if (catEl) catEl.textContent = (item.category || 'EQUIPMENT').toUpperCase();
  if (serialEl) serialEl.textContent = item.serial_number || 'N/A';
  if (deskEl) deskEl.textContent = item.location || 'Station 01';

  container.innerHTML = '';

  if (typeof QRCode !== 'undefined') {
    studioQrInstance = new QRCode(container, {
      text: item.asset_tag,
      width: 88,
      height: 88,
      colorDark: '#0F172A',
      colorLight: '#FFFFFF',
      correctLevel: QRCode.CorrectLevel.H
    });
  }
}

function printStudioSticker() {
  window.print();
}

// =============================================================================
// EXPORT TO CSV
// =============================================================================

function exportToCsv() {
  if (!allEquipment || allEquipment.length === 0) {
    alert('No equipment records to export.');
    return;
  }

  const headers = ['Asset Tag', 'Equipment Name', 'Category', 'Serial Number', 'Status', 'Assigned To', 'Location', 'Last Scanned At', 'Last Scanned By'];
  
  const rows = allEquipment.map(item => [
    `"${item.asset_tag || ''}"`,
    `"${(item.item_name || '').replace(/"/g, '""')}"`,
    `"${item.category || ''}"`,
    `"${item.serial_number || ''}"`,
    `"${item.status || ''}"`,
    `"${(item.assigned_to || '').replace(/"/g, '""')}"`,
    `"${(item.location || '').replace(/"/g, '""')}"`,
    `"${item.last_scanned_at || ''}"`,
    `"${item.last_scanned_by || ''}"`
  ]);

  const csvContent = '\uFEFF' + [headers.join(','), ...rows.map(r => r.join(','))].join('\r\n');
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  
  const link = document.createElement('a');
  link.setAttribute('href', url);
  link.setAttribute('download', `IT_Inventory_Export_${new Date().toISOString().split('T')[0]}.csv`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);

  showToast('Inventory exported to CSV successfully');
}

// =============================================================================
// UTILITY HELPERS
// =============================================================================

function formatTime(isoString) {
  if (!isoString) return '-';
  try {
    const d = new Date(isoString);
    if (isNaN(d.getTime())) return '-';
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  } catch (e) {
    return '-';
  }
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

function showToast(message) {
  const existing = document.getElementById('appToast');
  if (existing) existing.remove();

  const toast = document.createElement('div');
  toast.id = 'appToast';
  toast.className = 'fixed bottom-5 right-5 z-50 bg-[#1E40AF] text-white text-xs font-bold px-4 py-3 rounded-xl shadow-2xl flex items-center gap-2 border border-blue-400/30 transform transition-all duration-300';
  toast.innerHTML = `<i class="fa-solid fa-circle-check text-[#0EA5E9]"></i> <span>${escapeHtml(message)}</span>`;

  document.body.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0';
    setTimeout(() => toast.remove(), 300);
  }, 3500);
}

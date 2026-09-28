/**
 * BloodLink Dashboard - Main JavaScript Application
 * Manages donor registry, blood group inventory counts, search & date filters,
 * 90-day cooldown tracking, and donor registration.
 */

// API Configuration
const API_BASE = '/BloodLink';

// Application State
let allDonors = [];
let bloodGroupCounts = {};
let selectedBloodGroup = '';

// DOM Elements
const statTotalDonors = document.getElementById('statTotalDonors');
const statAvailableDonors = document.getElementById('statAvailableDonors');
const statCooldownDonors = document.getElementById('statCooldownDonors');
const statAvailabilityRate = document.getElementById('statAvailabilityRate');

const bloodChipsGrid = document.getElementById('bloodChipsGrid');
const donorsTableBody = document.getElementById('donorsTableBody');
const emptyState = document.getElementById('emptyState');
const tableResultCount = document.getElementById('tableResultCount');
const activeFilterBadge = document.getElementById('activeFilterBadge');

const searchTextInput = document.getElementById('searchTextInput');
const filterBloodGroup = document.getElementById('filterBloodGroup');
const filterAvailability = document.getElementById('filterAvailability');
const filterDonationDate = document.getElementById('filterDonationDate');

const btnApplyFilter = document.getElementById('btnApplyFilter');
const btnResetFilter = document.getElementById('btnResetFilter');
const btnEmptyReset = document.getElementById('btnEmptyReset');
const btnRefreshData = document.getElementById('btnRefreshData');

const registerModal = document.getElementById('registerModal');
const btnOpenRegisterModal = document.getElementById('btnOpenRegisterModal');
const btnCloseModal = document.getElementById('btnCloseModal');
const btnCancelModal = document.getElementById('btnCancelModal');
const donorForm = document.getElementById('donorForm');
const toastContainer = document.getElementById('toastContainer');

// ============================================================================
// Initialization
// ============================================================================
document.addEventListener('DOMContentLoaded', () => {
  setupEventListeners();
  loadDashboardData();
});

function setupEventListeners() {
  // Filter controls
  btnApplyFilter.addEventListener('click', applyFilters);
  btnResetFilter.addEventListener('click', resetFilters);
  btnEmptyReset.addEventListener('click', resetFilters);
  btnRefreshData.addEventListener('click', () => {
    loadDashboardData();
    showToast('Dashboard data refreshed', 'success');
  });

  // Real-time search as user types
  searchTextInput.addEventListener('input', debounce(applyFilters, 250));
  filterBloodGroup.addEventListener('change', () => {
    selectedBloodGroup = filterBloodGroup.value;
    updateBloodChipsSelection();
    applyFilters();
  });
  filterAvailability.addEventListener('change', applyFilters);
  filterDonationDate.addEventListener('change', applyFilters);

  // Modal actions
  btnOpenRegisterModal.addEventListener('click', openRegisterModal);
  btnCloseModal.addEventListener('click', closeRegisterModal);
  btnCancelModal.addEventListener('click', closeRegisterModal);
  registerModal.addEventListener('click', (e) => {
    if (e.target === registerModal) closeRegisterModal();
  });

  // Form submission
  donorForm.addEventListener('submit', handleDonorRegistration);
}

// ============================================================================
// Data Loading
// ============================================================================
async function loadDashboardData() {
  await Promise.all([fetchBloodGroupCounts(), fetchDonors()]);
}

async function fetchBloodGroupCounts() {
  try {
    const res = await fetch(`${API_BASE}/count-by-blood-group`);
    if (!res.ok) throw new Error('Failed to load blood group statistics');
    bloodGroupCounts = await res.json();
    renderBloodChips();
  } catch (err) {
    console.error('Error fetching blood group counts:', err);
    showToast('Could not load blood group stats', 'error');
  }
}

async function fetchDonors() {
  try {
    const res = await fetch(`${API_BASE}/donors`);
    if (!res.ok) throw new Error('Failed to fetch donors list');
    allDonors = await res.json();
    updateMetrics();
    applyFilters();
  } catch (err) {
    console.error('Error fetching donors:', err);
    showToast('Could not load donors directory', 'error');
  }
}

// ============================================================================
// Metrics & Inventory Cards
// ============================================================================
function updateMetrics() {
  const total = allDonors.length;
  const available = allDonors.filter(d => isDonorAvailable(d)).length;
  const inCooldown = total - available;
  const rate = total > 0 ? Math.round((available / total) * 100) : 0;

  statTotalDonors.textContent = total;
  statAvailableDonors.textContent = available;
  statCooldownDonors.textContent = inCooldown;
  statAvailabilityRate.textContent = `${rate}%`;
}

function renderBloodChips() {
  const standardGroups = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'];
  bloodChipsGrid.innerHTML = '';

  standardGroups.forEach(group => {
    const count = bloodGroupCounts[group] || 0;
    const chip = document.createElement('div');
    chip.className = `blood-chip ${selectedBloodGroup === group ? 'active' : ''}`;
    chip.id = `chip-${group.replace('+', 'pos').replace('-', 'neg')}`;
    chip.setAttribute('role', 'button');
    chip.setAttribute('tabindex', '0');

    chip.innerHTML = `
      <span class="blood-type">${group}</span>
      <span class="blood-count">${count} donor${count === 1 ? '' : 's'}</span>
    `;

    chip.addEventListener('click', () => {
      if (selectedBloodGroup === group) {
        selectedBloodGroup = '';
        filterBloodGroup.value = '';
      } else {
        selectedBloodGroup = group;
        filterBloodGroup.value = group;
      }
      updateBloodChipsSelection();
      applyFilters();
    });

    bloodChipsGrid.appendChild(chip);
  });
}

function updateBloodChipsSelection() {
  const chips = bloodChipsGrid.querySelectorAll('.blood-chip');
  chips.forEach(chip => {
    const group = chip.querySelector('.blood-type').textContent;
    chip.classList.toggle('active', group === selectedBloodGroup);
  });
}

// ============================================================================
// Filtering & Search
// ============================================================================
function applyFilters() {
  const query = searchTextInput.value.trim().toLowerCase();
  const bloodGroup = filterBloodGroup.value;
  const availability = filterAvailability.value;
  const minDate = filterDonationDate.value; // YYYY-MM-DD

  selectedBloodGroup = bloodGroup;
  updateBloodChipsSelection();

  const filtered = allDonors.filter(donor => {
    // 1. Text Query (Name, City, Phone)
    if (query) {
      const name = (donor.name || '').toLowerCase();
      const city = (donor.city || donor.location || '').toLowerCase();
      const phone = (donor.phoneNumber || '').toLowerCase();
      if (!name.includes(query) && !city.includes(query) && !phone.includes(query)) {
        return false;
      }
    }

    // 2. Category / Blood Group Filter
    if (bloodGroup && donor.bloodGroup !== bloodGroup) {
      return false;
    }

    // 3. Availability Filter
    const available = isDonorAvailable(donor);
    if (availability === 'available' && !available) return false;
    if (availability === 'cooldown' && available) return false;

    // 4. Date Filter: Donated on or after minDate
    if (minDate) {
      if (!donor.lastDonationDate) return false;
      if (donor.lastDonationDate < minDate) return false;
    }

    return true;
  });

  renderTable(filtered);
  updateActiveFilterBadge(filtered.length);
}

function resetFilters() {
  searchTextInput.value = '';
  filterBloodGroup.value = '';
  filterAvailability.value = 'all';
  filterDonationDate.value = '';
  selectedBloodGroup = '';

  updateBloodChipsSelection();
  renderTable(allDonors);
  updateActiveFilterBadge(allDonors.length);
}

function updateActiveFilterBadge(count) {
  tableResultCount.textContent = `${count} donor${count === 1 ? '' : 's'} found`;

  const activeFilters = [];
  if (filterBloodGroup.value) activeFilters.push(`Group: ${filterBloodGroup.value}`);
  if (filterAvailability.value !== 'all') activeFilters.push(filterAvailability.value === 'available' ? 'Available Only' : 'In Cooldown');
  if (filterDonationDate.value) activeFilters.push(`Since ${filterDonationDate.value}`);
  if (searchTextInput.value.trim()) activeFilters.push(`"${searchTextInput.value.trim()}"`);

  if (activeFilters.length > 0) {
    activeFilterBadge.textContent = activeFilters.join(' • ');
  } else {
    activeFilterBadge.textContent = 'Showing All Donors';
  }
}

// ============================================================================
// Donors Directory Table
// ============================================================================
function renderTable(donors) {
  donorsTableBody.innerHTML = '';

  if (donors.length === 0) {
    emptyState.classList.remove('hidden');
    return;
  }
  emptyState.classList.add('hidden');

  donors.forEach(donor => {
    const tr = document.createElement('tr');
    const available = isDonorAvailable(donor);
    const cooldownInfo = getCooldownInfo(donor.lastDonationDate);

    const initial = (donor.name || 'D').charAt(0).toUpperCase();
    const city = donor.city || donor.location || 'N/A';
    const lastDateFormatted = formatDate(donor.lastDonationDate);

    tr.innerHTML = `
      <td>
        <div class="donor-cell">
          <div class="donor-avatar">${initial}</div>
          <div class="donor-details">
            <span class="donor-name">${escapeHtml(donor.name)}</span>
            <span class="donor-sub">${donor.age ? donor.age + ' yrs' : ''} ${donor.gender ? '• ' + donor.gender : ''}</span>
          </div>
        </div>
      </td>
      <td>
        <span class="blood-pill">${escapeHtml(donor.bloodGroup)}</span>
      </td>
      <td>
        <strong>${escapeHtml(city)}</strong>
      </td>
      <td>
        <div class="contact-links">
          <a href="tel:${escapeHtml(donor.phoneNumber)}">📞 ${escapeHtml(donor.phoneNumber)}</a>
          ${donor.email ? `<a href="mailto:${escapeHtml(donor.email)}">✉️ ${escapeHtml(donor.email)}</a>` : ''}
        </div>
      </td>
      <td>
        <div>${lastDateFormatted}</div>
        <small class="form-help">${cooldownInfo.subText}</small>
      </td>
      <td>
        ${available 
          ? `<span class="status-pill status-available"><span class="status-dot"></span> Eligible Now</span>`
          : `<span class="status-pill status-cooldown"><span class="status-dot"></span> Cooldown (${cooldownInfo.daysRemaining}d left)</span>`
        }
      </td>
      <td class="text-right">
        <div class="row-actions">
          <button class="btn-donate" title="Record a donation today (activates 90-day cooldown)" onclick="recordDonation(${donor.id}, '${escapeHtml(donor.name)}')">
            🩸 Donated
          </button>
          <button class="btn-delete" title="Delete donor" onclick="deleteDonor(${donor.id}, '${escapeHtml(donor.name)}')">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="3 6 5 6 21 6"></polyline>
              <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
            </svg>
          </button>
        </div>
      </td>
    `;
    donorsTableBody.appendChild(tr);
  });
}

// ============================================================================
// Actions: Record Donation & Delete Donor
// ============================================================================
window.recordDonation = async function(donorId, donorName) {
  const confirmed = confirm(`Record blood donation for ${donorName} today?\nThis will mark the donor as unavailable for a 90-day cooldown period.`);
  if (!confirmed) return;

  try {
    const today = new Date().toISOString().split('T')[0];
    const res = await fetch(`${API_BASE}/donors/${donorId}/donate?date=${today}`, {
      method: 'POST'
    });

    if (!res.ok) throw new Error('Failed to record donation');
    showToast(`Donation recorded for ${donorName}. 90-day cooldown activated!`, 'success');
    await loadDashboardData();
  } catch (err) {
    console.error('Error recording donation:', err);
    showToast('Failed to record donation', 'error');
  }
};

window.deleteDonor = async function(donorId, donorName) {
  const confirmed = confirm(`Are you sure you want to remove ${donorName} from the donor registry?`);
  if (!confirmed) return;

  try {
    const res = await fetch(`${API_BASE}/donors/${donorId}`, {
      method: 'DELETE'
    });

    if (!res.ok) throw new Error('Failed to delete donor');
    showToast(`Donor ${donorName} was removed.`, 'success');
    await loadDashboardData();
  } catch (err) {
    console.error('Error deleting donor:', err);
    showToast('Could not remove donor', 'error');
  }
};

// ============================================================================
// Registration Modal
// ============================================================================
function openRegisterModal() {
  donorForm.reset();
  // Set max date for last donation to today
  const today = new Date().toISOString().split('T')[0];
  document.getElementById('inputLastDonation').max = today;
  registerModal.classList.remove('hidden');
  document.getElementById('inputName').focus();
}

function closeRegisterModal() {
  registerModal.classList.add('hidden');
}

async function handleDonorRegistration(e) {
  e.preventDefault();

  const submitBtn = document.getElementById('btnSubmitDonor');
  submitBtn.disabled = true;
  submitBtn.textContent = 'Registering...';

  const newDonor = {
    name: document.getElementById('inputName').value.trim(),
    bloodGroup: document.getElementById('inputBloodGroup').value,
    city: document.getElementById('inputCity').value.trim(),
    phoneNumber: document.getElementById('inputPhone').value.trim(),
    email: document.getElementById('inputEmail').value.trim() || null,
    age: document.getElementById('inputAge').value ? parseInt(document.getElementById('inputAge').value, 10) : null,
    gender: document.getElementById('inputGender').value,
    lastDonationDate: document.getElementById('inputLastDonation').value || null
  };

  try {
    const res = await fetch(`${API_BASE}/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(newDonor)
    });

    if (!res.ok) throw new Error('Registration failed');

    const created = await res.json();
    closeRegisterModal();
    showToast(`Donor "${created.name}" registered successfully!`, 'success');
    await loadDashboardData();
  } catch (err) {
    console.error('Error registering donor:', err);
    showToast('Failed to register donor. Please check server connection.', 'error');
  } finally {
    submitBtn.disabled = false;
    submitBtn.textContent = 'Register Donor';
  }
}

// ============================================================================
// Helper Utilities
// ============================================================================
function isDonorAvailable(donor) {
  if (donor.lastDonationDate) {
    const donationDate = new Date(donor.lastDonationDate);
    const today = new Date();
    const diffTime = today - donationDate;
    const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24));
    if (diffDays < 90) return false;
  }
  return donor.available !== false;
}

function getCooldownInfo(dateStr) {
  if (!dateStr) {
    return { daysRemaining: 0, subText: 'No prior donation' };
  }

  const donationDate = new Date(dateStr);
  const today = new Date();
  const diffDays = Math.floor((today - donationDate) / (1000 * 60 * 60 * 24));

  if (diffDays >= 90) {
    return { daysRemaining: 0, subText: `${diffDays} days ago (Eligible)` };
  } else {
    const remaining = 90 - diffDays;
    return { daysRemaining: remaining, subText: `${diffDays} days ago (${remaining}d cooldown left)` };
  }
}

function formatDate(dateStr) {
  if (!dateStr) return 'Never Donated';
  try {
    const [year, month, day] = dateStr.split('-');
    const date = new Date(year, month - 1, day);
    return date.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });
  } catch (e) {
    return dateStr;
  }
}

function showToast(message, type = 'success') {
  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  toast.innerHTML = `
    <span>${type === 'success' ? '✓' : '⚠️'}</span>
    <span>${escapeHtml(message)}</span>
  `;

  toastContainer.appendChild(toast);
  setTimeout(() => {
    toast.remove();
  }, 3500);
}

function debounce(fn, delay) {
  let timeout;
  return function(...args) {
    clearTimeout(timeout);
    timeout = setTimeout(() => fn.apply(this, args), delay);
  };
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

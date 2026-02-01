import { AuditAPI } from "../api/audit.api.js";

const HistoryComponent = (() => {
  let currentTab = "inventory";
  let inventoryAudits = [];
  let recipeAudits = [];
  let orderAudits = [];
  let filteredInventoryAudits = [];
  let filteredRecipeAudits = [];
  let filteredOrderAudits = [];

  // DOM Elements
  const getElements = () => ({
    inventoryTab: document.getElementById("inventoryHistoryTab"),
    recipesTab: document.getElementById("recipesHistoryTab"),
    ordersTab: document.getElementById("ordersHistoryTab"),
    inventoryBody: document.getElementById("inventoryHistoryBody"),
    recipesBody: document.getElementById("recipesHistoryBody"),
    ordersBody: document.getElementById("ordersHistoryBody"),
    tabButtons: document.querySelectorAll(".tab-btn"),
    searchInput: document.querySelector(".history-search"),
    filterSelect: document.querySelector(".history-filter"),
    dateFrom: document.getElementById("dateFrom"),
    dateTo: document.getElementById("dateTo"),
  });

  const init = () => {
    console.log("Initializing History Component...");
    setupEventListeners();
    loadInventoryHistory();
  };

  const setupEventListeners = () => {
    const elements = getElements();

    // Tab switching
    elements.tabButtons.forEach((btn) => {
      btn.addEventListener("click", handleTabSwitch);
    });

    // Search
    elements.searchInput.addEventListener("input", handleSearch);

    // Filter
    elements.filterSelect.addEventListener("change", handleFilter);

    // Date range
    elements.dateFrom.addEventListener("change", handleDateFilter);
    elements.dateTo.addEventListener("change", handleDateFilter);
  };

  const handleTabSwitch = (e) => {
    const tab = e.target.dataset.tab;
    if (!tab) return;

    currentTab = tab;

    const elements = getElements();

    // Update tab buttons
    elements.tabButtons.forEach((btn) => {
      btn.classList.toggle("active", btn.dataset.tab === tab);
    });

    // Update tab content
    elements.inventoryTab.classList.toggle("active", tab === "inventory");
    elements.recipesTab.classList.toggle("active", tab === "recipes");
    elements.ordersTab.classList.toggle("active", tab === "orders");

    // Update search placeholder
    if (tab === "inventory") {
      elements.searchInput.placeholder = "Buscar por producto o usuario";
      elements.filterSelect.style.display = "block";
      if (!inventoryAudits.length) {
        loadInventoryHistory();
      }
    } else if (tab === "recipes") {
      elements.searchInput.placeholder = "Buscar por receta o usuario";
      elements.filterSelect.style.display = "none";
      if (!recipeAudits.length) {
        loadRecipeHistory();
      }
    } else if (tab === "orders") {
      elements.searchInput.placeholder = "Buscar por orden o usuario";
      elements.filterSelect.style.display = "none";
      if (!orderAudits.length) {
        loadOrderHistory();
      }
    }

    // Clear search and filter
    elements.searchInput.value = "";
    elements.filterSelect.value = "all";
  };

  const handleSearch = (e) => {
    const searchTerm = e.target.value.toLowerCase();

    if (currentTab === "inventory") {
      filteredInventoryAudits = inventoryAudits.filter(
        (audit) =>
          audit.productName?.toLowerCase().includes(searchTerm) ||
          audit.userName?.toLowerCase().includes(searchTerm)
      );
      renderInventoryHistory(filteredInventoryAudits);
    } else if (currentTab === "recipes") {
      filteredRecipeAudits = recipeAudits.filter(
        (audit) =>
          audit.recipeName?.toLowerCase().includes(searchTerm) ||
          audit.userName?.toLowerCase().includes(searchTerm)
      );
      renderRecipeHistory(filteredRecipeAudits);
    } else if (currentTab === "orders") {
      filteredOrderAudits = orderAudits.filter(
        (audit) =>
          audit.orderId?.toString().includes(searchTerm) ||
          audit.userName?.toLowerCase().includes(searchTerm)
      );
      renderOrderHistory(filteredOrderAudits);
    }
  };

  const handleFilter = (e) => {
    const filterValue = e.target.value;

    if (filterValue === "all") {
      filteredInventoryAudits = [...inventoryAudits];
    } else {
      filteredInventoryAudits = inventoryAudits.filter(
        (audit) => audit.movementType === filterValue
      );
    }

    renderInventoryHistory(filteredInventoryAudits);
  };

  const handleDateFilter = () => {
    const elements = getElements();
    const dateFrom = elements.dateFrom.value;
    const dateTo = elements.dateTo.value;

    if (!dateFrom && !dateTo) {
      if (currentTab === "inventory") {
        filteredInventoryAudits = [...inventoryAudits];
        renderInventoryHistory(filteredInventoryAudits);
      } else if (currentTab === "recipes") {
        filteredRecipeAudits = [...recipeAudits];
        renderRecipeHistory(filteredRecipeAudits);
      } else if (currentTab === "orders") {
        filteredOrderAudits = [...orderAudits];
        renderOrderHistory(filteredOrderAudits);
      }
      return;
    }

    if (currentTab === "inventory") {
      filteredInventoryAudits = inventoryAudits.filter((audit) => {
        const auditDate = new Date(audit.movementDate);
        const fromDate = dateFrom ? new Date(dateFrom) : new Date(0);
        const toDate = dateTo ? new Date(dateTo) : new Date();
        toDate.setHours(23, 59, 59, 999);
        
        return auditDate >= fromDate && auditDate <= toDate;
      });
      renderInventoryHistory(filteredInventoryAudits);
    } else if (currentTab === "recipes") {
      filteredRecipeAudits = recipeAudits.filter((audit) => {
        const auditDate = new Date(audit.actionDate);
        const fromDate = dateFrom ? new Date(dateFrom) : new Date(0);
        const toDate = dateTo ? new Date(dateTo) : new Date();
        toDate.setHours(23, 59, 59, 999);
        
        return auditDate >= fromDate && auditDate <= toDate;
      });
      renderRecipeHistory(filteredRecipeAudits);
    } else if (currentTab === "orders") {
      filteredOrderAudits = orderAudits.filter((audit) => {
        const auditDate = new Date(audit.auditDate);
        const fromDate = dateFrom ? new Date(dateFrom) : new Date(0);
        const toDate = dateTo ? new Date(dateTo) : new Date();
        toDate.setHours(23, 59, 59, 999);
        
        return auditDate >= fromDate && auditDate <= toDate;
      });
      renderOrderHistory(filteredOrderAudits);
    }
  };

  // ==================== INVENTORY HISTORY ====================

  const loadInventoryHistory = async () => {
    try {
      console.log("Loading inventory history...");
      const response = await AuditAPI.getAllInventoryAudits({ size: 200 });
      inventoryAudits = response || [];
      filteredInventoryAudits = [...inventoryAudits];
      renderInventoryHistory(filteredInventoryAudits);
    } catch (error) {
      console.error("Error loading inventory history:", error);
      showError("inventoryHistoryBody", "Error al cargar el historial de inventario");
    }
  };

  const renderInventoryHistory = (audits) => {
    const tbody = document.getElementById("inventoryHistoryBody");

    if (!audits || audits.length === 0) {
      tbody.innerHTML = `
        <tr class="empty-history">
          <td colspan="7">
            <div class="empty-history">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M9 11l3 3L22 4"></path>
                <path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11"></path>
              </svg>
              <h3>No hay movimientos registrados</h3>
              <p>Los movimientos de inventario aparecerán aquí</p>
            </div>
          </td>
        </tr>
      `;
      return;
    }

    tbody.innerHTML = audits
      .map((audit) => {
        const date = formatDate(audit.movementDate);
        const movementClass = audit.movementType?.toLowerCase() || "";
        const quantity = formatQuantity(audit.quantity);
        const stateDiff = formatStateDiff(audit.previousState, audit.newState);

        return `
          <tr>
            <td>${date}</td>
            <td><strong>${audit.productName || "N/A"}</strong></td>
            <td>
              <span class="movement-badge ${movementClass}">
                ${audit.movementType || "N/A"}
              </span>
            </td>
            <td>
              <span class="quantity-display ${parseFloat(audit.quantity) >= 0 ? 'positive' : 'negative'}">
                ${quantity}
              </span>
            </td>
            <td>${audit.userName || "Sistema"}</td>
            <td>
              <div class="state-diff previous">
                ${stateDiff.previous}
              </div>
            </td>
            <td>
              <div class="state-diff new">
                ${stateDiff.new}
              </div>
            </td>
          </tr>
        `;
      })
      .join("");
  };

  // ==================== RECIPE HISTORY ====================

  const loadRecipeHistory = async () => {
    try {
      console.log("Loading recipe history...");
      const response = await AuditAPI.getAllRecipeAudits({ size: 200 });
      recipeAudits = response || [];
      filteredRecipeAudits = [...recipeAudits];
      renderRecipeHistory(filteredRecipeAudits);
    } catch (error) {
      console.error("Error loading recipe history:", error);
      showError("recipesHistoryBody", "Error al cargar el historial de recetas");
    }
  };

  const renderRecipeHistory = (audits) => {
    const tbody = document.getElementById("recipesHistoryBody");

    if (!audits || audits.length === 0) {
      tbody.innerHTML = `
        <tr class="empty-history">
          <td colspan="6">
            <div class="empty-history">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M9 11l3 3L22 4"></path>
                <path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11"></path>
              </svg>
              <h3>No hay cambios registrados</h3>
              <p>Los cambios en recetas aparecerán aquí</p>
            </div>
          </td>
        </tr>
      `;
      return;
    }

    tbody.innerHTML = audits
      .map((audit) => {
        const date = formatDate(audit.actionDate);
        const actionClass = audit.actionType?.toLowerCase() || "";
        const stateDiff = formatStateDiff(audit.previousState, audit.newState);

        return `
          <tr>
            <td>${date}</td>
            <td><strong>${audit.recipeName || "N/A"}</strong></td>
            <td>
              <span class="action-badge ${actionClass}">
                ${audit.actionType || "N/A"}
              </span>
            </td>
            <td>${audit.userName || "Sistema"}</td>
            <td>
              <div class="state-diff previous">
                ${stateDiff.previous}
              </div>
            </td>
            <td>
              <div class="state-diff new">
                ${stateDiff.new}
              </div>
            </td>
          </tr>
        `;
      })
      .join("");
  };

  // ==================== ORDER HISTORY ====================

  const loadOrderHistory = async () => {
    try {
      console.log("Loading order history...");
      const response = await AuditAPI.getAllOrderAudits({ size: 200 });
      orderAudits = response || [];
      filteredOrderAudits = [...orderAudits];
      renderOrderHistory(filteredOrderAudits);
    } catch (error) {
      console.error("Error loading order history:", error);
      showError("ordersHistoryBody", "Error al cargar el historial de órdenes");
    }
  };

  const renderOrderHistory = (audits) => {
    const tbody = document.getElementById("ordersHistoryBody");

    if (!audits || audits.length === 0) {
      tbody.innerHTML = `
        <tr class="empty-history">
          <td colspan="6">
            <div class="empty-history">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M9 11l3 3L22 4"></path>
                <path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11"></path>
              </svg>
              <h3>No hay cambios registrados</h3>
              <p>Los cambios en órdenes aparecerán aquí</p>
            </div>
          </td>
        </tr>
      `;
      return;
    }

    tbody.innerHTML = audits
      .map((audit) => {
        const date = formatDate(audit.auditDate);
        const actionClass = audit.action?.toLowerCase().replace(/ /g, '-') || "";
        const stateDiff = formatStateDiff(audit.previousState, audit.newState);

        return `
          <tr>
            <td>${date}</td>
            <td><strong>#${audit.orderId || "N/A"}</strong></td>
            <td>
              <span class="action-badge ${actionClass}">
                ${audit.action || "N/A"}
              </span>
            </td>
            <td>${audit.userName || "Sistema"}</td>
            <td>
              <div class="state-diff previous">
                ${stateDiff.previous}
              </div>
            </td>
            <td>
              <div class="state-diff new">
                ${stateDiff.new}
              </div>
            </td>
          </tr>
        `;
      })
      .join("");
  };

  // ==================== UTILITY FUNCTIONS ====================

  const formatDate = (dateString) => {
    if (!dateString) return "N/A";
    const date = new Date(dateString);
    return new Intl.DateTimeFormat("es-ES", {
      year: "numeric",
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    }).format(date);
  };

  const formatQuantity = (quantity) => {
    if (quantity === null || quantity === undefined) return "N/A";
    const num = parseFloat(quantity);
    const sign = num >= 0 ? "+" : "";
    return `${sign}${num.toFixed(3)}`;
  };

  const formatState = (state) => {
    if (!state || state === "N/A" || state === "null") return "—";
    
    try {
      const parsed = JSON.parse(state);
      const entries = Object.entries(parsed)
        .map(([key, value]) => `<div class="state-field"><span class="field-label">${key}:</span> <span class="field-value">${value}</span></div>`)
        .join("");
      return entries || "—";
    } catch {
      // If it's not JSON, return truncated string
      return state.length > 50 ? state.substring(0, 50) + "..." : state;
    }
  };

  const formatStateDiff = (previousState, newState) => {
    if ((!previousState || previousState === "N/A" || previousState === "null") && 
        (!newState || newState === "N/A" || newState === "null")) {
      return { previous: "—", new: "—" };
    }

    try {
      const prevParsed = previousState ? JSON.parse(previousState) : {};
      const newParsed = newState ? JSON.parse(newState) : {};
      
      // Get all unique keys from both states
      const allKeys = new Set([...Object.keys(prevParsed), ...Object.keys(newParsed)]);
      
      let previousHtml = "";
      let newHtml = "";
      
      allKeys.forEach(key => {
        const prevValue = prevParsed[key];
        const newValue = newParsed[key];
        const hasChanged = prevValue !== newValue;
        
        // Format previous state
        if (prevValue !== undefined) {
          const highlightClass = hasChanged ? 'changed' : '';
          previousHtml += `<div class="state-field ${highlightClass}"><span class="field-label">${key}:</span> <span class="field-value">${prevValue}</span></div>`;
        } else {
          previousHtml += `<div class="state-field removed"><span class="field-label">${key}:</span> <span class="field-value">—</span></div>`;
        }
        
        // Format new state
        if (newValue !== undefined) {
          const highlightClass = hasChanged ? 'changed' : '';
          newHtml += `<div class="state-field ${highlightClass}"><span class="field-label">${key}:</span> <span class="field-value">${newValue}</span></div>`;
        } else {
          newHtml += `<div class="state-field removed"><span class="field-label">${key}:</span> <span class="field-value">—</span></div>`;
        }
      });
      
      return {
        previous: previousHtml || "—",
        new: newHtml || "—"
      };
    } catch {
      // If parsing fails, return raw values
      return {
        previous: formatState(previousState),
        new: formatState(newState)
      };
    }
  };

  const showError = (tbodyId, message) => {
    const tbody = document.getElementById(tbodyId);
    tbody.innerHTML = `
      <tr>
        <td colspan="7" style="text-align: center; padding: 2rem; color: #c62828;">
          <strong>⚠ ${message}</strong>
        </td>
      </tr>
    `;
  };

  // Public API
  return {
    init,
    loadInventoryHistory,
    loadRecipeHistory,
    loadOrderHistory,
  };
})();

export { HistoryComponent };

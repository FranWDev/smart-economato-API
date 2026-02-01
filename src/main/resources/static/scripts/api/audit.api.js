import { UsersAPI } from "./users.api.js";

const AuditAPI = (() => {
  const INVENTORY_API_BASE = "/api/inventory-audits";
  const RECIPE_API_BASE = "/api/recipe-audits";
  const ORDER_API_BASE = "/api/order-audits";

  const getAuthHeaders = () => {
    const headers = {
      "Content-Type": "application/json",
    };

    const token = UsersAPI.getStoredToken();
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }

    return headers;
  };

  // ==================== INVENTORY AUDITS ====================

  const getAllInventoryAudits = async (params = {}) => {
    try {
      const defaultParams = { page: 0, size: 100, ...params };
      const queryString = new URLSearchParams(defaultParams).toString();
      const url = `${INVENTORY_API_BASE}?${queryString}`;
      
      const response = await fetch(url, {
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch inventory audits: ${response.statusText}`);
      }

      return response.json();
    } catch (error) {
      console.error("Error fetching inventory audits:", error);
      throw error;
    }
  };

  const getInventoryAuditById = async (id) => {
    try {
      const response = await fetch(`${INVENTORY_API_BASE}/${id}`, {
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch inventory audit: ${response.statusText}`);
      }

      return response.json();
    } catch (error) {
      console.error("Error fetching inventory audit:", error);
      throw error;
    }
  };

  const getInventoryAuditsByType = async (type) => {
    try {
      const response = await fetch(`${INVENTORY_API_BASE}/type/${type}`, {
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch inventory audits by type: ${response.statusText}`);
      }

      return response.json();
    } catch (error) {
      console.error("Error fetching inventory audits by type:", error);
      throw error;
    }
  };

  const getInventoryAuditsByDateRange = async (startDate, endDate) => {
    try {
      const params = new URLSearchParams({
        start: startDate,
        end: endDate,
      });
      
      const response = await fetch(`${INVENTORY_API_BASE}/by-date-range?${params}`, {
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch inventory audits by date range: ${response.statusText}`);
      }

      return response.json();
    } catch (error) {
      console.error("Error fetching inventory audits by date range:", error);
      throw error;
    }
  };

  // ==================== RECIPE AUDITS ====================

  const getAllRecipeAudits = async (params = {}) => {
    try {
      const defaultParams = { page: 0, size: 100, ...params };
      const queryString = new URLSearchParams(defaultParams).toString();
      const url = `${RECIPE_API_BASE}?${queryString}`;
      
      const response = await fetch(url, {
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch recipe audits: ${response.statusText}`);
      }

      return response.json();
    } catch (error) {
      console.error("Error fetching recipe audits:", error);
      throw error;
    }
  };

  const getRecipeAuditById = async (id) => {
    try {
      const response = await fetch(`${RECIPE_API_BASE}/${id}`, {
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch recipe audit: ${response.statusText}`);
      }

      return response.json();
    } catch (error) {
      console.error("Error fetching recipe audit:", error);
      throw error;
    }
  };

  const getRecipeAuditsByRecipeId = async (recipeId) => {
    try {
      const response = await fetch(`${RECIPE_API_BASE}/by-recipe/${recipeId}`, {
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch recipe audits by recipe: ${response.statusText}`);
      }

      return response.json();
    } catch (error) {
      console.error("Error fetching recipe audits by recipe:", error);
      throw error;
    }
  };

  const getRecipeAuditsByUserId = async (userId) => {
    try {
      const response = await fetch(`${RECIPE_API_BASE}/by-user/${userId}`, {
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch recipe audits by user: ${response.statusText}`);
      }

      return response.json();
    } catch (error) {
      console.error("Error fetching recipe audits by user:", error);
      throw error;
    }
  };

  const getRecipeAuditsByDateRange = async (startDate, endDate) => {
    try {
      const params = new URLSearchParams({
        start: startDate,
        end: endDate,
      });
      
      const response = await fetch(`${RECIPE_API_BASE}/by-date-range?${params}`, {
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch recipe audits by date range: ${response.statusText}`);
      }

      return response.json();
    } catch (error) {
      console.error("Error fetching recipe audits by date range:", error);
      throw error;
    }
  };

  // ==================== ORDER AUDITS ====================

  const getAllOrderAudits = async (params = {}) => {
    try {
      const defaultParams = { page: 0, size: 100, ...params };
      const queryString = new URLSearchParams(defaultParams).toString();
      const url = `${ORDER_API_BASE}?${queryString}`;
      
      const response = await fetch(url, {
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch order audits: ${response.statusText}`);
      }

      return response.json();
    } catch (error) {
      console.error("Error fetching order audits:", error);
      throw error;
    }
  };

  const getOrderAuditById = async (id) => {
    try {
      const response = await fetch(`${ORDER_API_BASE}/${id}`, {
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch order audit: ${response.statusText}`);
      }

      return response.json();
    } catch (error) {
      console.error("Error fetching order audit:", error);
      throw error;
    }
  };

  const getOrderAuditsByOrderId = async (orderId) => {
    try {
      const response = await fetch(`${ORDER_API_BASE}/by-order/${orderId}`, {
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch order audits by order: ${response.statusText}`);
      }

      return response.json();
    } catch (error) {
      console.error("Error fetching order audits by order:", error);
      throw error;
    }
  };

  const getOrderAuditsByUserIdOrders = async (userId) => {
    try {
      const response = await fetch(`${ORDER_API_BASE}/by-user/${userId}`, {
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch order audits by user: ${response.statusText}`);
      }

      return response.json();
    } catch (error) {
      console.error("Error fetching order audits by user:", error);
      throw error;
    }
  };

  const getOrderAuditsByDateRangeOrders = async (startDate, endDate) => {
    try {
      const params = new URLSearchParams({
        start: startDate,
        end: endDate,
      });
      
      const response = await fetch(`${ORDER_API_BASE}/by-date-range?${params}`, {
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch order audits by date range: ${response.statusText}`);
      }

      return response.json();
    } catch (error) {
      console.error("Error fetching order audits by date range:", error);
      throw error;
    }
  };

  return {
    // Inventory Audits
    getAllInventoryAudits,
    getInventoryAuditById,
    getInventoryAuditsByType,
    getInventoryAuditsByDateRange,
    
    // Recipe Audits
    getAllRecipeAudits,
    getRecipeAuditById,
    getRecipeAuditsByRecipeId,
    getRecipeAuditsByUserId,
    getRecipeAuditsByDateRange,

    // Order Audits
    getAllOrderAudits,
    getOrderAuditById,
    getOrderAuditsByOrderId,
    getOrderAuditsByUserId: getOrderAuditsByUserIdOrders,
    getOrderAuditsByDateRange: getOrderAuditsByDateRangeOrders,
  };
})();

export { AuditAPI };

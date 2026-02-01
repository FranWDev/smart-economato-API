import { AuthComponent } from "./components/auth.component.js";
import { initSidebar } from "./components/sidebar.component.js";
import { initProductComponent } from "./components/product.component.js";
import { initRecipeComponent } from "./components/recipe.component.js";
import { initReceptionComponent } from "./components/reception.component.js";
import { showCreateOrderModal } from "./components/order-creation.component.js";
import { initNavRouter } from "./components/router.component.js";
import OrdersComponent from "./components/orders.component.js";
import { BarcodeScannerUtils } from "./utils/barcode-scanner.utils.js";
import { HistoryComponent } from "./components/history.component.js";

document.addEventListener("DOMContentLoaded", () => {
  try {
    AuthComponent.init();
    AuthComponent.setupTokenExpirationCheck();
  } catch (error) {
    console.warn("Auth component error:", error);
  }

  try {
    BarcodeScannerUtils.preloadCamera().catch(err => {
      console.log('Camera preload failed (non-critical):', err);
    });
  } catch (error) {
    console.log('Camera preload not available:', error);
  }

  const appState = initNavRouter();

  try {
    initSidebar();
  } catch (error) {
    console.warn("Sidebar not available on this page:", error);
  }

  const tableBody = document.querySelector(".inventory-table tbody");
  if (tableBody) {
    try {
      initProductComponent(tableBody);
    } catch (error) {
      console.warn("Product component not available on this page:", error);
    }
  }

  try {
    initRecipeComponent();
  } catch (error) {
    console.warn("Recipe component not available on this page:", error);
  }

  try {
    initReceptionComponent();

    OrdersComponent.init(
      "#receptionGrid",
      ".reception-search",
      ".reception-filter"
    );
  } catch (error) {
    console.warn("Reception/Orders component error:", error);
  }

  try {
    HistoryComponent.init();
  } catch (error) {
    console.warn("History component error:", error);
  }

  const createOrderBtn = document.querySelector("#createOrderBtn");
  if (createOrderBtn) {
    createOrderBtn.addEventListener("click", () => {
      showCreateOrderModal();
    });
  }
});

window.addEventListener("error", (event) => {
  console.error("Global error:", event.error);
});

window.addEventListener("unhandledrejection", (event) => {
  console.error("Unhandled promise rejection:", event.reason);
});
